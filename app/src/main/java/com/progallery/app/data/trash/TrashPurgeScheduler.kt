package com.progallery.app.data.trash

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the trash purge running.
 *
 * The same shape as the memories reminder, and for the same reason: WorkManager runs it when the
 * app is not open and repeats it after a reboot, so the retention the app promises does not depend
 * on somebody opening a screen.
 *
 * `KEEP` rather than `UPDATE`: the work is already scheduled and identical, so replacing it on
 * every launch would only reset its timer — a purge that keeps being pushed back by every app start
 * would never run for the person who opens the app often.
 */
@Singleton
class TrashPurgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        const val UNIQUE_NAME = "trash-purge-daily"
    }

    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS)
            // A day's grace before the first run: an app opened once, immediately after install,
            // should not spend that moment deleting files.
            .setInitialDelay(1, TimeUnit.DAYS)
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
