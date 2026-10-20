package com.progallery.app.edit.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Progress/result states for a video export. */
sealed interface VideoExportState {
    data class Progress(val percent: Int) : VideoExportState
    data class Success(val uri: Uri) : VideoExportState
    data class Failure(val message: String) : VideoExportState
}

/**
 * Trims (and optionally mutes/rotates/rescales) a video using AndroidX Media3
 * Transformer. Exports to a cache file, then publishes it to the user-visible
 * Movies/ProGallery collection. Cancelling the collector cancels the export.
 */
@OptIn(UnstableApi::class)
@Singleton
class VideoTrimmer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun export(
        source: Uri,
        edit: VideoEditState,
        outputName: String,
    ): Flow<VideoExportState> = callbackFlow {
        val outputFile = File(context.cacheDir, "video_export/${System.currentTimeMillis()}_$outputName.mp4")
        outputFile.parentFile?.mkdirs()

        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(edit.trimStartMs)
                    .setEndPositionMs(if (edit.trimEndMs > 0) edit.trimEndMs else MediaItem.ClippingConfiguration.TIME_END_OF_SOURCE)
                    .build(),
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(edit.mute)
            .setEffects(buildEffects(edit))
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    publishToMediaStore(outputFile, outputName)?.let { uri ->
                        trySend(VideoExportState.Success(uri))
                    } ?: trySend(VideoExportState.Failure("Could not publish exported video"))
                    close()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                    trySend(VideoExportState.Failure(exception.message ?: "Export failed"))
                    close()
                }
            })
            .build()

        val progressHolder = ProgressHolder()
        val progressJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(VideoExportState.Progress(progressHolder.progress))
                }
                delay(250)
            }
        }

        try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (t: Throwable) {
            trySend(VideoExportState.Failure(t.message ?: "Could not start export"))
            close()
        }

        awaitClose {
            progressJob.cancel()
            transformer.cancel()
        }
    }.flowOn(Dispatchers.Default)

    private fun buildEffects(edit: VideoEditState): Effects {
        val effects = ArrayList<androidx.media3.common.Effect>()
        if (edit.rotationDegrees != 0) {
            effects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(edit.rotationDegrees.toFloat())
                .build()
        }
        if (edit.resolution != ResolutionPreset.ORIGINAL) {
            effects += Presentation.createForHeight(edit.resolution.targetHeight)
        }
        return Effects(emptyList(), effects)
    }

    private fun publishToMediaStore(file: File, displayName: String): Uri? {
        if (!file.exists()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ProGallery")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
            if (uri == null) {
                null
            } else {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                file.delete()
                uri
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ProGallery")
            if (!dir.exists() && !dir.mkdirs()) return null
            val dest = File(dir, "$displayName.mp4")
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
            file.delete()
            Uri.fromFile(dest)
        }
    }
}
