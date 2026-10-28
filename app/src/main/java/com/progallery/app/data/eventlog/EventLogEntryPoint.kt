package com.progallery.app.data.eventlog

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the event log from a composable.
 *
 * A screen that has a view model should go through it — this exists for the places that are not a
 * screen: the navigation host, which sees every route and is therefore the only place that can log
 * a screen view once per screen.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface EventLogEntryPoint {
    fun eventLogStore(): EventLogStore

    /** Which screen is showing, for the crash handler to name. */
    fun currentScreen(): CurrentScreen

    companion object {
        fun from(context: Context): EventLogStore =
            EntryPointAccessors.fromApplication(context, EventLogEntryPoint::class.java)
                .eventLogStore()
    }
}

@Composable
fun rememberEventLog(): EventLogStore {
    val context = LocalContext.current
    return remember(context) { EventLogEntryPoint.from(context) }
}

/**
 * The screen the navigation host last showed.
 *
 * Read by the crash handler through the application, not through this — this is only how the host
 * writes it.
 */
@Composable
fun rememberCurrentScreen(): CurrentScreen {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(context, EventLogEntryPoint::class.java).currentScreen()
    }
}
