package com.sdrapp.sdr

/**
 * Abstraction over a physical (or simulated) SDR frontend.
 * Sample data is 8-bit unsigned IQ, interleaved: I0,Q0,I1,Q1,...
 */
interface SdrDevice {
    val name: String
    val tunerName: String
    val isOpen: Boolean

    fun open(): Boolean
    fun close()

    fun setSampleRate(rateHz: Int): Boolean
    fun setCenterFrequency(freqHz: Long): Boolean

    /** [gainTenthDb] < 0 selects automatic (AGC) gain. */
    fun setGain(gainTenthDb: Int): Boolean

    /**
     * Read raw samples into [buffer]. Returns the number of bytes read,
     * or a negative value on error.
     */
    fun readSync(buffer: ByteArray, offset: Int, length: Int): Int

    val gainValuesTenthDb: IntArray
        get() = GAINS_TENTH_DB

    companion object {
        /** Supported tuner gain steps, in tenths of a dB. */
        val GAINS_TENTH_DB = intArrayOf(
            0, 9, 14, 27, 37, 77, 87, 125, 144, 157, 166, 197, 207, 229,
            254, 280, 297, 328, 338, 364, 372, 386, 402, 421, 434, 439, 445, 480, 496
        )
    }
}

/** I2C transport used by the R820T tuner driver. */
interface I2cBus {
    fun i2cWrite(addr: Int, data: ByteArray, len: Int): Int
    fun i2cRead(addr: Int, reg: Int, out: ByteArray, len: Int): Int
}

enum class DemodType { WFM, NFM, AM, USB, LSB, CW, RAW }

data class SdrDeviceInfo(
    val name: String,
    val tuner: String,
    val vendorId: Int,
    val productId: Int
)
