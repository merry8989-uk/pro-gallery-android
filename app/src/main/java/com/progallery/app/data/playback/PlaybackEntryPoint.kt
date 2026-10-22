package com.progallery.app.data.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the engine from a composable, which cannot take a constructor parameter.
 *
 * The alternative — building a player by hand in each screen — is exactly what this replaced.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackEntryPoint {
    fun playbackEngine(): PlaybackEngine

    companion object {
        fun from(context: Context): PlaybackEngine =
            EntryPointAccessors.fromApplication(context, PlaybackEntryPoint::class.java).playbackEngine()
    }
}

@Composable
fun rememberPlaybackEngine(): PlaybackEngine {
    val context = LocalContext.current
    return remember(context) { PlaybackEntryPoint.from(context) }
}
