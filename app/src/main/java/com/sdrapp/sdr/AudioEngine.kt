package com.sdrapp.sdr

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Low-latency PCM playback of demodulated mono audio. */
class AudioEngine(private val sampleRate: Int = 48_000) {

    private var track: AudioTrack? = null
    private var shortBuf = ShortArray(0)

    @Volatile
    private var volume = 0.8f

    val isRunning: Boolean get() = track != null

    /** Sets output volume in the 0..100 range. */
    fun setVolume(percent: Float) {
        volume = (percent / 100f).coerceIn(0f, 1f)
    }

    fun start() {
        if (track != null) return

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = if (minBuf > 0) minBuf * 4 else sampleRate / 2 * 4

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        t.play()
        track = t
        shortBuf = ShortArray(4096)
    }

    fun stop() {
        track?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        track = null
    }

    fun write(samples: FloatArray, count: Int) {
        val t = track ?: return
        if (count <= 0) return
        val v = volume
        if (shortBuf.size < count) shortBuf = ShortArray(count)
        for (n in 0 until count) {
            val s = (samples[n] * v * 32767f).toInt().coerceIn(-32768, 32767)
            shortBuf[n] = s.toShort()
        }
        t.write(shortBuf, 0, count)
    }
}
