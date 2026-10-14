package com.sdrapp.sdr

import com.sdrapp.sdr.native.AAudioEngine

class AudioEngine(private val sampleRate: Int = 48_000) {

    private val nativeEngine = AAudioEngine()
    private var isStarted = false
    private var volume = 0.8f

    val isRunning: Boolean get() = isStarted

    fun setVolume(percent: Float) {
        volume = (percent / 100f).coerceIn(0f, 1f)
        nativeEngine.nativeSetVolume(volume)
    }

    fun start() {
        if (isStarted) return
        val ok = nativeEngine.nativeInit(sampleRate)
        if (!ok) {
            android.util.Log.e("AudioEngine", "AAudio init failed, falling back to AudioTrack")
            return
        }
        nativeEngine.nativeSetVolume(volume)
        isStarted = true
    }

    fun stop() {
        if (!isStarted) return
        nativeEngine.nativeStop()
        isStarted = false
    }

    fun write(samples: FloatArray, count: Int) {
        if (!isStarted || count <= 0) return
        nativeEngine.nativeWrite(samples, count)
    }
}
