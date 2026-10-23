package com.progallery.app.data.trash

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName
import com.progallery.app.data.repository.TrashRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** How the worker reaches the app's dependencies, which Hilt does not construct for it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TrashPurgeEntryPoint {
    fun trashRepository(): TrashRepository
    fun eventLogStore(): EventLogStore

    companion object {
        fun from(context: Context): TrashPurgeEntryPoint =
            EntryPointAccessors.fromApplication(context, TrashPurgeEntryPoint::class.java)
    }
}

/**
 * Removes trash entries past their retention, whether or not anyone opens the Trash screen.
 *
 * `purgeExpired` has existed since the trash did, and it ran from **one** place: the Trash screen's
 * `init`. So a library nobody opened never expired, and the promise that a deleted file goes after
 * thirty days was kept only for people who went looking for it.
 *
 * It is deliberately **not** a precise schedule. A day either way does not matter for a file that
 * has already been sitting there for thirty, and asking WorkManager to be exact would mean an
 * exact alarm — which is a permission, a battery cost, and the wrong tool for this.
 */
class TrashPurgeWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val entryPoint = TrashPurgeEntryPoint.from(applicationContext)
        val eventLog = entryPoint.eventLogStore()
        eventLog.log(EventName.JOB_STARTED, details = mapOf("job" to JOB))

        return runCatching { entryPoint.trashRepository().purgeExpired(RETENTION_DAYS) }
            .fold(
                onSuccess = { purged ->
                    eventLog.log(
                        EventName.JOB_FINISHED,
                        details = mapOf("job" to JOB, "outcome" to "success", "purged" to purged.toString()),
                    )
                    Result.success()
                },
                onFailure = { failure ->
                    // A file that could not be deleted is worth trying again: the next run will find
                    // it again, because nothing was removed from the database.
                    eventLog.log(
                        EventName.JOB_FAILED,
                        details = mapOf(
                            "job" to JOB,
                            "exception_type" to failure.javaClass.name.substringAfterLast('.'),
                            "action" to "retry",
                        ),
                    )
                    Result.retry()
                },
            )
    }

    private companion object {
        const val JOB = "trash_purge"
        const val RETENTION_DAYS = 30
    }
}
