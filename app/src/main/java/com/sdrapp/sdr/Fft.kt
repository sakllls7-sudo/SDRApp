package com.sdrapp.sdr

import kotlin.math.cos
import kotlin.math.sin

/**
 * Self-contained iterative radix-2 Cooley-Tukey FFT.
 *
 * [n] must be a power of two. Twiddle factors and the bit-reversal table are
 * precomputed once so repeated [transform] calls are allocation free.
 */
class Fft(private val n: Int) {

    private val cosTab = DoubleArray(n / 2)
    private val sinTab = DoubleArray(n / 2)
    private val rev = IntArray(n)

    init {
        require(n > 1 && (n and (n - 1)) == 0) { "FFT size must be a power of two" }
        for (i in 0 until n / 2) {
            val a = 2.0 * Math.PI * i / n
            cosTab[i] = cos(a)
            sinTab[i] = sin(a)
        }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            rev[i] = j
        }
    }

    /** In-place complex FFT of length [n]. */
    fun transform(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                var j = i
                val end = i + half
                while (j < end) {
                    val c = cosTab[k]
                    val s = sinTab[k]
                    val jh = j + half
                    val tre = c * re[jh] + s * im[jh]
                    val tim = -s * re[jh] + c * im[jh]
                    re[jh] = re[j] - tre
                    im[jh] = im[j] - tim
                    re[j] += tre
                    im[j] += tim
                    k += step
                    j++
                }
                i += size
            }
            size = size shl 1
        }
    }
}
