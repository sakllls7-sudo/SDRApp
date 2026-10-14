package com.sdrapp.sdr

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Records and replays no hardware. Generates synthetic IQ so the whole
 * DSP/UI chain can be exercised on any device without a dongle.
 */
class MockSdrDevice : SdrDevice {

    override val name: String get() = "Mock SDR"
    override val tunerName: String get() = "Simulated"

    @Volatile
    override var isOpen = false
        private set

    private var sampleRate = 2_400_000
    private var phase = 0.0
    private var modPhase = 0.0
    private var tonePhase = 0.0
    private var noiseSeed = 0x1234567L

    override fun open(): Boolean {
        isOpen = true
        return true
    }

    override fun close() {
        isOpen = false
    }

    override fun setSampleRate(rateHz: Int): Boolean {
        sampleRate = rateHz
        return true
    }

    override fun setCenterFrequency(freqHz: Long): Boolean = true

    override fun setGain(gainTenthDb: Int): Boolean = true

    override fun readSync(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!isOpen) return -1

        // A tone at +40 kHz with slow FM, plus low-level noise.
        val audioTone = 1_000.0 * 2.0 * PI
        val carrier = 40_000.0 * 2.0 * PI
        val fmDev = 3_000.0 * 2.0 * PI
        val dt = 1.0 / sampleRate
        val pairs = length / 2

        for (n in 0 until pairs) {
            tonePhase += audioTone * dt
            val mod = sin(tonePhase) * fmDev
            phase += (carrier + mod) * dt
            if (phase > PI * 2) phase -= PI * 2
            if (phase < 0) phase += PI * 2

            val ni = (nextNoise() * 0.06f)
            val nq = (nextNoise() * 0.06f)

            val i = (0.5 * cos(phase) + ni).coerceIn(-1.0, 1.0)
            val q = (0.5 * sin(phase) + nq).coerceIn(-1.0, 1.0)

            buffer[offset + n * 2] = ((i * 127.0) + 127.5).toInt().toByte()
            buffer[offset + n * 2 + 1] = ((q * 127.0) + 127.5).toInt().toByte()
        }
        // fill the tail if odd length
        if (length % 2 != 0) buffer[offset + length - 1] = 0x80.toByte()
        return length
    }

    private fun nextNoise(): Float {
        noiseSeed = (noiseSeed * 6364136223846793005L + 1442695040888963407L)
        val v = ((noiseSeed ushr 33) and 0xFFFF).toInt()
        return (v / 32768f) - 1f
    }
}
