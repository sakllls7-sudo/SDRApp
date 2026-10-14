package com.sdrapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlin.math.*
import kotlinx.coroutines.delay

@Composable
fun SpectrumView(
    frequency: Double,
    isPlaying: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                phase += 0.1f
                delay(50)
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
                    detectTapGestures { offset ->
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val span = 10_000_000f

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

            val spectrumPoints = mutableListOf<Offset>()
            val noisePoints = mutableListOf<Offset>()

            val signals = listOf(
                Triple(-0.2f, 0.03f, 0.7f),
                Triple(0.1f, 0.015f, 0.9f),
                Triple(0.35f, 0.04f, 0.5f),
                Triple(-0.5f, 0.02f, 0.4f)
            )

            for (px in 0..w.toInt() step 2) {
                val freq = (px / w - 0.5f) * span

                var power = -80f + (Math.random() * 8 - 4).toFloat()
                for ((sigFreq, sigBW, sigAmp) in signals) {
                    val d = (freq - sigFreq * span) / (sigBW * span)
                    power += sigAmp * 30f * exp(-d * d * 4)
                }

                power += 3f * sin(phase + px * 0.02f).toFloat()

                val y = ((power + 80f) / 80f * h).coerceIn(0f, h)

                spectrumPoints.add(Offset(px.toFloat(), y))
                noisePoints.add(Offset(px.toFloat(), ((power + 80f) / 80f * h).coerceIn(0f, h)))
            }

            val spectrumPath = Path().apply {
                if (spectrumPoints.isNotEmpty()) {
                    moveTo(spectrumPoints[0].x, spectrumPoints[0].y)
                    spectrumPoints.drop(1).forEach { lineTo(it.x, it.y) }
                }
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
            drawText(
                textMeasurer,
                "0 Hz",
                topLeft = Offset(4f, 4f),
                style = labelStyle
            )
            drawText(
                textMeasurer,
                "-5M",
                topLeft = Offset(w * 0.25f, 4f),
                style = labelStyle
            )
            drawText(
                textMeasurer,
                "+5M",
                topLeft = Offset(w * 0.75f - 10f, 4f),
                style = labelStyle
            )
        }
    }
}
