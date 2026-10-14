package com.sdrapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.delay

internal fun formatSpan(hz: Double): String {
    val a = kotlin.math.abs(hz)
    return when {
        a >= 1_000_000 -> String.format(Locale.US, "%.1fM", hz / 1_000_000.0)
        a >= 1_000 -> String.format(Locale.US, "%.0fk", hz / 1_000.0)
        else -> String.format(Locale.US, "%.0f", hz)
    }
}

@Composable
fun SpectrumView(
    frequency: Double,
    isPlaying: Boolean,
    spanHz: Double,
    onTune: (Double) -> Unit,
    onZoom: (Float) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var phase by remember { mutableFloatStateOf(0f) }

    val spanState = rememberUpdatedState(spanHz)
    val tuneState = rememberUpdatedState(onTune)
    val zoomState = rememberUpdatedState(onZoom)

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                phase += 0.1f
                delay(80)
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
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val hzPerPx = spanState.value / size.width
                        tuneState.value(-pan.x * hzPerPx)
                        zoomState.value(zoom)
                    }
                }
        ) {
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

            val signals = listOf(
                Triple(99_900_000.0, 60_000.0, 0.9),
                Triple(100_350_000.0, 120_000.0, 0.5),
                Triple(99_200_000.0, 40_000.0, 0.6),
                Triple(100_900_000.0, 80_000.0, 0.4)
            )

            val step = 4
            val count = w.toInt() / step + 2
            val ys = FloatArray(count)
            val f0 = frequency - spanHz / 2.0
            val hzPerPx = spanHz / w
            for (i in 0 until count) {
                val px = i * step
                val freq = f0 + px * hzPerPx

                var power = -80.0 + sin((px * 0.37f + phase) * 1.7f) * 4.0 + sin(px * 0.11f) * 3.0
                for ((sigFreq, sigBW, sigAmp) in signals) {
                    val d = (freq - sigFreq) / sigBW
                    power += sigAmp * 30.0 * exp(-d * d * 4.0)
                }

                val y = (((power + 80.0) / 80.0) * h).toFloat().coerceIn(0f, h)
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
                        SpectrumGreen.copy(alpha = 0.3f),
                        SpectrumGreen.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            drawPath(spectrumPath, SpectrumGreen, style = Stroke(width = 2f))

            val cursorX = centerX + 0.1f * w
            drawLine(
                Color.White.copy(alpha = 0.7f),
                Offset(cursorX, 0f),
                Offset(cursorX, h),
                strokeWidth = 2f
            )

            val cursorY = h * 0.3f
            drawLine(
                Color.Yellow.copy(alpha = 0.5f),
                Offset(0f, cursorY),
                Offset(w, cursorY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )

            val labelStyle = TextStyle(color = Color.White, fontSize = 10.sp)
            val quarterLabel = formatSpan(spanHz / 4.0)
            val centerLabel = formatSpan(spanHz)
            drawText(
                textMeasurer,
                "-$centerLabel",
                topLeft = Offset(4f, 4f),
                style = labelStyle
            )
            drawText(
                textMeasurer,
                "-$quarterLabel",
                topLeft = Offset(w * 0.25f, 4f),
                style = labelStyle
            )
            drawText(
                textMeasurer,
                "+$quarterLabel",
                topLeft = Offset(w * 0.75f - 24f, 4f),
                style = labelStyle
            )
            drawText(
                textMeasurer,
                "+$centerLabel",
                topLeft = Offset(w - 36f, 4f),
                style = labelStyle
            )
        }
    }
}
