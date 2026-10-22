package com.progallery.app.data.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Reaches the uploader from a worker, which Hilt does not construct directly here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogUploadEntryPoint {
    fun logUploader(): LogUploader

    companion object {
        fun from(context: Context): LogUploader =
            EntryPointAccessors.fromApplication(context, LogUploadEntryPoint::class.java).logUploader()
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
        return runCatching { uploader.uploadPending() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
