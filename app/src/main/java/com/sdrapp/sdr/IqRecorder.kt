package com.sdrapp.sdr

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records raw 8-bit unsigned IQ samples to a .iq file. A companion
 * .txt sidecar stores the center frequency and sample rate so the
 * capture can be replayed later.
 */
class IqRecorder(private val context: Context) {

    private var stream: BufferedOutputStream? = null
    private var file: File? = null
    private var bytesWritten = 0L

    val isRecording: Boolean get() = stream != null

    fun start(centerFreq: Long, sampleRate: Int): File? {
        if (stream != null) return file

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val base = "iq_${(centerFreq / 1000)}kHz_${sampleRate}_$stamp"
        val f = File(dir, "$base.iq")
        val out = BufferedOutputStream(FileOutputStream(f), 1 shl 16)

        try {
            File(dir, "$base.txt").writeText(
                "center_freq=$centerFreq\nsample_rate=$sampleRate\nformat=cu8\n"
            )
        } catch (_: Exception) {
        }

        file = f
        stream = out
        bytesWritten = 0
        return f
    }

    fun write(buffer: ByteArray, length: Int) {
        val s = stream ?: return
        s.write(buffer, 0, length)
        bytesWritten += length
    }

    fun stop() {
        try {
            stream?.flush()
            stream?.close()
        } catch (_: Exception) {
        }
        stream = null
    }
}
