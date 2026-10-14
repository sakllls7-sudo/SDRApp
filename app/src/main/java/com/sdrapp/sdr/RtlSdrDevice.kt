package com.sdrapp.sdr

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Native RTL2832U driver over Android's UsbManager (USB host / OTG).
 * This is a Kotlin port of the relevant parts of librtlsdr (GPL-2.0):
 * baseband initialisation, sample-rate setup and the R820T tuner driver.
 */
class RtlSdrDevice(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice
) : SdrDevice, I2cBus {

    override val name: String get() = "RTL-SDR"
    override val tunerName: String get() = "R820T"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var r820t: R820T? = null

    private var sampleRate = 0
    private var centerFreq = 0L
    private var ppm = 0
    private var rtlAgc = false
    private var directSampling = 0

    @Volatile
    override var isOpen = false
        private set

    // ----- USB block / request constants ----------------------------------

    private companion object {
        const val RTL_XTAL = 28_800_000L
        const val IF_FREQ_DEFAULT = 3_570_000

        const val CTRL_IN = 0xC0
        const val CTRL_OUT = 0x40
        const val CTRL_TIMEOUT = 300

        const val DEMODB = 0
        const val USBB = 1
        const val SYSB = 2
        const val IICB = 6

        const val DEMOD_CTL_1 = 0x300b
        const val DEMOD_CTL = 0x3000
        const val USB_SYSCTL = 0x2000
        const val USB_EPA_MAXPKT = 0x2158
        const val USB_EPA_CTL = 0x2148

        // SYSB GPIO registers (bias tee)
        const val GPO = 0x3001
        const val GPI = 0x3002
        const val GPOE = 0x3003
        const val GPD = 0x3004
        const val BIAS_TEE_GPIO = 0

        val FIR_DEFAULT = intArrayOf(
            -54, -36, -41, -40, -32, -14, 14, 53,
            101, 156, 215, 273, 327, 372, 404, 421
        )
    }

    // ----- lifecycle ------------------------------------------------------

    override fun open(): Boolean {
        if (isOpen) return true

        val iface = findBulkInterface() ?: run {
            Log.e(TAG, "No bulk interface found")
            return false
        }
        val conn = usbManager.openDevice(usbDevice) ?: run {
            Log.e(TAG, "openDevice() failed")
            return false
        }
        if (!conn.claimInterface(iface, true)) {
            Log.e(TAG, "claimInterface() failed")
            conn.close()
            return false
        }

        usbInterface = iface
        connection = conn
        epIn = findBulkIn(iface)

        initBaseband()

        // R820T specific init (mirrors rtlsdr_open())
        demodWriteReg(1, 0xb1, 0x1a)          // disable Zero-IF mode
        demodWriteReg(0, 0x08, 0x4d)          // only enable In-phase ADC input
        setIfFreq(IF_FREQ_DEFAULT)
        demodWriteReg(1, 0x15, 0x01)          // enable spectrum inversion

        val tuner = R820T(this, R820T.Chip.R820T)
        withI2cRepeater { tuner.init() }
        r820t = tuner

        isOpen = true
        return true
    }

    override fun close() {
        if (!isOpen) return
        try {
            withI2cRepeater { }
        } catch (_: Exception) {
        }
        // power off demodulator / ADCs
        writeReg(SYSB, DEMOD_CTL, 0x20, 1)
        usbInterface?.let { connection?.releaseInterface(it) }
        connection?.close()
        connection = null
        usbInterface = null
        epIn = null
        isOpen = false
    }

    private fun findBulkInterface(): UsbInterface? {
        for (i in 0 until usbDevice.interfaceCount) {
            val iface = usbDevice.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_IN
                ) {
                    return iface
                }
            }
        }
        return if (usbDevice.interfaceCount > 0) usbDevice.getInterface(0) else null
    }

    private fun findBulkIn(iface: UsbInterface): UsbEndpoint? {
        for (e in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_IN
            ) {
                return ep
            }
        }
        return null
    }

    // ----- low level USB transfers ----------------------------------------

    private fun controlTransfer(
        requestType: Int,
        value: Int,
        index: Int,
        buffer: ByteArray,
        length: Int
    ): Int {
        val conn = connection ?: return -1
        return conn.controlTransfer(requestType, 0, value, index, buffer, length, CTRL_TIMEOUT)
    }

    private fun readArray(block: Int, addr: Int, out: ByteArray, len: Int): Int {
        val index = block shl 8
        return controlTransfer(CTRL_IN, addr, index, out, len)
    }

    private fun writeArray(block: Int, addr: Int, data: ByteArray, len: Int): Int {
        val index = (block shl 8) or 0x10
        return controlTransfer(CTRL_OUT, addr, index, data, len)
    }

    private fun readReg(block: Int, addr: Int, len: Int): Int {
        val buf = ByteArray(len)
        val r = readArray(block, addr, buf, len)
        if (r < 0) return -1
        return buf[0].toInt() and 0xff
    }

    private fun writeReg(block: Int, addr: Int, value: Int, len: Int): Int {
        val data = ByteArray(2)
        data[0] = if (len == 1) (value and 0xff).toByte() else ((value shr 8) and 0xff).toByte()
        data[1] = (value and 0xff).toByte()
        return writeArray(block, addr, data, len)
    }

    private fun demodWriteReg(page: Int, addr: Int, value: Int, len: Int = 1): Int {
        val index = 0x10 or page
        val a = (addr shl 8) or 0x20
        val data = ByteArray(2)
        data[0] = if (len == 1) (value and 0xff).toByte() else ((value shr 8) and 0xff).toByte()
        data[1] = (value and 0xff).toByte()
        val r = controlTransfer(CTRL_OUT, a, index, data, len)
        demodReadReg(0x0a, 0x01, 1)
        return if (r == len) 0 else -1
    }

    private fun demodReadReg(page: Int, addr: Int, len: Int): Int {
        val index = page
        val a = (addr shl 8) or 0x20
        val data = ByteArray(2)
        val r = controlTransfer(CTRL_IN, a, index, data, len)
        if (r < 0) return -1
        return ((data[1].toInt() and 0xff) shl 8) or (data[0].toInt() and 0xff)
    }

    // ----- I2cBus (used by the R820T driver) ------------------------------

    override fun i2cWrite(addr: Int, data: ByteArray, len: Int): Int {
        return writeArray(IICB, addr, data, len)
    }

    override fun i2cRead(addr: Int, reg: Int, out: ByteArray, len: Int): Int {
        val regBuf = byteArrayOf(reg.toByte())
        writeArray(IICB, addr, regBuf, 1)
        return readArray(IICB, addr, out, len)
    }

    private inline fun withI2cRepeater(block: () -> Unit) {
        setI2cRepeater(true)
        try {
            block()
        } finally {
            setI2cRepeater(false)
        }
    }

    private fun setI2cRepeater(on: Boolean) {
        demodWriteReg(1, 0x01, if (on) 0x18 else 0x10)
    }

    // ----- baseband -------------------------------------------------------

    private fun initBaseband() {
        // initialize USB
        writeReg(USBB, USB_SYSCTL, 0x09, 1)
        writeReg(USBB, USB_EPA_MAXPKT, 0x0002, 2)
        writeReg(USBB, USB_EPA_CTL, 0x1002, 2)

        // power on demod
        writeReg(SYSB, DEMOD_CTL_1, 0x22, 1)
        writeReg(SYSB, DEMOD_CTL, 0xe8, 1)

        // reset demod (bit 3, soft_rst)
        demodWriteReg(1, 0x01, 0x14)
        demodWriteReg(1, 0x01, 0x10)

        // disable spectrum inversion and adjacent channel rejection
        demodWriteReg(1, 0x15, 0x00)
        demodWriteReg(1, 0x16, 0x0000, 2)

        // clear both DDC shift and IF frequency registers
        for (i in 0 until 6) demodWriteReg(1, 0x16 + i, 0x00)

        setFir()

        // enable SDR mode, disable DAGC (bit 5)
        demodWriteReg(0, 0x19, 0x05)

        // init FSM state-holding register
        demodWriteReg(1, 0x93, 0xf0)
        demodWriteReg(1, 0x94, 0x0f)

        // disable AGC (en_dagc, bit 0)
        demodWriteReg(1, 0x11, 0x00)

        // disable RF and IF AGC loop
        demodWriteReg(1, 0x04, 0x00)

        // disable PID filter (enable_PID = 0)
        demodWriteReg(0, 0x61, 0x60)

        // opt_adc_iq = 0, default ADC_I/ADC_Q datapath
        demodWriteReg(0, 0x06, 0x80)

        // Enable Zero-IF mode, DC cancellation, IQ estimation/compensation
        demodWriteReg(1, 0xb1, 0x1b)

        // disable 4.096 MHz clock output on pin TP_CK0
        demodWriteReg(0, 0x0d, 0x83)
    }

    private fun setFir() {
        val fir = ByteArray(20)
        for (i in 0 until 8) {
            fir[i] = FIR_DEFAULT[i].toByte()
        }
        var i = 0
        while (i < 8) {
            val val0 = FIR_DEFAULT[8 + i]
            val val1 = FIR_DEFAULT[8 + i + 1]
            fir[8 + i * 3 / 2] = (val0 shr 4).toByte()
            fir[8 + i * 3 / 2 + 1] = ((val0 shl 4) or ((val1 shr 8) and 0x0f)).toByte()
            fir[8 + i * 3 / 2 + 2] = val1.toByte()
            i += 2
        }
        for (j in fir.indices) {
            demodWriteReg(1, 0x1c + j, fir[j].toInt() and 0xff)
        }
    }

    private fun setIfFreq(freq: Int) {
        val ifFreq = ((freq.toLong() * (1L shl 22)) / RTL_XTAL).toInt() * -1
        demodWriteReg(1, 0x19, (ifFreq shr 16) and 0x3f)
        demodWriteReg(1, 0x1a, (ifFreq shr 8) and 0xff)
        demodWriteReg(1, 0x1b, ifFreq and 0xff)
    }

    private fun setSampleFreqCorrection(ppm: Int) {
        val offs = (ppm.toLong() * -1L * (1L shl 24) / 1_000_000L).toInt()
        demodWriteReg(1, 0x3f, offs and 0xff)
        demodWriteReg(1, 0x3e, (offs shr 8) and 0x3f)
    }

    /** Reset and start the bulk-in endpoint (mirrors rtlsdr_reset_buffer). */
    override fun startStreaming(): Boolean {
        // Dummy read
        readReg(USBB, USB_EPA_CTL, 2)
        try {
            Thread.sleep(10)
        } catch (_: InterruptedException) {
        }
        writeReg(USBB, USB_EPA_CTL, 0x1002, 2)
        try {
            Thread.sleep(10)
        } catch (_: InterruptedException) {
        }
        writeReg(USBB, USB_EPA_CTL, 0x0000, 2)
        try {
            Thread.sleep(10)
        } catch (_: InterruptedException) {
        }
        return true
    }

    // ----- public SDR API -------------------------------------------------

    override fun setSampleRate(rateHz: Int): Boolean {
        val r = rateHz
        if (r <= 225_000 || r > 3_200_000 || (r > 300_000 && r <= 900_000)) {
            Log.e(TAG, "Invalid sample rate: $r Hz")
            return false
        }

        var rsampRatio = ((RTL_XTAL shl 22) / r).toInt()
        rsampRatio = rsampRatio and 0x0ffffffc
        val realRatio = rsampRatio.toLong() or (((rsampRatio.toLong()) and 0x08000000L) shl 1)
        val realRate = (RTL_XTAL.toDouble() * (1 shl 22).toDouble()) / realRatio.toDouble()
        sampleRate = realRate.toInt()

        demodWriteReg(1, 0x9f, (rsampRatio shr 16) and 0xffff, 2)
        demodWriteReg(1, 0xa1, rsampRatio and 0xffff, 2)

        setSampleFreqCorrection(0)

        // reset demod (bit 3, soft_rst)
        demodWriteReg(1, 0x01, 0x14)
        demodWriteReg(1, 0x01, 0x10)

        return true
    }

    override fun setCenterFrequency(freqHz: Long): Boolean {
        val tuner = r820t ?: return false
        val r = withI2cRepeaterResult { tuner.setFreq(freqHz) }
        if (r >= 0) {
            centerFreq = freqHz
            return true
        }
        return false
    }

    override fun setGain(gainTenthDb: Int): Boolean {
        val tuner = r820t ?: return false
        val vgaCtrl = withI2cRepeaterResult {
            if (gainTenthDb < 0) tuner.setGain(false, 0) else tuner.setGain(true, gainTenthDb)
        }
        // enable/disable RF AGC loop in the RTL2832U
        demodWriteReg(1, 0x04, if (vgaCtrl != 0) 0x80 else 0x00)
        return true
    }

    override fun setPpmCorrection(ppm: Int): Boolean {
        this.ppm = ppm
        r820t?.setFreqCorrection(ppm)
        setSampleFreqCorrection(ppm)
        if (centerFreq > 0) setCenterFrequency(centerFreq)
        return true
    }

    override fun setRtlAgc(on: Boolean): Boolean {
        rtlAgc = on
        return demodWriteReg(0, 0x19, if (on) 0x25 else 0x05) == 0
    }

    override fun setDirectSampling(mode: Int): Boolean {
        if (mode == directSampling) return true
        directSampling = mode
        if (mode != 0) {
            // disable Zero-IF mode
            demodWriteReg(1, 0xb1, 0x1a)
            // disable spectrum inversion
            demodWriteReg(1, 0x15, 0x00)
            // only enable In-phase ADC input
            demodWriteReg(0, 0x08, 0x4d)
            // swap I and Q ADC to select the input
            demodWriteReg(0, 0x06, if (mode > 1) 0x90 else 0x80)
        } else {
            r820t?.let { withI2cRepeater { it.init() } }
            setIfFreq(IF_FREQ_DEFAULT)
            demodWriteReg(1, 0x15, 0x01) // enable spectrum inversion
            demodWriteReg(0, 0x08, 0xcd) // enable In-phase + Quadrature ADC
            demodWriteReg(1, 0xb1, 0x1b) // enable Zero-IF mode
            demodWriteReg(0, 0x06, 0x80) // default ADC_I/ADC_Q datapath
        }
        if (centerFreq > 0) setCenterFrequency(centerFreq)
        return true
    }

    override fun setBiasTee(on: Boolean): Boolean {
        val gpio = 1 shl BIAS_TEE_GPIO
        var r = readReg(SYSB, GPD, 1)
        if (r < 0) return false
        writeReg(SYSB, GPD, r and gpio.inv(), 1)
        r = readReg(SYSB, GPOE, 1)
        if (r < 0) return false
        writeReg(SYSB, GPOE, r or gpio, 1)
        r = readReg(SYSB, GPO, 1)
        if (r < 0) return false
        writeReg(SYSB, GPO, if (on) (r or gpio) else (r and gpio.inv()), 1)
        return true
    }

    override fun setOffsetTuning(on: Boolean): Boolean {
        // Not supported by R820T/R828D dongles (librtlsdr returns -2).
        return false
    }

    override fun readSync(buffer: ByteArray, offset: Int, length: Int): Int {
        val conn = connection ?: return -1
        val ep = epIn ?: return -1
        val n = conn.bulkTransfer(ep, buffer, offset, length, READ_TIMEOUT)
        // Detect USB buffer overflow / dropped bursts. SDR++ resets endpoint
        // in that case; do the same by re-initializing the buffer.
        if (n == length) {
            // check if every sample equal (stuck buffer)
            val first = buffer[offset].toInt() and 0xff
            var allEqual = true
            for (i in offset until offset + length) {
                if ((buffer[i].toInt() and 0xff) != first) {
                    allEqual = false
                    break
                }
            }
            if (allEqual) {
                // reset endpoint
                try {
                    writeReg(USBB, USB_EPA_CTL, 0x1002, 2)
                    Thread.sleep(5)
                    writeReg(USBB, USB_EPA_CTL, 0x0000, 2)
                    Thread.sleep(5)
                } catch (_: Exception) { }
            }
        }
        return n
    }

    private inline fun <T> withI2cRepeaterResult(block: () -> T): T {
        setI2cRepeater(true)
        try {
            return block()
        } finally {
            setI2cRepeater(false)
        }
    }
}

private const val TAG = "RtlSdrDevice"
private const val READ_TIMEOUT = 1000
