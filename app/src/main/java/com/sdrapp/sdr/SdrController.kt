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
    val status: String = "Idle"
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
            agcEnabled = s.agcEnabled
        )
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
        dev.setCenterFrequency(st.frequency.toLong())
        dev.setGain(gainTenthDb(st))
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
        demod = Demodulator(st.demodType, st.sampleRate, st.bandwidthHz.toDouble())
    }

    fun startPlayback() {
        val dev = device ?: return
        if (running) return
        rebuildDemod()
        audio.start()
        running = true
        _state.value = _state.value.copy(playing = true, status = "Playing")

        thread = Thread({ readerLoop(dev) }, "SdrReader").apply {
            priority = Thread.MAX_PRIORITY
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
        while (running) {
            val n = dev.readSync(buf, 0, buf.size)
            if (n <= 0) {
                if (!running) break
                continue
            }
            if (recorder.isRecording) recorder.write(buf, n)

            val d = demod ?: continue
            val count = d.process(buf, n, audioOut)
            audio.write(audioOut, count)

            val dbm = computeLevel(buf, n)
            _state.value = _state.value.copy(signalDbm = dbm)
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
        if (running) rebuildDemod()
        persist()
    }

    fun setAgc(enabled: Boolean) {
        _state.value = _state.value.copy(agcEnabled = enabled)
        device?.setGain(gainTenthDb(_state.value))
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
                agcEnabled = st.agcEnabled
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
            agcEnabled = d.agcEnabled
        )
        applyAll()
    }

    fun release() {
        stopPlayback()
        closeDevice()
    }
}
