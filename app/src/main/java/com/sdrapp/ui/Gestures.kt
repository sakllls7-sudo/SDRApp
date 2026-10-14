package com.sdrapp.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Single-finger drag calls [onTune] with a frequency delta (Hz); two-finger
 * pinch calls [onZoom] with a scale factor. [span] supplies the current visible
 * span in Hz so the pixel delta can be converted to frequency.
 */
internal fun Modifier.tunePanZoom(
    span: () -> Double,
    onTune: (Double) -> Unit,
    onZoom: (Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pastSlop = false
        var accPan = Offset.Zero
        var accZoom = 1f
        var hadMulti = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.any { it.pressed }
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                if (event.changes.size > 1) hadMulti = true
                val panChange = event.calculatePan()
                val zoomChange = event.calculateZoom()
                if (!pastSlop) {
                    accPan += panChange
                    accZoom *= zoomChange
                    val zoomMotion = abs(1f - accZoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > viewConfiguration.touchSlop || accPan.getDistance() > viewConfiguration.touchSlop) {
                        pastSlop = true
                    }
                }
                if (pastSlop) {
                    if (hadMulti) {
                        if (zoomChange != 1f) onZoom(zoomChange)
                    } else if (panChange.x != 0f) {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val hzPerPx = span() / w
                        onTune(-panChange.x * hzPerPx)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
            if (!pressed) break
        }
    }
}
