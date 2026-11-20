package com.tikhomirov.zdorovyak.nutrition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.tikhomirov.zdorovyak.ai.ClaudeClient
import com.tikhomirov.zdorovyak.ai.Msg
import com.tikhomirov.zdorovyak.ai.Part
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.FoodEntry
import com.tikhomirov.zdorovyak.data.FoodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

data class PhotoItem(val name: String, val grams: Double, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)
data class PhotoResult(val items: List<PhotoItem>, val comment: String)

class NutritionRepo(private val db: AppDatabase, private val claude: ClaudeClient) {
    private val dao = db.food()
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun add(e: FoodEntry) {
        dao.insert(e)
        // запоминаем продукт в справочник (на 100 г), чтобы потом добавлять в одно касание
        if (e.grams > 0) {
            val k = 100.0 / e.grams
            val existing = dao.byName(e.name)
            dao.upsertItem(FoodItem(id = existing?.id ?: 0, name = e.name, kcal100 = e.kcal * k, protein100 = e.protein * k,
                fat100 = e.fat * k, carbs100 = e.carbs * k, barcode = e.barcode ?: existing?.barcode))
        }
    }

    /** Штрихкод: сначала локальный справочник, затем Open Food Facts. */
    suspend fun lookupBarcode(code: String): FoodItem? {
        dao.byBarcode(code)?.let { return it }
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,product_name_ru,brands,nutriments")
                .header("User-Agent", "Zdorovyak/0.1 (personal Android app)")
                .build()
            runCatching {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val o = json.parseToJsonElement(r.body!!.string()).jsonObject
                    if (o["status"]?.jsonPrimitive?.content != "1") return@use null
                    val p = o["product"]!!.jsonObject
                    val n = p["nutriments"]?.jsonObject ?: JsonObject(emptyMap())
                    fun num(k: String) = n[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val name = listOfNotNull(
                        p["product_name_ru"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: p["product_name"]?.jsonPrimitive?.content,
                        p["brands"]?.jsonPrimitive?.content?.substringBefore(',')?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { "Продукт $code" }
                    var kcal = num("energy-kcal_100g")
                    if (kcal == 0.0) kcal = num("energy_100g") / 4.184
                    FoodItem(name = name, kcal100 = kcal, protein100 = num("proteins_100g"), fat100 = num("fat_100g"),
                        carbs100 = num("carbohydrates_100g"), barcode = code)
                }
            }.getOrNull()
        }?.also { dao.upsertItem(it) }
    }

    /** Фото тарелки → список блюд с оценкой массы и КБЖУ. */
    suspend fun analyzePhoto(file: File, hint: String): PhotoResult {
        val b64 = withContext(Dispatchers.Default) { compress(file) }
        val system = """
Ты нутрициолог. По фото еды оцени, что на тарелке, массу каждого компонента в граммах и КБЖУ.
Учитывай типичные порции, видимый размер посуды, масло при жарке. Если не уверен — дай наиболее вероятную оценку и скажи об этом в comment.
Верни только JSON:
{"items":[{"name":"гречка отварная","grams":180,"kcal":198,"protein":7.6,"fat":2.0,"carbs":38.0}],"comment":"коротко по-русски: что учтено, где неопределённость"}
Значения kcal/protein/fat/carbs — на указанную массу, не на 100 г.
""".trimIndent()
        val obj = claude.completeJson(system, listOf(Msg("user", listOf(
            Part.Image(b64),
            Part.Text(if (hint.isBlank()) "Что здесь и сколько?" else "Подсказка: $hint"),
        ))), maxTokens = 1500)
        val items = obj["items"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            fun d(k: String) = o[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
            PhotoItem(o["name"]?.jsonPrimitive?.content ?: "Блюдо", d("grams"), d("kcal"), d("protein"), d("fat"), d("carbs"))
        } ?: emptyList()
        return PhotoResult(items, obj["comment"]?.jsonPrimitive?.content ?: "")
    }

    private fun compress(file: File): String {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / sample > 2000 || opts.outHeight / sample > 2000) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        val scale = 1568.0 / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
