package com.tikhomirov.zdorovyak.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tikhomirov.zdorovyak.strength.Inventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Profile(
    val name: String = "Антон",
    val age: Int = 50,
    val heightCm: Int = 180,
    val targetWeightKg: Double? = null,
    val kcalTarget: Int = 2200,
    val proteinTarget: Int = 140,
    val fatTarget: Int = 70,
    val carbsTarget: Int = 220,
    val goal: String = "Снизить вес и стать сильнее",
    // Авто-нормы КБЖУ (nutrition/Targets.kt). Значения enum храним строками, чтобы старый JSON читался.
    val sex: String = "M",
    val activity: String = "LIGHT",
    val goalMode: String = "LOSE",
    val rateKgPerWeek: Double = 0.5,
    val proteinPerKg: Double = 1.8,
    val autoTargets: Boolean = true,
    val experience: String = "небольшой",
    val trainingDays: Int = 3,
    /** Дни силовых, ISO: 1 — понедельник … 7 — воскресенье. Пусто — ещё не выбирали (берём по trainingDays). */
    val trainingWeekdays: List<Int> = emptyList(),
    /** Напоминание в день тренировки, час (0–23); -1 — выключено. */
    val remindHour: Int = 18,
    val sessionMinutes: Int = 50,
    val limitations: String = "",
    val inventory: Inventory = Inventory(),
) {
    /** Дни тренировок с учётом старых профилей, где выбирали только «сколько раз в неделю». */
    val weekdays: Set<Int> get() = trainingWeekdays.ifEmpty { com.tikhomirov.zdorovyak.strength.Schedule.defaultFor(trainingDays) }.toSet()
}

/** Состояние адаптивного расхода: текущая оценка, когда пересчитывали, пояснение для экрана. */
@Serializable
data class EnergyState(
    val tdee: Double? = null,          // null — ещё не было расчёта по факту, работает формула
    val adaptive: Boolean = false,     // true — оценка уже по факту (вес + питание)
    val lastCheckDay: Long = 0,
    val note: String = "",
    val formulaTdee: Double? = null,
)

/**
 * Обычные настройки — в SharedPreferences, секреты (ключ Claude, пароль/токены Garmin)
 * — в EncryptedSharedPreferences (ключ шифрования в Android Keystore).
 */
class Settings(ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val plain: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = runCatching { openSecure(ctx) }.getOrElse {
        // На некоторых прошивках Keystore иногда портит файл — пересоздаём, в крайнем случае без шифрования
        ctx.deleteSharedPreferences("secure")
        runCatching { openSecure(ctx) }.getOrElse { ctx.getSharedPreferences("secure_fallback", Context.MODE_PRIVATE) }
    }

    private fun openSecure(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<Profile> = _profile

    private fun loadProfile(): Profile =
        plain.getString("profile", null)?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
            ?: Profile()

    fun saveProfile(p: Profile) {
        plain.edit().putString("profile", json.encodeToString(p)).apply()
        _profile.value = p
    }

    private val _energy = MutableStateFlow(
        plain.getString("energy", null)?.let { runCatching { json.decodeFromString<EnergyState>(it) }.getOrNull() } ?: EnergyState()
    )
    val energy: StateFlow<EnergyState> = _energy

    fun saveEnergy(e: EnergyState) {
        plain.edit().putString("energy", json.encodeToString(e)).apply()
        _energy.value = e
    }

    var claudeKey: String
        get() = secure.getString("claude_key", "") ?: ""
        set(v) = secure.edit().putString("claude_key", v.trim()).apply()

    var claudeModel: String
        get() = plain.getString("claude_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = plain.edit().putString("claude_model", v.trim().ifEmpty { DEFAULT_MODEL }).apply()

    var garminEmail: String
        get() = secure.getString("garmin_email", "") ?: ""
        set(v) = secure.edit().putString("garmin_email", v.trim()).apply()

    var garminPassword: String
        get() = secure.getString("garmin_password", "") ?: ""
        set(v) = secure.edit().putString("garmin_password", v).apply()

    var garminTokens: String
        get() = secure.getString("garmin_tokens", "") ?: ""
        set(v) = secure.edit().putString("garmin_tokens", v).apply()

    var garminLastSync: Long
        get() = plain.getLong("garmin_last_sync", 0)
        set(v) = plain.edit().putLong("garmin_last_sync", v).apply()

    /** Долговременная память коуча: сжатая выжимка фактов о человеке, обновляется ИИ. */
    var coachMemory: String
        get() = secure.getString("coach_memory", "") ?: ""
        set(v) = secure.edit().putString("coach_memory", v).apply()

    var coachMemoryUpdatedAtCount: Int
        get() = plain.getInt("coach_mem_count", 0)
        set(v) = plain.edit().putInt("coach_mem_count", v).apply()

    // ---------- Голос коуча ----------

    /** Озвучка ответов: off / device (Google TTS на телефоне) / yandex (SpeechKit) / google (Cloud TTS). */
    var ttsEngine: String
        get() = plain.getString("tts_engine", "device") ?: "device"
        set(v) = plain.edit().putString("tts_engine", v).apply()

    /** Когда озвучивать: voice — если вопрос задан голосом, always — всегда, manual — только по кнопке. */
    var ttsWhen: String
        get() = plain.getString("tts_when", "voice") ?: "voice"
        set(v) = plain.edit().putString("tts_when", v).apply()

    var ttsSpeed: Float
        get() = plain.getFloat("tts_speed", 1.0f)
        set(v) = plain.edit().putFloat("tts_speed", v.coerceIn(0.6f, 1.8f)).apply()

    var deviceVoice: String
        get() = plain.getString("device_voice", "") ?: ""
        set(v) = plain.edit().putString("device_voice", v).apply()

    var yandexKey: String
        get() = secure.getString("yandex_key", "") ?: ""
        set(v) = secure.edit().putString("yandex_key", v.trim()).apply()

    var yandexVoice: String
        get() = plain.getString("yandex_voice", "filipp") ?: "filipp"
        set(v) = plain.edit().putString("yandex_voice", v.trim().ifEmpty { "filipp" }).apply()

    var googleTtsKey: String
        get() = secure.getString("google_tts_key", "") ?: ""
        set(v) = secure.edit().putString("google_tts_key", v.trim()).apply()

    var googleVoice: String
        get() = plain.getString("google_voice", "ru-RU-Wavenet-D") ?: "ru-RU-Wavenet-D"
        set(v) = plain.edit().putString("google_voice", v.trim().ifEmpty { "ru-RU-Wavenet-D" }).apply()

    /** Отправлять сообщение коучу сразу после диктовки. */
    var voiceAutoSend: Boolean
        get() = plain.getBoolean("voice_autosend", true)
        set(v) = plain.edit().putBoolean("voice_autosend", v).apply()

    /** Счётчик облачной озвучки за текущий месяц (для оценки стоимости). */
    fun cloudCharsThisMonth(engine: String): Int {
        val m = java.time.YearMonth.now().toString()
        return if (plain.getString("tts_month_$engine", "") == m) plain.getInt("tts_chars_$engine", 0) else 0
    }

    fun addCloudChars(engine: String, n: Int) {
        val m = java.time.YearMonth.now().toString()
        val cur = cloudCharsThisMonth(engine)
        plain.edit().putString("tts_month_$engine", m).putInt("tts_chars_$engine", cur + n).apply()
    }

    var screeningPassed: Boolean
        get() = plain.getBoolean("screening_passed", false)
        set(v) = plain.edit().putBoolean("screening_passed", v).apply()

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}
