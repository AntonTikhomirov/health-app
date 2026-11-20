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
    val experience: String = "небольшой",
    val trainingDays: Int = 3,
    val sessionMinutes: Int = 50,
    val limitations: String = "",
    val inventory: Inventory = Inventory(),
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

    var screeningPassed: Boolean
        get() = plain.getBoolean("screening_passed", false)
        set(v) = plain.edit().putBoolean("screening_passed", v).apply()

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}
