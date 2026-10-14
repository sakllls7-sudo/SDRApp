package com.sdrapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay

internal fun formatSpan(hz: Double): String {
    val a = kotlin.math.abs(hz)
    return when {
        a >= 1_000_000 -> String.format(Locale.US, "%.1fM", hz / 1_000_000.0)
        a >= 1_000 -> String.format(Locale.US, "%.0fk", hz / 1_000.0)
        else -> String.format(Locale.US, "%.0f", hz)
    }
}

/** Absolute frequency label for the spectrum axis. */
internal fun formatAxis(hz: Double): String {
    val a = kotlin.math.abs(hz)
    return when {
        a >= 1_000_000_000 -> String.format(Locale.US, "%.4f GHz", hz / 1_000_000_000.0)
        a >= 1_000_000 -> String.format(Locale.US, "%.3f MHz", hz / 1_000_000.0)
        a >= 1_000 -> String.format(Locale.US, "%.2f kHz", hz / 1_000.0)
        else -> String.format(Locale.US, "%.0f Hz", hz)
    }
}

@Composable
fun SpectrumView(
    frequency: Double,
    isPlaying: Boolean,
    spanHz: Double,
    sampleRate: Int = 2_400_000,
    fftRate: Int = 20,
    palette: String = "classic",
    dbMin: Float = -120f,
    dbMax: Float = -20f,
    spectrum: () -> FloatArray? = { null },
    onTune: (Double) -> Unit,
    onZoom: (Float) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var tick by remember { mutableIntStateOf(0) }

    val spanState = rememberUpdatedState(spanHz)
    val tuneState = rememberUpdatedState(onTune)
    val zoomState = rememberUpdatedState(onZoom)
    val spectrumState = rememberUpdatedState(spectrum)

    val frameDelay = (1000 / fftRate.coerceIn(1, 60)).coerceAtLeast(16).toLong()

    LaunchedEffect(isPlaying, frameDelay) {
        if (isPlaying) {
            while (true) {
                tick++
                delay(frameDelay)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .padding(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .tunePanZoom(
                    span = { spanState.value },
                    onTune = { tuneState.value(it) },
                    onZoom = { zoomState.value(it) }
                )
        ) {
            if (tick < 0) return@Canvas
            val w = size.width
            val h = size.height
            val centerX = w / 2f

            drawRect(Color.Black)

            val gridColor = Color(0xFF1A3322)
            for (i in 0..10) {
                val x = w * i / 10f
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            for (i in 0..6) {
                val y = h * i / 6f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val lineColor = Color(paletteColor(palette, 0.8f))
            val labelStyle = TextStyle(color = Color.White, fontSize = 10.sp)

            val bins = spectrumState.value()
            val spanDb = (dbMax - dbMin).let { if (it < 1f) 1f else it }

            if (bins != null && bins.size > 1) {
                val n = bins.size
                val rate = sampleRate.toDouble().coerceAtLeast(1.0)
                val f0 = frequency - spanHz / 2.0
                val hzPerPx = spanHz / w
                val step = 2
                val count = w.toInt() / step + 2
                val ys = FloatArray(count)

                fun binAt(freq: Double): Float {
                    val idxF = ((freq - frequency) / rate + 0.5) * n
                    val idx = idxF.toInt().coerceIn(0, n - 1)
                    return bins[idx]
                }

                for (i in 0 until count) {
                    val px = i * step
                    val fA = f0 + px * hzPerPx
                    val fB = fA + step * hzPerPx
                    // max over the bins covered by this pixel column
                    val iA = (((fA - frequency) / rate + 0.5) * n).toInt()
                    val iB = (((fB - frequency) / rate + 0.5) * n).toInt()
                    var db = binAt(fA)
                    if (iB > iA && iB - iA < 64) {
                        for (b in (iA + 1)..iB) {
                            if (b in 0 until n && bins[b] > db) db = bins[b]
                        }
                    }
                    val y = (((dbMax - db) / spanDb) * h).coerceIn(0f, h)
                    ys[i] = y
                }

                val spectrumPath = Path().apply {
                    moveTo(0f, ys[0])
                    for (i in 1 until count) lineTo((i * step).toFloat(), ys[i])
                }

                val fillPath = Path().apply {
                    addPath(spectrumPath)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }

                drawPath(
                    fillPath,
                    Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.3f),
                            lineColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h
                    )
                )

                drawPath(spectrumPath, lineColor, style = Stroke(width = 2f))
            } else {
                drawText(
                    textMeasurer,
                        "Нет спектра — включите воспроизведение",
                    topLeft = Offset(w * 0.30f, h * 0.45f),
                    style = TextStyle(color = Color(0xFF668866), fontSize = 13.sp)
                )
            }

            drawLine(
                Color.White.copy(alpha = 0.7f),
                Offset(centerX, 0f),
                Offset(centerX, h),
                strokeWidth = 2f
            )

            val leftHz = frequency - spanHz / 2.0
            val rightHz = frequency + spanHz / 2.0
            drawText(textMeasurer, formatAxis(leftHz), topLeft = Offset(4f, 4f), style = labelStyle)
            drawText(textMeasurer, formatAxis(frequency), topLeft = Offset(centerX - 34f, 4f), style = labelStyle)
            drawText(textMeasurer, formatAxis(rightHz), topLeft = Offset(w - 74f, 4f), style = labelStyle)
        }
    }
}
