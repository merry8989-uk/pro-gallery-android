package com.progallery.app.data.upload

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the background send scheduled — and, just as importantly, stops it.
 *
 * **Turning the switch off cancels the work.** A consent gate that only stops *new* sends, while
 * a job already queued still runs, is not a consent gate. That is why [cancel] exists and why it
 * is called from the same place the setting changes.
 *
 * The work is also told to run only on an unmetered network and a charged battery. A diagnostics
 * log is not worth someone's mobile data, and it is never urgent.
 */
@Singleton
class LogUploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        const val UNIQUE_NAME = "diagnostics-upload"

        /** Six hours: often enough to be useful, rare enough to be invisible. */
        const val PERIOD_HOURS = 6L
    }

    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<LogUploadWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build(),
            )
            .build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun cancel() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
    }
}
