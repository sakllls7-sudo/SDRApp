package com.sdrapp.sdr

/**
 * Kotlin port of the Rafael Micro R820T/R828D tuner driver
 * (tuner_r82xx.c from librtlsdr, GPL-2.0). Only the R820T/R828D
 * (RAFael) path is implemented, which is what virtually all
 * RTL2832U dongles use.
 */
class R820T(private val bus: I2cBus, private val chip: Chip = Chip.R820T) {

    enum class Chip(val i2cAddr: Int, val xtal: Long) {
        R820T(0x34, 28_800_000L),
        R828D(0x74, 16_000_000L)
    }

    private class FreqRange(
        val freq: Long,        // kHz
        val openD: Int,
        val rfMuxPloy: Int,
        val tfC: Int,
        val xtalCap20p: Int,
        val xtalCap10p: Int,
        val xtalCap0p: Int
    )

    private val i2cAddr = chip.i2cAddr
    private val xtal = chip.xtal

    private val regs = IntArray(NUM_REGS)
    private val buf = ByteArray(16)

    @Volatile var hasLock = false
        private set

    private var rfFreq: Long = 0
    private var ifBandCenterFreq = 0
    private var intFreq = 3_570_000
    private var sideband = 0

    private var lastVcoCurr = 0xff
    private var lastIfMode = 0
    private var lastManualGain = 0
    private var lastExtendedMode = 0
    private var lastLnaValue = 0
    private var lastMixerValue = 0
    private var lastVgaValue = DEFAULT_IF_VGA_VAL
    private var tunerPllSet = false
    private var tunerHarmonic = 0
    private var filCalCode = 0
    private var initDone = false

    // ----- low level register access -------------------------------------

    private fun writeArr(reg: Int, values: IntArray, len: Int) {
        for (i in 0 until len) {
            val r = reg + i
            if (r >= REG_SHADOW_START && r < NUM_REGS) {
                regs[r] = values[i] and 0xff
            }
        }
        val b = ByteArray(len + 1)
        b[0] = reg.toByte()
        for (i in 0 until len) b[i + 1] = values[i].toByte()
        bus.i2cWrite(i2cAddr, b, len + 1)
    }

    private fun writeReg(reg: Int, value: Int) = writeArr(reg, intArrayOf(value), 1)

    private fun readCacheReg(reg: Int): Int =
        if (reg in REG_SHADOW_START until NUM_REGS) regs[reg] else -1

    private fun writeRegMask(reg: Int, value: Int, mask: Int) {
        var v = value
        val current = readCacheReg(reg)
        if (current >= 0) {
            v = (current and mask.inv()) or (value and mask)
        }
        writeArr(reg, intArrayOf(v), 1)
    }

    private fun read(reg: Int, out: ByteArray, len: Int): Int {
        val rc = bus.i2cRead(i2cAddr, reg, out, len)
        for (i in 0 until len) {
            var b = out[i].toInt() and 0xff
            b = ((b and 0xf0) shr 4) or ((b and 0x0f) shl 4)
            b = ((b and 0xcc) shr 2) or ((b and 0x33) shl 2)
            b = ((b and 0xaa) shr 1) or ((b and 0x55) shl 1)
            out[i] = b.toByte()
        }
        return rc
    }

    // ----- mux / pll ------------------------------------------------------

    private fun setMux(freqIn: Long) {
        val freqMhz = freqIn / 1_000_000L
        var idx = 0
        for (i in 0 until FREQ_RANGES.size - 1) {
            if (freqMhz < FREQ_RANGES[i + 1].freq) break
            idx = i + 1
        }
        val range = FREQ_RANGES[idx]

        // Open Drain
        writeRegMask(0x17, range.openD, 0x08)
        // RF_MUX, Polymux
        writeRegMask(0x1a, range.rfMuxPloy, 0xc3)
        // TF BAND
        writeReg(0x1b, range.tfC)
        // XTAL CAP & Drive (xtal_cap_sel == XTAL_HIGH_CAP_0P)
        val capVal = range.xtalCap0p or 0x00
        writeRegMask(0x10, capVal, 0x0b)
    }

    private fun setPll(freqIn: Long): Int {
        val freq = freqIn
        val refdiv2 = 0
        val vcoMin = 1_770_000L
        val vcoMax = vcoMin * 2   // cfg.vco_algo == 0
        val vcoPowerRef = if (chip == Chip.R828D) 1 else 2

        val pllRef = xtal

        val freqKhz = (freq + 500) / 1000

        writeRegMask(0x10, refdiv2 shl 4, 0x10)
        writeRegMask(0x1a, 0x00, 0x0c)

        val vcoCurrMin = if (VCO_CURR_MIN == 0xff) 0x80 else (VCO_CURR_MIN shl 5)
        val vcoCurrMax = if (VCO_CURR_MAX == 0xff) 0x60 else (VCO_CURR_MAX shl 5)
        if (lastVcoCurr != vcoCurrMin) {
            writeRegMask(0x12, vcoCurrMin, 0xe0)
            lastVcoCurr = vcoCurrMin
        }

        var mixDiv = 2
        var divNum = 0
        var divBuf = 0
        while (mixDiv <= 64) {
            if (freqKhz * mixDiv >= vcoMin && freqKhz * mixDiv < vcoMax) {
                divBuf = mixDiv
                while (divBuf > 2) {
                    divBuf = divBuf shr 1
                    divNum++
                }
                break
            }
            mixDiv = mixDiv shl 1
        }
        if (mixDiv > 64) divNum = 0

        val data = ByteArray(5)
        read(0x00, data, data.size)

        val vcoFineTune = (data[4].toInt() and 0x30) shr 4
        if (vcoFineTune > vcoPowerRef) divNum--
        else if (vcoFineTune < vcoPowerRef) divNum++

        writeRegMask(0x10, divNum shl 5, 0xe0)

        val vcoFreq = freq * mixDiv
        val vcoDiv = (pllRef + 65536L * vcoFreq) / (2L * pllRef)
        val nint = (vcoDiv / 65536L).toInt()
        val sdm = (vcoDiv % 65536L).toInt()

        if (nint > ((128 / vcoPowerRef) - 1)) {
            return -1
        }

        val ni = (nint - 13) / 4
        val si = nint - 4 * ni - 13

        writeReg(0x14, ni + (si shl 6))
        var val0 = if (sdm == 0) 0x08 else 0x00
        if (DISABLE_DITHER) val0 = val0 or 0x10
        writeRegMask(0x12, val0, 0x18)
        writeReg(0x16, sdm shr 8)
        writeReg(0x15, sdm and 0xff)

        tunerPllSet = true

        var i = 0
        while (i < 2) {
            read(0x00, data, 3)
            if ((data[2].toInt() and 0x40) != 0 || vcoCurrMax == vcoCurrMin) break
            if (i == 0) {
                if (lastVcoCurr != vcoCurrMax) {
                    writeRegMask(0x12, vcoCurrMax, 0xe0)
                    lastVcoCurr = vcoCurrMax
                }
            }
            i++
        }

        if ((data[2].toInt() and 0x40) == 0) {
            hasLock = false
            return -1
        }

        hasLock = true
        writeRegMask(0x1a, 0x08, 0x08)
        return 0
    }

    // ----- standard / system frequency selection --------------------------

    private fun setTvStandard(type: Int) {
        val filtQ = 0x10

        if (type != TUNER_ANALOG_TV) {
            writeRegMask(0x1d, 0x00, 0x38)
        }

        rfFreq = 56_000_000L
        ifBandCenterFreq = 0
        intFreq = 3_570_000
        sideband = 0

        val data = ByteArray(5)
        var i = 0
        while (i < 2) {
            writeRegMask(0x0f, 0x04, 0x04)
            tunerPllSet = false
            if (setPll(rfFreq) < 0 || !hasLock) return
            writeRegMask(0x0b, 0x10, 0x10)
            writeRegMask(0x0b, 0x00, 0x10)
            writeRegMask(0x0f, 0x00, 0x04)
            read(0x00, data, data.size)
            filCalCode = data[4].toInt() and 0x0f
            if (filCalCode != 0 && filCalCode != 0x0f) break
            i++
        }
        if (filCalCode == 0x0f) filCalCode = 0

        writeRegMask(0x0a, (filtQ or filCalCode) and 0x1f, 0x1f)
    }

    private fun sysfreqSel(type: Int) {
        // use_predetect == 0, so the pre_dect write is skipped
        // Air-IN only for Astrometa (air_cable1_in == 0)
        writeRegMask(0x05, 0x00, 0x60)
        writeRegMask(0x06, 0x00, 0x08)

        if (type != TUNER_ANALOG_TV) {
            // LNA TOP: lowest
            writeRegMask(0x1d, 0x00, 0x38)
            // 0: PRE_DECT off
            writeRegMask(0x06, 0x00, 0x40)
            // agc clk 250hz
            writeRegMask(0x1a, 0x30, 0x30)
            // write LNA TOP = 3
            writeRegMask(0x1d, 0x18, 0x38)
            // agc clk 60hz
            writeRegMask(0x1a, 0x20, 0x30)
        } else {
            // PRE_DECT off
            writeRegMask(0x06, 0x00, 0x40)
            // write LNA TOP
            writeRegMask(0x1d, 0xe5, 0x38)
            // agc clk 1Khz, external det1 cap 1u
            writeRegMask(0x1a, 0x00, 0x30)
            writeRegMask(0x10, 0x00, 0x04)
        }
    }

    // ----- gain -----------------------------------------------------------

    private fun getRfGainIndex(gain: Int): IntArray {
        var totalGain = 0
        var mixIndex = 0
        var lnaIndex = 0
        for (i in 0 until 15) {
            if (totalGain >= gain) break
            lnaIndex++
            totalGain += LNA_GAIN_STEPS[lnaIndex]
            if (totalGain >= gain) break
            mixIndex++
            totalGain += MIXER_GAIN_STEPS[mixIndex]
        }
        return intArrayOf(lnaIndex, mixIndex)
    }

    private fun getIfGainIndex(gain: Int): Int {
        var totalGain = VGA_BASE_GAIN
        var vgaIndex = 0
        while (vgaIndex < 15) {
            if (totalGain >= gain) break
            vgaIndex++
            totalGain += VGA_GAIN_STEPS[vgaIndex]
        }
        return vgaIndex
    }

    private fun setIfMode(ifModeIn: Int): Int {
        var ifMode = ifModeIn
        var vgaGainIdx: Int
        if (ifMode == 0 || ifMode == 10016) {
            vgaGainIdx = 0x10
        } else if (ifMode in -2500..2500) {
            vgaGainIdx = getIfGainIndex(ifMode)
        } else if (ifMode in 2501 until 10000) {
            vgaGainIdx = getIfGainIndex(ifMode - 5000)
        } else if (ifMode in 10000..10016 + 15) {
            vgaGainIdx = ifMode - 10000
        } else {
            ifMode = 10000 + DEFAULT_IF_VGA_VAL
            vgaGainIdx = DEFAULT_IF_VGA_VAL
        }

        writeRegMask(0x0c, vgaGainIdx, 0x1f)
        lastIfMode = ifMode
        lastVgaValue = vgaGainIdx
        return if ((vgaGainIdx and 0x10) != 0) 1 else 0
    }

    /** Returns the RTL AGC (VGA control) flag: 1 == activate RF AGC loop in RTL2832U. */
    fun setGain(manual: Boolean, gain: Int): Int {
        var lnaIdx = lastLnaValue
        var mixIdx = lastMixerValue
        var newIfMode = lastIfMode

        if (manual) {
            val idx = getRfGainIndex(gain)
            lnaIdx = idx[0]
            mixIdx = idx[1]

            writeRegMask(0x05, 0x10, 0x10)
            writeRegMask(0x07, 0x00, 0x10)
            val data = ByteArray(4)
            read(0x00, data, data.size)
            writeRegMask(0x05, lnaIdx, 0x0f)
            writeRegMask(0x07, mixIdx, 0x0f)

            lastManualGain = 1
            lastExtendedMode = 0
            lastLnaValue = lnaIdx
            lastMixerValue = mixIdx
        } else {
            writeRegMask(0x05, 0x00, 0x10)
            writeRegMask(0x07, 0x10, 0x10)
            lastManualGain = 0
            lastExtendedMode = 0
            newIfMode = 10000 + DEFAULT_IF_VGA_VAL
        }

        return setIfMode(newIfMode)
    }

    // ----- frequency ------------------------------------------------------

    fun setFreq(freq: Long): Int {
        val upconvertFreq = freq
        val harm = DEFAULT_HARMONIC
        var nthHarm = if (freq > 1_770_000L * 1000) 1 else 0
        var rc = -1

        while (nthHarm < 2) {
            tunerPllSet = false
            tunerHarmonic = if (nthHarm != 0) harm else 0

            rfFreq = upconvertFreq
            val loFreq: Long
            if ((sideband xor HARM_SIDEBAND_XOR[tunerHarmonic]) != 0) {
                loFreq = upconvertFreq - intFreq + ifBandCenterFreq
            } else {
                loFreq = upconvertFreq + intFreq + ifBandCenterFreq
            }
            val loFreqHarm = if (nthHarm != 0) loFreq / harm else loFreq

            setMux(loFreq)
            rc = setPll(loFreqHarm)

            if (rc < 0 || !hasLock) {
                if (nthHarm == 0 && loFreq > 1_760_000L * 1000) {
                    nthHarm++
                    continue
                }
                hasLock = false
                return rc
            }
            break
        }
        return rc
    }

    fun init(): Int {
        rfFreq = 0
        ifBandCenterFreq = 0
        lastIfMode = 0
        lastManualGain = 0
        lastExtendedMode = 0
        lastLnaValue = 0
        lastMixerValue = 0
        lastVgaValue = DEFAULT_IF_VGA_VAL
        lastVcoCurr = 0xff

        writeArr(0x05, INIT_ARRAY, INIT_ARRAY.size)
        lastVcoCurr = INIT_ARRAY[0x12 - 0x05] and 0xe0

        setTvStandard(TUNER_DIGITAL_TV)
        sysfreqSel(TUNER_DIGITAL_TV)

        initDone = true
        return 0
    }

    companion object {
        private const val REG_SHADOW_START = 5
        private const val NUM_REGS = 32
        private const val DEFAULT_IF_VGA_VAL = 11
        private const val DEFAULT_HARMONIC = 5
        private const val VER_NUM = 49
        private const val XTAL_CAP20P = 2
        private const val VCO_CURR_MIN = 0xff
        private const val VCO_CURR_MAX = 0xff
        private const val VCO_POWER_REF = 2
        private const val DISABLE_DITHER = 0
        private const val VGA_BASE_GAIN = -47

        private const val TUNER_ANALOG_TV = 0
        private const val TUNER_DIGITAL_TV = 1

        private val INIT_ARRAY = intArrayOf(
            0x80, 0x13, 0x70, 0xc0, 0x40, 0xdb, 0x6b,
            0xe0 or DEFAULT_IF_VGA_VAL,
            0x53, 0x75, 0x68, 0x6c, 0xbb, 0x80, VER_NUM and 0x3f, 0x0f,
            0x00, 0xc0, 0x30, 0x48, 0xec, 0x60, 0x00, 0x24,
            0xdd, 0x0e, 0x40
        )

        private val HARM_SIDEBAND_XOR = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0
        )

        private val LNA_GAIN_STEPS = intArrayOf(0, 9, 13, 40, 38, 13, 31, 22, 26, 31, 26, 14, 19, 5, 35, 13)
        private val MIXER_GAIN_STEPS = intArrayOf(0, 5, 10, 10, 19, 9, 10, 25, 17, 10, 8, 16, 13, 6, 3, -8)
        private val VGA_GAIN_STEPS = intArrayOf(0, 26, 26, 30, 42, 35, 24, 13, 14, 32, 36, 34, 35, 37, 35, 36)

        private val FREQ_RANGES = arrayOf(
            FreqRange(0, 0x08, 0x02, 0xdf, 0x02, 0x01, 0x00),
            FreqRange(50, 0x08, 0x02, 0xbe, 0x02, 0x01, 0x00),
            FreqRange(55, 0x08, 0x02, 0x8b, 0x02, 0x01, 0x00),
            FreqRange(60, 0x08, 0x02, 0x7b, 0x02, 0x01, 0x00),
            FreqRange(65, 0x08, 0x02, 0x69, 0x02, 0x01, 0x00),
            FreqRange(70, 0x08, 0x02, 0x58, 0x02, 0x01, 0x00),
            FreqRange(75, 0x00, 0x02, 0x44, 0x02, 0x01, 0x00),
            FreqRange(80, 0x00, 0x02, 0x44, 0x02, 0x01, 0x00),
            FreqRange(90, 0x00, 0x02, 0x34, 0x01, 0x01, 0x00),
            FreqRange(100, 0x00, 0x02, 0x34, 0x01, 0x01, 0x00),
            FreqRange(110, 0x00, 0x02, 0x24, 0x01, 0x01, 0x00),
            FreqRange(120, 0x00, 0x02, 0x24, 0x01, 0x01, 0x00),
            FreqRange(140, 0x00, 0x02, 0x14, 0x01, 0x01, 0x00),
            FreqRange(180, 0x00, 0x02, 0x13, 0x00, 0x00, 0x00),
            FreqRange(220, 0x00, 0x02, 0x13, 0x00, 0x00, 0x00),
            FreqRange(250, 0x00, 0x02, 0x11, 0x00, 0x00, 0x00),
            FreqRange(280, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00),
            FreqRange(310, 0x00, 0x41, 0x00, 0x00, 0x00, 0x00),
            FreqRange(450, 0x00, 0x41, 0x00, 0x00, 0x00, 0x00),
            FreqRange(588, 0x00, 0x40, 0x00, 0x00, 0x00, 0x00),
            FreqRange(650, 0x00, 0x40, 0x00, 0x00, 0x00, 0x00)
        )
    }
}
