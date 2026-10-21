package com.progallery.app.data.memories

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the daily reminder scheduled.
 *
 * Called once at startup. `KEEP` matters: re-enqueuing on every launch would reset the delay
 * each time the app was opened, so a user who opened the app every morning would never be
 * reminded at all.
 *
 * The work is also told not to run on a low battery. A reminder about old photographs is
 * exactly the kind of thing that should not cost the last of the charge.
 */
@Singleton
class MemoriesScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val UNIQUE_NAME = "memories-daily"

        /** How long until the next reminder, in minutes, from [now]. Exposed for testing. */
        fun initialDelayMinutes(now: ZonedDateTime): Long =
            MemoriesSchedule.untilNext(now).toMinutes().coerceAtLeast(0L)
    }

    fun ensureScheduled(now: ZonedDateTime = ZonedDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<MemoriesWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes(now), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
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
}
