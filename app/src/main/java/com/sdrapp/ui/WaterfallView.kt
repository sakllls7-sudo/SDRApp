package com.sdrapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import kotlinx.coroutines.delay

@Composable
fun WaterfallView(
    frequency: Double,
    isPlaying: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var frameCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                scrollOffset += 1f
                frameCount++
                delay(30)
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(Color.Black)

            val gridColor = Color(0xFF0A1A10)
            for (i in 0..10) {
                val x = w * i / 10f
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            for (i in 0..4) {
                val y = h * i / 4f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val signals = listOf(
                Triple(-0.15f, 0.02f, 0.8f),
                Triple(0.08f, 0.01f, 1.0f),
                Triple(0.25f, 0.03f, 0.6f)
            )

            val rowHeight = 1f
            for (row in 0 until h.toInt()) {
                val timeOffset = (scrollOffset + row) * 0.01f
                for (px in 0..w.toInt() step 3) {
                    val freq = px / w

                    var intensity = 0.01f + (Math.random() * 0.01f).toFloat()
                    for ((sigFreq, sigBW, sigAmp) in signals) {
                        val d = (freq - 0.5f - sigFreq) / sigBW
                        val wobble = sin(timeOffset * 2 + sigFreq * 10).toFloat() * 0.002f
                        intensity += sigAmp * 0.4f * exp(-d * d * 3)
                    }

                    val finalIntensity = intensity.coerceIn(0f, 1f)
                    val color = when {
                        finalIntensity < 0.05f -> Color(0xFF000010)
                        finalIntensity < 0.15f -> Color(0xFF001030)
                        finalIntensity < 0.3f -> Color(0xFF0044AA)
                        finalIntensity < 0.5f -> Color(0xFF00AA44)
                        finalIntensity < 0.7f -> Color(0xFFFFAA00)
                        else -> Color(0xFFFF4400)
                    }

                    drawRect(
                        color,
                        topLeft = Offset(px.toFloat(), row.toFloat()),
                        size = Size(3f, rowHeight)
                    )
                }
            }

            val centerX = w / 2f + 0.08f * w
            drawLine(
                Color.White.copy(alpha = 0.6f),
                Offset(centerX, 0f),
                Offset(centerX, h),
                strokeWidth = 2f
            )

            val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            drawText(
                textMeasurer,
                "Time →",
                topLeft = Offset(4f, 4f),
                style = labelStyle
            )
        }
    }
}
