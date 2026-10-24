package com.progallery.app.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Reaches [MediaActions] from a composable, which cannot take a constructor parameter.
 *
 * The navigation host needs it for the one case `FileOpener` hands a file over: a file nothing here
 * can open has to be passed to another app, and the host is where the tap arrives.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaActionsEntryPoint {
    fun mediaActions(): MediaActions

    companion object {
        fun from(context: Context): MediaActions =
            EntryPointAccessors.fromApplication(context, MediaActionsEntryPoint::class.java).mediaActions()
    }
}

@Composable
fun rememberMediaActions(): MediaActions {
    val context = LocalContext.current
    return remember(context) { MediaActionsEntryPoint.from(context) }
}
