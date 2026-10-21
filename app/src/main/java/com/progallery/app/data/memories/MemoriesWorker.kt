package com.progallery.app.data.memories

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

/**
 * The once-a-day check that sends the memories reminder.
 *
 * WorkManager runs this even when the app is not open, and repeats it after a reboot — which
 * is what makes the reminder dependable without the app having to be running, and without a
 * foreground service or an exact alarm.
 */
class MemoriesWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val entryPoint = MemoriesEntryPoint.from(applicationContext)
        return runCatching {
            val memories = entryPoint.memoriesRepository().forDate(LocalDate.now())
            entryPoint.memoriesNotifier().notifyIfAnything(memories, LocalDate.now())
            Result.success()
        }.getOrElse {
            // A library that could not be read once is worth trying again; WorkManager's own
            // backoff decides when.
            Result.retry()
        }
    }
}
