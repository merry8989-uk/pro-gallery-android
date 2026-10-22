package com.progallery.app.data.memories

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.progallery.app.data.eventlog.EventName
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
        val eventLog = entryPoint.eventLogStore()
        eventLog.log(EventName.JOB_STARTED, details = mapOf("job" to JOB))

        return runCatching {
            val memories = entryPoint.memoriesRepository().forDate(LocalDate.now())
            entryPoint.memoriesNotifier().notifyIfAnything(memories, LocalDate.now())
            eventLog.log(
                EventName.JOB_FINISHED,
                details = mapOf("job" to JOB, "outcome" to "success", "found" to memories.itemCount.toString()),
            )
            Result.success()
        }.getOrElse { failure ->
            eventLog.log(
                EventName.JOB_FAILED,
                details = mapOf(
                    "job" to JOB,
                    "exception_type" to failure.javaClass.name.substringAfterLast('.'),
                    "action" to "retry",
                ),
            )
            // A library that could not be read once is worth trying again; WorkManager's own
            // backoff decides when.
            Result.retry()
        }
    }

    private companion object {
        const val JOB = "memories_reminder"
    }
}
