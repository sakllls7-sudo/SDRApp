package com.sdrapp.sdr

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Streaming IQ -> audio demodulator.
 *
 * Input is 8-bit unsigned interleaved IQ at [inputRate].
 * Output is mono float audio at roughly [audioRate] (real rate =
 * inputRate / decim). A biquad low-pass band-limits the signal before
 * the boxcar decimation stage.
 */
class Demodulator(
    private val mode: DemodType,
    private val inputRate: Int,
    bandwidthHz: Double,
    private val audioRate: Int = 48_000,
    private val squelchEnabled: Boolean = false,
    squelchDb: Float = -70f,
    deemphasisUs: Int = 75,
    private val noiseReduction: Boolean = false
) {
    private val decim = (inputRate / audioRate).coerceAtLeast(1)

    private val fmDev: Double = when (mode) {
        DemodType.WFM -> 75_000.0
        DemodType.NFM -> 3_000.0
        else -> 3_000.0
    }

    private val fmGain: Double =
        if (mode == DemodType.WFM || mode == DemodType.NFM) inputRate / (2.0 * PI * fmDev) else 1.0

    private val shiftHz: Double = when (mode) {
        DemodType.USB -> bandwidthHz / 2.0
        DemodType.LSB -> -bandwidthHz / 2.0
        DemodType.CW -> 700.0
        else -> 0.0
    }

    private val fc: Double = when (mode) {
        DemodType.WFM -> 15_000.0
        DemodType.NFM -> bandwidthHz / 2.0
        DemodType.AM -> bandwidthHz / 2.0
        DemodType.USB, DemodType.LSB -> bandwidthHz / 2.0
        DemodType.CW -> 400.0
        DemodType.RAW -> audioRate / 2.0
    }.coerceIn(80.0, inputRate * 0.45)

    // biquad low-pass (RBJ)
    private val lpB0: Double
    private val lpB1: Double
    private val lpB2: Double
    private val lpA1: Double
    private val lpA2: Double
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    // DC-blocking one-pole high-pass
    private var hpPrevX = 0.0
    private var hpPrevY = 0.0
    private val hpAlpha: Double

    private var prevPhase = 0.0
    private var mixPhase = 0.0

    private var acc = 0.0
    private var accCount = 0

    // De-emphasis (WFM): one-pole low-pass with the selected time constant.
    private val deAlpha: Double = if (deemphasisUs > 0 && mode == DemodType.WFM) {
        val tau = deemphasisUs * 1e-6
        val dt = 1.0 / audioRate
        dt / (tau + dt)
    } else 0.0
    private var deState = 0.0

    // Light noise-reduction smoothing (hiss reduction).
    private val nrAlpha: Double = if (noiseReduction) {
        val dt = 1.0 / audioRate
        val tau = 1.0 / (2.0 * PI * 3000.0)
        dt / (tau + dt)
    } else 0.0
    private var nrState = 0.0

    // Squelch: mute output when the audio envelope falls below the threshold.
    private val squelchLin: Double = 10.0.pow(squelchDb / 20.0)
    private var env = 0.0

    init {
        val w0 = 2.0 * PI * fc / inputRate
        val cosw = cos(w0)
        val sinw = sin(w0)
        val q = 0.707
        val alpha = sinw / (2.0 * q)
        val a0 = 1.0 + alpha
        lpB0 = ((1.0 - cosw) / 2.0) / a0
        lpB1 = (1.0 - cosw) / a0
        lpB2 = ((1.0 - cosw) / 2.0) / a0
        lpA1 = (-2.0 * cosw) / a0
        lpA2 = (1.0 - alpha) / a0

        val rc = 1.0 / (2.0 * PI * 25.0)
        val dt = 1.0 / inputRate
        hpAlpha = rc / (rc + dt)
    }

    /** Returns the number of audio samples written to [out]. */
    fun process(bytes: ByteArray, byteCount: Int, out: FloatArray): Int {
        var outIdx = 0
        val pairs = byteCount / 2

        for (n in 0 until pairs) {
            val i = ((bytes[n * 2].toInt() and 0xff) - 127.5) / 127.5
            val q = ((bytes[n * 2 + 1].toInt() and 0xff) - 127.5) / 127.5

            val s: Double = when (mode) {
                DemodType.AM -> sqrt(i * i + q * q)

                DemodType.NFM, DemodType.WFM -> {
                    val ph = atan2(q, i)
                    var d = ph - prevPhase
                    if (d > PI) d -= 2.0 * PI
                    if (d < -PI) d += 2.0 * PI
                    prevPhase = ph
                    d * fmGain
                }

                DemodType.USB, DemodType.LSB, DemodType.CW -> {
                    mixPhase += 2.0 * PI * shiftHz / inputRate
                    if (mixPhase > 2.0 * PI) mixPhase -= 2.0 * PI
                    i * cos(mixPhase) + q * sin(mixPhase)
                }

                DemodType.RAW -> i
            }

            // biquad low-pass
            val y = lpB0 * s + lpB1 * x1 + lpB2 * x2 - lpA1 * y1 - lpA2 * y2
            x2 = x1; x1 = s
            y2 = y1; y1 = y

            // boxcar decimation
            acc += y
            accCount++
            if (accCount >= decim) {
                var a = acc / decim
                acc = 0.0
                accCount = 0

                // DC block
                val hp = hpAlpha * (hpPrevY + a - hpPrevX)
                hpPrevX = a
                hpPrevY = hp
                a = hp

                if (nrAlpha > 0.0) {
                    nrState += nrAlpha * (a - nrState)
                    a = nrState
                }

                if (deAlpha > 0.0) {
                    deState += deAlpha * (a - deState)
                    a = deState
                }

                if (outIdx < out.size) {
                    env += 0.01 * (kotlin.math.abs(a) - env)
                    val gated = if (squelchEnabled && env < squelchLin) 0.0 else a
                    out[outIdx++] = (tanh(gated * 1.5) * 0.95).toFloat()
                }
            }
        }
        return outIdx
    }
}
