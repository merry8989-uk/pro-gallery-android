package com.progallery.app.data.memories

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.progallery.app.data.eventlog.EventLogStore

/**
 * How the worker reaches the app's dependencies.
 *
 * An entry point rather than `@HiltWorker`: the worker is started by WorkManager, not by Hilt,
 * and this reaches the same singletons the rest of the app uses without the application having
 * to hand WorkManager a factory it otherwise has no use for.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MemoriesEntryPoint {

    fun memoriesRepository(): MemoriesRepository
    fun memoriesNotifier(): MemoriesNotifier
    fun eventLogStore(): EventLogStore

    companion object {
        fun from(context: Context): MemoriesEntryPoint =
            EntryPointAccessors.fromApplication(context, MemoriesEntryPoint::class.java)
    }
}
