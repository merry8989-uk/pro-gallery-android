package com.progallery.app.data.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName

/** Reaches the uploader from a worker, which Hilt does not construct directly here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogUploadEntryPoint {
    fun logUploader(): LogUploader
    fun eventLogStore(): EventLogStore

    companion object {
        fun from(context: Context): LogUploader =
            EntryPointAccessors.fromApplication(context, LogUploadEntryPoint::class.java).logUploader()

        fun events(context: Context) =
            EntryPointAccessors.fromApplication(context, LogUploadEntryPoint::class.java).eventLogStore()
    }
}

/**
 * Sends whatever is pending, in the background.
 *
 * A retry is asked for only when a batch failed for a reason that might pass. There is no
 * backoff beyond WorkManager's own, because a log is not urgent: it will go on the next run, and
 * hammering a server that is down helps nobody.
 */
class LogUploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val uploader = LogUploadEntryPoint.from(applicationContext)
        val eventLog = LogUploadEntryPoint.events(applicationContext)
        eventLog.log(EventName.JOB_STARTED, details = mapOf("job" to JOB))

        return runCatching { uploader.uploadPending() }
            .fold(
                onSuccess = { result ->
                    eventLog.log(
                        EventName.JOB_FINISHED,
                        details = mapOf(
                            "job" to JOB,
                            "outcome" to if (result.skipped) "skipped" else "success",
                            "sent" to result.sent.toString(),
                            "batches" to result.batches.toString(),
                        ),
                    )
                    Result.success()
                },
                onFailure = { failure ->
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
        const val JOB = "log_upload"
    }
}
