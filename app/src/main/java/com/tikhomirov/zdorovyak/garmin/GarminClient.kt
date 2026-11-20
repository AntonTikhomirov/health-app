package com.tikhomirov.zdorovyak.garmin

import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.GarminActivity
import com.tikhomirov.zdorovyak.data.GarminDay
import com.tikhomirov.zdorovyak.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.TimeUnit

class GarminException(msg: String) : Exception(msg)

@Serializable
data class GarminTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val clientId: String,
    val expiresAt: Long = 0,
    val displayName: String? = null,
)

/**
 * Неофициальный доступ к Garmin Connect. Логика повторяет python-garminconnect 0.3.x
 * (garth с его OAuth1 Garmin сломал в 2025 году):
 * 1) вход как iOS-приложение Garmin Connect: sso.garmin.com/mobile/api/login → service ticket
 *    (запасной путь — старый SSO-виджет);
 * 2) обмен тикета на DI OAuth2 токены на diauth.garmin.com;
 * 3) access-токен обновляется по refresh-токену без пароля.
 * Garmin может поменять это без предупреждения: тогда сломается только синхронизация.
 */
class GarminClient(private val settings: Settings, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = mutableMapOf<String, MutableList<Cookie>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = this@GarminClient.cookies.getOrPut(url.host) { mutableListOf() }
                cookies.forEach { c -> list.removeAll { it.name == c.name }; list += c }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies[url.host].orEmpty()
        })
        .build()

    private val sso = "https://sso.garmin.com"
    private val api = "https://connectapi.garmin.com"
    private val diTokenUrl = "https://diauth.garmin.com/di-oauth2-service/oauth/token"
    private val diGrant = "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"
    private val diClientIds = listOf(
        "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2", "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
        "GARMIN_CONNECT_MOBILE_ANDROID_DI", "GARMIN_CONNECT_MOBILE_IOS_DI",
    )
    private val iosClientId = "GCM_IOS_DARK"
    private val iosService = "https://mobile.integration.garmin.com/gcm/ios"
    private val iosUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
    private val nativeHeaders = mapOf(
        "User-Agent" to "GCM-Android-5.23",
        "X-Garmin-User-Agent" to "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0",
        "X-Garmin-Paired-App-Version" to "10861",
        "X-Garmin-Client-Platform" to "Android",
        "X-App-Ver" to "10861",
        "X-Lang" to "en",
        "X-GCExperience" to "GC5",
        "Accept-Language" to "en-US,en;q=0.9",
    )
    private val jsonType = "application/json".toMediaType()

    val isLoggedIn: Boolean get() = settings.garminTokens.isNotBlank()

    fun logout() { settings.garminTokens = "" }

    private class AuthFailed(msg: String) : Exception(msg)

    // ---------------- Вход ----------------

    suspend fun login(email: String, password: String, askMfa: suspend () -> String?) = withContext(Dispatchers.IO) {
        cookies.clear()
        val (ticket, serviceUrl) = try {
            mobileLogin(email, password, askMfa) to iosService
        } catch (e: AuthFailed) {
            throw GarminException(e.message ?: "Garmin не пустил")
        } catch (e: Exception) {
            // Мобильный вход заблокирован (403/429/капча) — пробуем старый SSO-виджет
            cookies.clear()
            try { widgetLogin(email, password, askMfa) to "$sso/sso/embed" }
            catch (e2: Exception) { throw GarminException("Garmin: мобильный вход — ${e.message}; виджет — ${e2.message}") }
        }
        var t = exchangeTicket(ticket, serviceUrl)
        val profile = apiGet(t, "/userprofile-service/socialProfile") as? JsonObject
        t = t.copy(displayName = profile?.get("displayName")?.jsonPrimitive?.content)
        save(t)
    }

    private suspend fun mobileLogin(email: String, password: String, askMfa: suspend () -> String?): String {
        val signinUrl = "$sso/mobile/sso/en_US/sign-in".withQuery(mapOf("clientId" to iosClientId, "service" to iosService))
        http.newCall(Request.Builder().url(signinUrl).header("User-Agent", iosUa)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9").build()).execute().use { r ->
            if (r.code == 429) throw GarminException("Garmin: слишком много попыток, подождите час.")
            if (!r.isSuccessful) throw GarminException("страница входа ${r.code}")
        }
        kotlinx.coroutines.delay((2000L..5000L).random()) // как живой человек: Cloudflare не любит мгновенный POST
        val params = mapOf("clientId" to iosClientId, "locale" to "en-US", "service" to iosService)
        val headers = mapOf(
            "User-Agent" to iosUa, "Accept" to "application/json, text/plain, */*", "Accept-Language" to "en-US,en;q=0.9",
            "Origin" to sso, "Referer" to signinUrl,
        )
        val body = buildJsonObject { put("username", email); put("password", password); put("rememberMe", true); put("captchaToken", "") }
        var res = postJson("$sso/mobile/api/login".withQuery(params), body, headers)
        var type = (res["responseStatus"] as? JsonObject)?.get("type")?.jsonPrimitive?.content
        if (type == "MFA_REQUIRED") {
            val method = (res["customerMfaInfo"] as? JsonObject)?.get("mfaLastMethodUsed")?.jsonPrimitive?.content ?: "email"
            val code = askMfa() ?: throw AuthFailed("Нужен код двухфакторной проверки.")
            val mfa = buildJsonObject {
                put("mfaMethod", method); put("mfaVerificationCode", code.trim()); put("rememberMyBrowser", true)
                put("reconsentList", JsonArray(emptyList())); put("mfaSetup", false)
            }
            res = postJson("$sso/mobile/api/mfa/verifyCode".withQuery(params), mfa, headers)
            type = (res["responseStatus"] as? JsonObject)?.get("type")?.jsonPrimitive?.content
        }
        return when (type) {
            "SUCCESSFUL" -> res["serviceTicketId"]?.jsonPrimitive?.content ?: throw GarminException("нет serviceTicketId")
            "INVALID_USERNAME_PASSWORD" -> throw AuthFailed("Неверный логин или пароль Garmin.")
            "CAPTCHA_REQUIRED" -> throw GarminException("Garmin требует капчу")
            else -> throw GarminException("ответ ${type ?: "неизвестен"}")
        }
    }

    private fun postJson(url: String, body: JsonObject, headers: Map<String, String>): JsonObject {
        val rb = Request.Builder().url(url).post(body.toString().toRequestBody(jsonType))
        headers.forEach { (k, v) -> rb.header(k, v) }
        return http.newCall(rb.build()).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (r.code == 429) throw GarminException("Garmin: слишком много попыток (429), подождите час.")
            if (r.code == 403) throw GarminException("Garmin/Cloudflare отклонил запрос (403)")
            runCatching { json.parseToJsonElement(s).jsonObject }.getOrElse { throw GarminException("HTTP ${r.code}, ответ не JSON") }
        }
    }

    /** Запасной путь: HTML-виджет SSO (как было в garth). */
    private suspend fun widgetLogin(email: String, password: String, askMfa: suspend () -> String?): String {
        val base = "$sso/sso"; val embed = "$base/embed"
        val signinParams = mapOf(
            "id" to "gauth-widget", "embedWidget" to "true", "gauthHost" to embed,
            "service" to embed, "source" to embed,
            "redirectAfterAccountLoginUrl" to embed, "redirectAfterAccountCreationUrl" to embed,
        )
        get(embed.withQuery(mapOf("id" to "gauth-widget", "embedWidget" to "true", "gauthHost" to base)))
        val signinUrl = "$base/signin".withQuery(signinParams)
        val page = get(signinUrl, referer = embed)
        val csrfRe = Regex("name=\"_csrf\"\\s+value=\"(.+?)\"")
        val csrf = csrfRe.find(page)?.groupValues?.get(1) ?: throw GarminException("нет CSRF на странице входа")
        kotlinx.coroutines.delay((3000L..6000L).random())
        var resp = post(signinUrl, mapOf("username" to email, "password" to password, "embed" to "true", "_csrf" to csrf), referer = signinUrl)
        var title = Regex("<title>(.+?)</title>").find(resp)?.groupValues?.get(1) ?: ""
        if ("MFA" in title) {
            val code = askMfa() ?: throw GarminException("Нужен код двухфакторной проверки.")
            val csrf2 = csrfRe.find(resp)?.groupValues?.get(1) ?: csrf
            resp = post("$base/verifyMFA/loginEnterMfaCode".withQuery(signinParams),
                mapOf("mfa-code" to code, "embed" to "true", "_csrf" to csrf2, "fromPage" to "setupEnterMfaCode"), referer = signinUrl)
            title = Regex("<title>(.+?)</title>").find(resp)?.groupValues?.get(1) ?: ""
        }
        if (title != "Success") throw GarminException("виджет ответил «$title»")
        return Regex("embed\\?ticket=([^\"]+)\"").find(resp)?.groupValues?.get(1) ?: throw GarminException("нет тикета после входа")
    }

    private fun diPost(clientId: String, form: Map<String, String>): Pair<Int, String> {
        val fb = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val rb = Request.Builder().url(diTokenUrl).post(fb)
        nativeHeaders.forEach { (k, v) -> rb.header(k, v) }
        rb.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("$clientId:".toByteArray()))
        rb.header("Accept", "application/json").header("Cache-Control", "no-cache")
        return http.newCall(rb.build()).execute().use { r -> r.code to (r.body?.string().orEmpty()) }
    }

    private fun parseToken(body: String, fallbackClient: String, old: GarminTokens? = null): GarminTokens {
        val o = json.parseToJsonElement(body).jsonObject
        val access = o["access_token"]!!.jsonPrimitive.content
        val expiresIn = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        return GarminTokens(
            accessToken = access,
            refreshToken = o["refresh_token"]?.jsonPrimitive?.content ?: old?.refreshToken,
            clientId = clientIdFromJwt(access) ?: fallbackClient,
            expiresAt = System.currentTimeMillis() + (expiresIn - 120) * 1000,
            displayName = old?.displayName,
        )
    }

    private fun clientIdFromJwt(token: String): String? = runCatching {
        val payload = token.split('.')[1]
        val txt = String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
        json.parseToJsonElement(txt).jsonObject["client_id"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun exchangeTicket(ticket: String, serviceUrl: String): GarminTokens {
        val errors = mutableListOf<String>()
        for (cid in diClientIds) {
            val (code, body) = diPost(cid, mapOf("client_id" to cid, "service_ticket" to ticket, "grant_type" to diGrant, "service_url" to serviceUrl))
            if (code == 429) throw GarminException("Garmin: лимит обмена токенов (429), подождите.")
            if (code in 200..299) runCatching { return parseToken(body, cid) }.onFailure { errors += "$cid: разбор" }
            else errors += "$cid: $code"
        }
        throw GarminException("Garmin: не удалось получить токен (${errors.joinToString()})")
    }

    private fun refresh(t: GarminTokens): GarminTokens {
        val rt = t.refreshToken ?: throw GarminException("Сессия Garmin истекла — войдите заново в настройках.")
        val (code, body) = diPost(t.clientId, mapOf("grant_type" to "refresh_token", "client_id" to t.clientId, "refresh_token" to rt))
        if (code !in 200..299) throw GarminException("Сессия Garmin истекла ($code) — войдите заново в настройках.")
        return parseToken(body, t.clientId, t)
    }

    private fun load(): GarminTokens = runCatching { json.decodeFromString<GarminTokens>(settings.garminTokens) }.getOrNull()
        ?: throw GarminException("Не выполнен вход в Garmin.")

    private fun save(t: GarminTokens) { settings.garminTokens = json.encodeToString(t) }

    private fun fresh(): GarminTokens {
        var t = load()
        if (System.currentTimeMillis() > t.expiresAt) { t = refresh(t); save(t) }
        return t
    }

    private fun apiGet(t: GarminTokens, path: String): JsonElement {
        val rb = Request.Builder().url("$api$path")
        nativeHeaders.forEach { (k, v) -> rb.header(k, v) }
        val req = rb.header("Authorization", "Bearer ${t.accessToken}").header("Accept", "application/json").build()
        return http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            when {
                r.code == 204 || s.isBlank() -> JsonNull
                !r.isSuccessful -> throw GarminException("Garmin API ${r.code} на $path")
                else -> json.parseToJsonElement(s)
            }
        }
    }

    // ---------------- Синхронизация ----------------

    /** Тянет последние [days] дней: сводка дня, сон, HRV, активности. */
    suspend fun sync(days: Int = 7): String = withContext(Dispatchers.IO) {
        val t = fresh()
        val dn = t.displayName ?: throw GarminException("Нет displayName профиля Garmin — перезайдите.")
        val today = LocalDate.now()
        var ok = 0
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            val ds = d.toString()
            val sum = runCatching { apiGet(t, "/usersummary-service/usersummary/daily/$dn?calendarDate=$ds") as? JsonObject }.getOrNull()
            val sleep = runCatching { apiGet(t, "/wellness-service/wellness/dailySleepData/$dn?date=$ds&nonSleepBufferMinutes=60") as? JsonObject }.getOrNull()
            val hrv = runCatching { apiGet(t, "/hrv-service/hrv/$ds") as? JsonObject }.getOrNull()
            val sleepDto = sleep?.get("dailySleepDTO") as? JsonObject
            val score = ((sleepDto?.get("sleepScores") as? JsonObject)?.get("overall") as? JsonObject)?.int("value")
            db.garmin().upsertDay(GarminDay(
                day = d.toEpochDay(),
                steps = sum?.int("totalSteps"),
                restingHr = sum?.int("restingHeartRate"),
                sleepMinutes = sleepDto?.int("sleepTimeSeconds")?.div(60),
                sleepScore = score,
                hrvAvg = (hrv?.get("hrvSummary") as? JsonObject)?.int("lastNightAvg"),
                bodyBatteryHigh = sum?.int("bodyBatteryHighestValue"),
                bodyBatteryLow = sum?.int("bodyBatteryLowestValue"),
                stressAvg = sum?.int("averageStressLevel")?.takeIf { it >= 0 },
                activeKcal = sum?.int("activeKilocalories"),
            ))
            if (sum != null) ok++
        }
        val acts = runCatching { apiGet(t, "/activitylist-service/activities/search/activities?start=0&limit=20").jsonArray }.getOrNull()
        acts?.forEach { el ->
            val o = el.jsonObject
            val id = o["activityId"]?.jsonPrimitive?.longOrNull ?: return@forEach
            val start = o["startTimeLocal"]?.jsonPrimitive?.content?.take(10)
            val day = runCatching { LocalDate.parse(start).toEpochDay() }.getOrDefault(today.toEpochDay())
            db.garmin().upsertActivity(GarminActivity(
                activityId = id, day = day,
                type = (o["activityType"] as? JsonObject)?.get("typeKey")?.jsonPrimitive?.content ?: "other",
                name = o["activityName"]?.jsonPrimitive?.content ?: "",
                durationMin = (o["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 60,
                distanceKm = o["distance"]?.jsonPrimitive?.doubleOrNull?.div(1000),
                avgHr = o.int("averageHR"),
                kcal = o.int("calories"),
                trainingLoad = o["activityTrainingLoad"]?.jsonPrimitive?.doubleOrNull,
            ))
        }
        settings.garminLastSync = System.currentTimeMillis()
        "Синхронизировано: дней $ok из $days, активностей ${acts?.size ?: 0}."
    }

    private fun JsonObject.int(k: String): Int? = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.let { p ->
        p.intOrNull ?: p.doubleOrNull?.toInt()
    }

    // ---------------- HTTP-помощники ----------------

    private fun String.withQuery(q: Map<String, String>): String {
        val b = this.toHttpUrl().newBuilder()
        q.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private fun get(url: String, referer: String? = null): String {
        val rb = Request.Builder().url(url).header("User-Agent", iosUa)
        referer?.let { rb.header("Referer", it) }
        return http.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) throw GarminException("Garmin SSO ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    private fun post(url: String, form: Map<String, String>, referer: String?): String {
        val fb = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val rb = Request.Builder().url(url).post(fb).header("User-Agent", iosUa)
        referer?.let { rb.header("Referer", it) }
        return http.newCall(rb.build()).execute().use { r ->
            if (r.code == 429) throw GarminException("Garmin: слишком много попыток входа, подождите час.")
            if (!r.isSuccessful) throw GarminException("Garmin SSO ${r.code}")
            r.body?.string().orEmpty()
        }
    }
}
