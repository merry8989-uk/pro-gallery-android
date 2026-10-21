package com.progallery.app.data.usage

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * How the navigation layer reaches the usage store.
 *
 * An entry point rather than a constructor parameter: the nav host is a composable, not an
 * injected class, and threading a store through every screen would put it in the signature of
 * twenty functions that have no use for it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UsageEntryPoint {

    fun usageStore(): UsageStore

    companion object {
        fun from(context: Context): UsageStore =
            EntryPointAccessors.fromApplication(context, UsageEntryPoint::class.java).usageStore()
    }
}

/** The store for this composition, resolved once. */
@Composable
fun rememberUsageStore(): UsageStore {
    val context = LocalContext.current
    return remember(context) { UsageEntryPoint.from(context) }
}
