package com.sdrapp.sdr

import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Turns a stream of 8-bit unsigned interleaved IQ samples into a smoothed
 * magnitude spectrum (dBFS) for the spectrum / waterfall display.
 *
 * The analyser is fed from the reader thread and publishes an immutable
 * snapshot through [bins] (guarded by [seq]) which the UI thread reads.
 */
class SpectrumAnalyzer {

    @Volatile var bins: FloatArray = FloatArray(0)
        private set
    @Volatile var seq: Long = 0L
        private set
    @Volatile var centerHz: Double = 0.0
        private set
    @Volatile var sampleRate: Int = 2_400_000
        private set
    @Volatile var fftSize: Int = 1024
        private set

    private var fft = Fft(1024)
    private var window = DoubleArray(1024)
    private var winGain = 1.0

    private var scratch = FloatArray(1024)
    private var avg = FloatArray(1024)
    private var avgInit = false

    private val re = DoubleArray(8192)
    private val im = DoubleArray(8192)

    @Synchronized
    fun configure(fftSizeIn: Int, windowName: String, rate: Int, center: Double) {
        val size = fftSizeIn.coerceIn(256, 8192).let { prevPow2(it) }
        centerHz = center
        sampleRate = rate
        if (size != fftSize || window.size != size) {
            fftSize = size
            fft = Fft(size)
            window = makeWindow(windowName, size)
            winGain = window.sum() / size
            if (winGain < 1e-6) winGain = 1.0
            scratch = FloatArray(size)
            avg = FloatArray(size)
            avgInit = false
            bins = FloatArray(0)
        } else {
            val newWin = makeWindow(windowName, size)
            if (!newWin.contentEquals(window)) {
                window = newWin
                winGain = window.sum() / size
                if (winGain < 1e-6) winGain = 1.0
            }
        }
    }

    /** Analyse the most recent [fftSize] complex samples contained in [bytes]. */
    @Synchronized
    fun process(bytes: ByteArray, byteCount: Int, averaging: Int) {
        val n = fftSize
        if (scratch.size != n) scratch = FloatArray(n)
        if (avg.size != n) { avg = FloatArray(n); avgInit = false }
        val pairs = byteCount / 2
        val start = if (pairs >= n) pairs - n else 0
        val scale = 1.0 / (n * winGain)

        for (k in 0 until n) {
            val src = if (pairs >= n) start + k else k
            if (src < pairs) {
                val idx = src * 2
                val i = ((bytes[idx].toInt() and 0xff) - 127.5) / 127.5
                val q = ((bytes[idx + 1].toInt() and 0xff) - 127.5) / 127.5
                re[k] = i * window[k]
                im[k] = q * window[k]
            } else {
                re[k] = 0.0
                im[k] = 0.0
            }
        }

        fft.transform(re, im)

        // fftshift: bin j covers offset (j - n/2) * rate/n, i.e. -rate/2 .. +rate/2.
        val half = n / 2
        val out = scratch
        for (j in 0 until n) {
            val k = (j + half) % n
            val mag = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
            out[j] = if (mag < 1e-9) -160f else (20.0 * log10(mag)).toFloat()
        }

        val alpha = (1f - averaging.coerceIn(0, 100) / 100f * 0.9f).coerceIn(0.05f, 1f)
        if (!avgInit || avg.size != n) {
            avg = out.copyOf()
            avgInit = true
        } else {
            for (k in 0 until n) avg[k] += alpha * (out[k] - avg[k])
        }

        bins = avg.copyOf()
        seq++
    }

    @Synchronized
    fun reset() {
        avgInit = false
        bins = FloatArray(0)
        seq = 0L
    }

    fun setCenter(center: Double) {
        centerHz = center
    }

    private fun makeWindow(name: String, n: Int): DoubleArray {
        val w = DoubleArray(n)
        for (i in 0 until n) {
            val x = i.toDouble() / (n - 1)
            w[i] = when (name.lowercase()) {
                "hann" -> 0.5 - 0.5 * cos(2.0 * Math.PI * x)
                "hamming" -> 0.54 - 0.46 * cos(2.0 * Math.PI * x)
                "blackman" -> 0.42 - 0.5 * cos(2.0 * Math.PI * x) + 0.08 * cos(4.0 * Math.PI * x)
                else -> 1.0
            }
        }
        return w
    }

    private fun prevPow2(v: Int): Int {
        var p = 1
        while (p * 2 <= v) p *= 2
        return p
    }
}
