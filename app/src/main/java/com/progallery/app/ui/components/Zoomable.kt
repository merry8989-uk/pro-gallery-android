package com.progallery.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Zoom/pan state for a media surface.
 *
 * A single instance is shared by the screen so the gesture handlers always read
 * current values — a plain captured `Float` goes stale inside a long-lived
 * `pointerInput` block.
 */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    val isZoomed: Boolean get() = scale > 1f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun apply(zoomChange: Float, panChange: Offset) {
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = newScale
        offset = if (newScale <= 1f) Offset.Zero else offset + panChange
    }
}

/**
 * Two-finger pinch always zooms; one-finger drag pans only while zoomed.
 * At natural size the gesture is left unconsumed so a pager can still page.
 */
fun Modifier.zoomGestures(zoom: ZoomState): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val handle = pressedCount > 1 || zoom.isZoomed
            if (handle && (zoomChange != 1f || panChange != Offset.Zero)) {
                zoom.apply(zoomChange, panChange)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Compares a media URI with a navigation argument, tolerating double-encoding. */
fun sameUri(itemUri: String, passed: String): Boolean {
    if (itemUri == passed) return true
    val decoded = runCatching { java.net.URLDecoder.decode(passed, "UTF-8") }.getOrNull()
    return decoded != null && itemUri == decoded
}
