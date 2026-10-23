package com.progallery.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's one way of tapping back.
 *
 * Haptics are **off by default** and nothing vibrates until they are turned on, so every call site
 * goes through this rather than reaching for the platform itself — otherwise a new screen would
 * buzz regardless of the setting, and nobody would notice until a phone did it in a quiet room.
 *
 * [enabled] is passed in rather than read from here: a screen already has its settings-derived
 * state, and reaching for the store again inside a component would be a second source for one
 * boolean.
 */
class Haptics(private val enabled: Boolean, private val perform: (HapticFeedbackType) -> Unit) {
    fun tap() {
        if (enabled) perform(HapticFeedbackType.TextHandleMove)
    }

    fun longPress() {
        if (enabled) perform(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(enabled) { Haptics(enabled) { type -> feedback.performHapticFeedback(type) } }
}
