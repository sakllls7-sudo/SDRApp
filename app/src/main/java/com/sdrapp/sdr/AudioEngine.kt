package com.sdrapp.sdr

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Low-latency PCM playback of demodulated mono audio. */
class AudioEngine(private val sampleRate: Int = 48_000) {

    private var track: AudioTrack? = null
    private var shortBuf = ShortArray(0)
    private val running = AtomicBoolean(false)
    private var sinkThread: Thread? = null
    private val sink = ArrayBlockingQueue<FloatArray>(128)

    @Volatile
    private var volume = 0.8f

    val isRunning: Boolean get() = track != null

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
        val bufSize = if (minBuf > 0) minBuf * 8 else sampleRate / 2 * 8

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

        running.set(true)
        sinkThread = Thread({
            // Pre-fill buffer with silence to prevent initial underrun
            val silence = ShortArray(bufSize / 2)
            t.write(silence, 0, silence.size)
            while (running.get()) {
                try {
                    val samples = sink.take()
                    val count = samples.size
                    val v = volume
                    if (shortBuf.size < count) shortBuf = ShortArray(count)
                    for (n in 0 until count) {
                        val s = (samples[n] * v * 32767f).toInt().coerceIn(-32768, 32767)
                        shortBuf[n] = s.toShort()
                    }
                    t.write(shortBuf, 0, count)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    break
                }
            }
        }, "SdrAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running.set(false)
        sinkThread?.interrupt()
        sinkThread = null
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
        if (count <= 0) return
        val copy = samples.copyOf(count)
        if (!sink.offer(copy)) {
            sink.poll()
            sink.offer(copy)
        }
    }
}
