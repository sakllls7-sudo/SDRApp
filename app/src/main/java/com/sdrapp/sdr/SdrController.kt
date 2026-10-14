package com.sdrapp.sdr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SdrState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val deviceName: String = "No device",
    val frequency: Double = 100_000_000.0,
    val demodType: DemodType = DemodType.WFM,
    val gainPercent: Float = 50f,
    val bandwidthHz: Float = 12_500f,
    val sampleRate: Int = 2_400_000,
    val squelchDb: Float = -70f,
    val agcEnabled: Boolean = true,
    val signalDbm: Float = -120f,
    val recording: Boolean = false,
    val status: String = "Idle",

    // Hardware
    val ppmCorrection: Int = 0,
    val rtlAgc: Boolean = false,
    val directSampling: Int = 0,
    val biasTee: Boolean = false,
    val offsetTuning: Boolean = false,

    // Demodulation
    val deemphasisUs: Int = 75,
    val noiseReduction: Boolean = false,
    val squelchEnabled: Boolean = false,

    // Audio
    val audioVolume: Float = 80f,

    // Display / FFT
    val fftSize: Int = 8192,
    val fftWindow: String = "blackman",
    val fftRate: Int = 20,
    val dbMin: Float = -120f,
    val dbMax: Float = -20f,
    val averaging: Int = 25,
    val waterfallPalette: String = "classic",
    val splitRatio: Float = 50f,
    val waterfallSpeed: Int = 60,

    // Tuning
    val snapToStep: Boolean = false,
    val stepHz: Int = 25_000
)

/**
 * Owns the SDR device, the demodulation/audio pipeline and background
 * reader thread. UI observes [state].
 */
class SdrController(
    private val context: Context,
    private val settingsRepo: SettingsRepository
) {
    private val _state = MutableStateFlow(SdrState())
    val state: StateFlow<SdrState> = _state.asStateFlow()

    private var device: SdrDevice? = null
    private val audio = AudioEngine()
    private val recorder = IqRecorder(context)

    /** Publishes a real FFT magnitude spectrum for the spectrum/waterfall views. */
    val analyzer = SpectrumAnalyzer()

    @Volatile private var demod: Demodulator? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        val s = settingsRepo.load()
        _state.value = _state.value.copy(
            frequency = s.frequency,
            demodType = s.demodType,
            gainPercent = s.gainPercent,
            bandwidthHz = s.bandwidthHz,
            sampleRate = s.sampleRate,
            squelchDb = s.squelchDb,
            agcEnabled = s.agcEnabled,
            ppmCorrection = s.ppmCorrection,
            rtlAgc = s.rtlAgc,
            directSampling = s.directSampling,
            biasTee = s.biasTee,
            offsetTuning = s.offsetTuning,
            deemphasisUs = s.deemphasisUs,
            noiseReduction = s.noiseReduction,
            squelchEnabled = s.squelchEnabled,
            audioVolume = s.audioVolume,
            fftSize = s.fftSize,
            fftWindow = s.fftWindow,
            fftRate = s.fftRate,
            dbMin = s.dbMin,
            dbMax = s.dbMax,
            averaging = s.averaging,
            waterfallPalette = s.waterfallPalette,
            splitRatio = s.splitRatio,
            waterfallSpeed = s.waterfallSpeed,
            snapToStep = s.snapToStep,
            stepHz = s.stepHz
        )
        audio.setVolume(s.audioVolume)
    }

    /** Attach (and open) a device. Pass null to detach. */
    fun openDevice(dev: SdrDevice): Boolean {
        closeDevice()
        val ok = dev.open()
        if (!ok) {
            _state.value = _state.value.copy(status = "Failed to open device")
            return false
        }
        device = dev
        _state.value = _state.value.copy(
            connected = true,
            deviceName = dev.name,
            status = "Connected"
        )
        applyAll()
        return true
    }

    fun closeDevice() {
        stopPlayback()
        device?.close()
        device = null
        _state.value = _state.value.copy(
            connected = false,
            deviceName = "No device",
            status = "Disconnected"
        )
    }

    private fun applyAll() {
        val dev = device ?: return
        val st = _state.value
        dev.setSampleRate(st.sampleRate)
        dev.setPpmCorrection(st.ppmCorrection)
        dev.setRtlAgc(st.rtlAgc)
        dev.setBiasTee(st.biasTee)
        dev.setDirectSampling(st.directSampling)
        dev.setCenterFrequency(st.frequency.toLong())
        dev.setGain(gainTenthDb(st))
        analyzer.configure(st.fftSize, st.fftWindow, st.sampleRate, st.frequency)
    }

    private fun gainTenthDb(st: SdrState): Int {
        if (st.agcEnabled) return -1
        val gains = SdrDevice.GAINS_TENTH_DB
        val idx = ((st.gainPercent / 100f) * (gains.size - 1)).roundToInt()
            .coerceIn(0, gains.size - 1)
        return gains[idx]
    }

    private fun rebuildDemod() {
        val st = _state.value
        demod = Demodulator(
            st.demodType,
            st.sampleRate,
            st.bandwidthHz.toDouble(),
            squelchEnabled = st.squelchEnabled,
            squelchDb = st.squelchDb,
            deemphasisUs = st.deemphasisUs,
            noiseReduction = st.noiseReduction
        )
    }

    fun startPlayback() {
        val dev = device ?: return
        if (running) return
        rebuildDemod()
        val st0 = _state.value
        analyzer.configure(st0.fftSize, st0.fftWindow, st0.sampleRate, st0.frequency)
        analyzer.reset()
        audio.start()
        running = true
        _state.value = _state.value.copy(playing = true, status = "Playing")

        thread = Thread({ readerLoop(dev) }, "SdrReader").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    fun stopPlayback() {
        running = false
        thread?.let {
            try {
                it.join(1000)
            } catch (_: InterruptedException) {
            }
        }
        thread = null
        audio.stop()
        if (recorder.isRecording) stopRecording()
        _state.value = _state.value.copy(playing = false, status = "Stopped")
    }

    private fun readerLoop(dev: SdrDevice) {
        val buf = ByteArray(1 shl 18) // 256 KiB
        val audioOut = FloatArray(1 shl 15)
        var lastLevelUpdate = 0L
        var lastFft = 0L
        while (running) {
            val n = dev.readSync(buf, 0, buf.size)
            if (n <= 0) {
                if (!running) break
                continue
            }
            if (recorder.isRecording) recorder.write(buf, n)

            val st = _state.value

            val d = demod
            if (d != null) {
                val count = d.process(buf, n, audioOut)
                audio.write(audioOut, count)
            }

            val now = System.currentTimeMillis()
            val fftInterval = (1000L / st.fftRate.coerceIn(1, 60)).coerceAtLeast(15L)
            if (now - lastFft >= fftInterval) {
                lastFft = now
                analyzer.process(buf, n, st.averaging)
            }

            if (now - lastLevelUpdate >= 200) {
                lastLevelUpdate = now
                val dbm = computeLevel(buf, n)
                _state.value = _state.value.copy(signalDbm = dbm)
            }
        }
    }

    private fun computeLevel(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        var count = 0
        var idx = 0
        while (idx + 1 < n) {
            val i = ((buf[idx].toInt() and 0xff) - 127.5) / 127.5
            val q = ((buf[idx + 1].toInt() and 0xff) - 127.5) / 127.5
            sum += i * i + q * q
            count++
            idx += 32 // subsample
        }
        if (count == 0) return _state.value.signalDbm
        val rms = sqrt(sum / count)
        // map 0..1 amplitude to roughly -110..0 dBFS
        val db = if (rms <= 1e-6) -120f else (20.0 * kotlin.math.log10(rms)).toFloat()
        return db.coerceIn(-120f, 0f)
    }

    // ----- setters --------------------------------------------------------

    fun setFrequency(freq: Double) {
        _state.value = _state.value.copy(frequency = freq)
        device?.setCenterFrequency(freq.toLong())
        analyzer.setCenter(freq)
        persist()
    }

    fun setDemodType(type: DemodType) {
        _state.value = _state.value.copy(demodType = type)
        if (running) rebuildDemod()
        persist()
    }

    fun setGainPercent(percent: Float) {
        _state.value = _state.value.copy(gainPercent = percent)
        device?.let { it.setGain(gainTenthDb(_state.value)) }
        persist()
    }

    fun setBandwidth(bw: Float) {
        _state.value = _state.value.copy(bandwidthHz = bw)
        if (running) rebuildDemod()
        persist()
    }

    fun setSampleRate(rate: Int) {
        _state.value = _state.value.copy(sampleRate = rate)
        device?.setSampleRate(rate)
        analyzer.configure(_state.value.fftSize, _state.value.fftWindow, rate, _state.value.frequency)
        if (running) rebuildDemod()
        persist()
    }

    fun setAgc(enabled: Boolean) {
        _state.value = _state.value.copy(agcEnabled = enabled)
        device?.setGain(gainTenthDb(_state.value))
        persist()
    }

    fun setSquelch(enabled: Boolean, db: Float) {
        _state.value = _state.value.copy(squelchEnabled = enabled, squelchDb = db)
        if (running) rebuildDemod()
        persist()
    }

    fun setPpmCorrection(ppm: Int) {
        _state.value = _state.value.copy(ppmCorrection = ppm)
        device?.setPpmCorrection(ppm)
        persist()
    }

    fun setRtlAgc(on: Boolean) {
        _state.value = _state.value.copy(rtlAgc = on)
        device?.setRtlAgc(on)
        persist()
    }

    fun setDirectSampling(mode: Int) {
        _state.value = _state.value.copy(directSampling = mode)
        device?.setDirectSampling(mode)
        persist()
    }

    fun setBiasTee(on: Boolean) {
        _state.value = _state.value.copy(biasTee = on)
        device?.setBiasTee(on)
        persist()
    }

    fun setOffsetTuning(on: Boolean) {
        _state.value = _state.value.copy(offsetTuning = on)
        device?.setOffsetTuning(on)
        persist()
    }

    fun setDeemphasis(us: Int) {
        _state.value = _state.value.copy(deemphasisUs = us)
        if (running) rebuildDemod()
        persist()
    }

    fun setNoiseReduction(on: Boolean) {
        _state.value = _state.value.copy(noiseReduction = on)
        if (running) rebuildDemod()
        persist()
    }

    fun setAudioVolume(percent: Float) {
        _state.value = _state.value.copy(audioVolume = percent)
        audio.setVolume(percent)
        persist()
    }

    fun setDisplay(
        fftSize: Int = _state.value.fftSize,
        fftWindow: String = _state.value.fftWindow,
        fftRate: Int = _state.value.fftRate,
        dbMin: Float = _state.value.dbMin,
        dbMax: Float = _state.value.dbMax,
        averaging: Int = _state.value.averaging,
        waterfallPalette: String = _state.value.waterfallPalette,
        splitRatio: Float = _state.value.splitRatio,
        waterfallSpeed: Int = _state.value.waterfallSpeed
    ) {
        _state.value = _state.value.copy(
            fftSize = fftSize,
            fftWindow = fftWindow,
            fftRate = fftRate,
            dbMin = dbMin,
            dbMax = dbMax,
            averaging = averaging,
            waterfallPalette = waterfallPalette,
            splitRatio = splitRatio,
            waterfallSpeed = waterfallSpeed
        )
        analyzer.configure(fftSize, fftWindow, _state.value.sampleRate, _state.value.frequency)
        persist()
    }

    fun setTuning(snapToStep: Boolean, stepHz: Int) {
        _state.value = _state.value.copy(snapToStep = snapToStep, stepHz = stepHz)
        persist()
    }

    // ----- recording ------------------------------------------------------

    fun toggleRecording(): Boolean {
        return if (recorder.isRecording) {
            stopRecording()
            false
        } else {
            startRecording()
            true
        }
    }

    private fun startRecording() {
        val st = _state.value
        val f = recorder.start(st.frequency.toLong(), st.sampleRate)
        _state.value = _state.value.copy(recording = true, status = "Recording: ${f?.name ?: ""}")
    }

    private fun stopRecording() {
        recorder.stop()
        _state.value = _state.value.copy(recording = false, status = "Recording saved")
    }

    // ----- settings -------------------------------------------------------

    fun persist() {
        val st = _state.value
        settingsRepo.save(
            AppSettings(
                frequency = st.frequency,
                demodType = st.demodType,
                gainPercent = st.gainPercent,
                bandwidthHz = st.bandwidthHz,
                sampleRate = st.sampleRate,
                squelchDb = st.squelchDb,
                agcEnabled = st.agcEnabled,
                ppmCorrection = st.ppmCorrection,
                rtlAgc = st.rtlAgc,
                directSampling = st.directSampling,
                biasTee = st.biasTee,
                offsetTuning = st.offsetTuning,
                deemphasisUs = st.deemphasisUs,
                noiseReduction = st.noiseReduction,
                squelchEnabled = st.squelchEnabled,
                audioVolume = st.audioVolume,
                fftSize = st.fftSize,
                fftWindow = st.fftWindow,
                fftRate = st.fftRate,
                dbMin = st.dbMin,
                dbMax = st.dbMax,
                averaging = st.averaging,
                waterfallPalette = st.waterfallPalette,
                splitRatio = st.splitRatio,
                waterfallSpeed = st.waterfallSpeed,
                snapToStep = st.snapToStep,
                stepHz = st.stepHz
            )
        )
    }

    fun resetSettings() {
        settingsRepo.clear()
        val d = AppSettings()
        _state.value = _state.value.copy(
            frequency = d.frequency,
            demodType = d.demodType,
            gainPercent = d.gainPercent,
            bandwidthHz = d.bandwidthHz,
            sampleRate = d.sampleRate,
            squelchDb = d.squelchDb,
            agcEnabled = d.agcEnabled,
            ppmCorrection = d.ppmCorrection,
            rtlAgc = d.rtlAgc,
            directSampling = d.directSampling,
            biasTee = d.biasTee,
            offsetTuning = d.offsetTuning,
            deemphasisUs = d.deemphasisUs,
            noiseReduction = d.noiseReduction,
            squelchEnabled = d.squelchEnabled,
            audioVolume = d.audioVolume,
            fftSize = d.fftSize,
            fftWindow = d.fftWindow,
            fftRate = d.fftRate,
            dbMin = d.dbMin,
            dbMax = d.dbMax,
            averaging = d.averaging,
            waterfallPalette = d.waterfallPalette,
            splitRatio = d.splitRatio,
            waterfallSpeed = d.waterfallSpeed,
            snapToStep = d.snapToStep,
            stepHz = d.stepHz
        )
        audio.setVolume(d.audioVolume)
        applyAll()
    }

    fun release() {
        stopPlayback()
        closeDevice()
    }
}
