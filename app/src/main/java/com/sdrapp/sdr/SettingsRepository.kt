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
    val agcEnabled: Boolean = true,

    // Hardware (RTL-SDR)
    val ppmCorrection: Int = 0,
    val rtlAgc: Boolean = false,
    val directSampling: Int = 0,          // 0 = off, 1 = I, 2 = Q
    val biasTee: Boolean = false,
    val offsetTuning: Boolean = false,

    // Demodulation
    val deemphasisUs: Int = 75,           // 0 / 50 / 75
    val noiseReduction: Boolean = false,
    val squelchEnabled: Boolean = false,

    // Audio
    val audioVolume: Float = 80f,         // 0..100

    // Display / FFT
    val fftSize: Int = 8192,
    val fftWindow: String = "blackman",
    val fftRate: Int = 20,                // frames per second
    val dbMin: Float = -120f,
    val dbMax: Float = -20f,
    val averaging: Int = 25,              // 0..100
    val waterfallPalette: String = "classic",
    val splitRatio: Float = 50f,          // % of height for spectrum
    val waterfallSpeed: Int = 60,         // ms per line

    // Tuning
    val snapToStep: Boolean = false,
    val stepHz: Int = 25_000
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
            put("ppmCorrection", settings.ppmCorrection)
            put("rtlAgc", settings.rtlAgc)
            put("directSampling", settings.directSampling)
            put("biasTee", settings.biasTee)
            put("offsetTuning", settings.offsetTuning)
            put("deemphasisUs", settings.deemphasisUs)
            put("noiseReduction", settings.noiseReduction)
            put("squelchEnabled", settings.squelchEnabled)
            put("audioVolume", settings.audioVolume.toDouble())
            put("fftSize", settings.fftSize)
            put("fftWindow", settings.fftWindow)
            put("fftRate", settings.fftRate)
            put("dbMin", settings.dbMin.toDouble())
            put("dbMax", settings.dbMax.toDouble())
            put("averaging", settings.averaging)
            put("waterfallPalette", settings.waterfallPalette)
            put("splitRatio", settings.splitRatio.toDouble())
            put("waterfallSpeed", settings.waterfallSpeed)
            put("snapToStep", settings.snapToStep)
            put("stepHz", settings.stepHz)
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
                agcEnabled = json.optBoolean("agcEnabled", true),
                ppmCorrection = json.optInt("ppmCorrection", 0),
                rtlAgc = json.optBoolean("rtlAgc", false),
                directSampling = json.optInt("directSampling", 0),
                biasTee = json.optBoolean("biasTee", false),
                offsetTuning = json.optBoolean("offsetTuning", false),
                deemphasisUs = json.optInt("deemphasisUs", 75),
                noiseReduction = json.optBoolean("noiseReduction", false),
                squelchEnabled = json.optBoolean("squelchEnabled", false),
                audioVolume = json.optDouble("audioVolume", 80.0).toFloat(),
                fftSize = json.optInt("fftSize", 8192),
                fftWindow = json.optString("fftWindow", "blackman"),
                fftRate = json.optInt("fftRate", 20),
                dbMin = json.optDouble("dbMin", -120.0).toFloat(),
                dbMax = json.optDouble("dbMax", -20.0).toFloat(),
                averaging = json.optInt("averaging", 25),
                waterfallPalette = json.optString("waterfallPalette", "classic"),
                splitRatio = json.optDouble("splitRatio", 50.0).toFloat(),
                waterfallSpeed = json.optInt("waterfallSpeed", 60),
                snapToStep = json.optBoolean("snapToStep", false),
                stepHz = json.optInt("stepHz", 25_000)
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
