package com.sdrapp.sdr

import android.content.Context
import org.json.JSONObject

data class AppSettings(
    val frequency: Double = 100_000_000.0,
    val demodType: DemodType = DemodType.WFM,
    val gainPercent: Float = 50f,
    val bandwidthHz: Float = 12_500f,
    val sampleRate: Int = 2_400_000,
    val squelchDb: Float = -70f,
    val agcEnabled: Boolean = true
)

/** Persists the application settings as JSON in SharedPreferences. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("sdr_settings", Context.MODE_PRIVATE)

    fun save(settings: AppSettings) {
        val json = JSONObject().apply {
            put("frequency", settings.frequency)
            put("demodType", settings.demodType.name)
            put("gainPercent", settings.gainPercent.toDouble())
            put("bandwidthHz", settings.bandwidthHz.toDouble())
            put("sampleRate", settings.sampleRate)
            put("squelchDb", settings.squelchDb.toDouble())
            put("agcEnabled", settings.agcEnabled)
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun load(): AppSettings {
        val raw = prefs.getString(KEY, null) ?: return AppSettings()
        return try {
            val json = JSONObject(raw)
            AppSettings(
                frequency = json.optDouble("frequency", 100_000_000.0),
                demodType = runCatching { DemodType.valueOf(json.optString("demodType", "WFM")) }
                    .getOrDefault(DemodType.WFM),
                gainPercent = json.optDouble("gainPercent", 50.0).toFloat(),
                bandwidthHz = json.optDouble("bandwidthHz", 12_500.0).toFloat(),
                sampleRate = json.optInt("sampleRate", 2_400_000),
                squelchDb = json.optDouble("squelchDb", -70.0).toFloat(),
                agcEnabled = json.optBoolean("agcEnabled", true)
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY = "settings_json"
    }
}
