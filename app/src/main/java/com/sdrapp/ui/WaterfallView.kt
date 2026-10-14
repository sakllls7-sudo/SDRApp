package com.sdrapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlinx.coroutines.delay

private class WaterfallState {
    var bitmap: Bitmap? = null
    var image: ImageBitmap? = null
    var rowIndex = 0
    var lastFrame = -1
}

@Composable
fun WaterfallView(
    frequency: Double,
    isPlaying: Boolean,
    spanHz: Double,
    onTune: (Double) -> Unit,
    onZoom: (Float) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val wf = remember { WaterfallState() }
    var frame by remember { mutableIntStateOf(0) }

    val spanState = rememberUpdatedState(spanHz)
    val tuneState = rememberUpdatedState(onTune)
    val zoomState = rememberUpdatedState(onZoom)

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                frame++
                delay(60)
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
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            val f = frame

            if (wf.bitmap == null || wf.bitmap!!.width != w || wf.bitmap!!.height != h) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.BLACK)
                wf.bitmap = bmp
                wf.image = bmp.asImageBitmap()
                wf.rowIndex = 0
                wf.lastFrame = -1
            }

            val bmp = wf.bitmap!!
            val image = wf.image!!

            if (isPlaying && f != wf.lastFrame) {
                wf.lastFrame = f
                wf.rowIndex = if (wf.rowIndex <= 0) h - 1 else wf.rowIndex - 1
                val row = IntArray(w)
                val f0 = frequency - spanHz / 2.0
                val hzPerPx = spanHz / w
                for (x in 0 until w) {
                    val freq = f0 + x * hzPerPx
                    var intensity = 0.015f + ((x * 7 + f * 13) % 5) * 0.004f
                    for (s in wfSignals) {
                        val d = (freq - s[0]) / s[1]
                        intensity += (s[2] * exp(-(d * d) * 3.0)).toFloat()
                    }
                    row[x] = intensityColor(intensity)
                }
                bmp.setPixels(row, 0, w, 0, wf.rowIndex, w, 1)
            }

            val r = wf.rowIndex
            val topH = h - r
            if (topH > 0) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(0, r),
                    srcSize = IntSize(w, topH),
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(w, topH)
                )
            }
            if (r > 0) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(w, r),
                    dstOffset = IntOffset(0, topH),
                    dstSize = IntSize(w, r)
                )
            }

            val centerX = w / 2f
            drawLine(
                Color.White.copy(alpha = 0.6f),
                Offset(centerX, 0f),
                Offset(centerX, h.toFloat()),
                strokeWidth = 2f
            )

            val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            drawText(
                textMeasurer,
                "Time ->",
                topLeft = Offset(4f, 4f),
                style = labelStyle
            )
        }
    }
}

// {centerHz, bandwidthHz, amplitude}
private val wfSignals = arrayOf(
    doubleArrayOf(99_900_000.0, 60_000.0, 0.85),
    doubleArrayOf(100_350_000.0, 120_000.0, 0.45),
    doubleArrayOf(99_200_000.0, 40_000.0, 0.55),
    doubleArrayOf(100_900_000.0, 80_000.0, 0.35)
)

private fun intensityColor(v: Float): Int {
    val i = v.coerceIn(0f, 1f)
    return when {
        i < 0.05f -> 0xFF000010.toInt()
        i < 0.15f -> 0xFF001030.toInt()
        i < 0.30f -> 0xFF0044AA.toInt()
        i < 0.50f -> 0xFF00AA44.toInt()
        i < 0.70f -> 0xFFFFAA00.toInt()
        else -> 0xFFFF4400.toInt()
    }
}
