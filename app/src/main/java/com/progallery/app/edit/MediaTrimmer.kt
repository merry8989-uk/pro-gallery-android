package com.progallery.app.edit

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
import com.progallery.app.edit.video.VideoEditState
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
import kotlinx.coroutines.CancellationException

/** What kind of media an export produces, which decides where it is published. */
enum class TrimKind { VIDEO, AUDIO }

/** Progress/result states for a trim/export. */
sealed interface MediaExportState {
    data class Progress(val percent: Int) : MediaExportState
    data class Success(val uri: Uri) : MediaExportState
    data class Failure(val message: String) : MediaExportState
}

/**
 * Trims (and optionally mutes/rotates/rescales) a video, or trims an audio
 * track, using AndroidX Media3 Transformer. Exports to a cache file, then
 * publishes it to the user-visible collection — Movies/ProGallery for video,
 * Music/ProGallery for audio. Cancelling the collector cancels the export and
 * removes the temporary file.
 */
@OptIn(UnstableApi::class)
@Singleton
class MediaTrimmer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun export(
        source: Uri,
        edit: VideoEditState,
        outputName: String,
        kind: TrimKind = TrimKind.VIDEO,
    ): Flow<MediaExportState> = callbackFlow {
        val extension = if (kind == TrimKind.AUDIO) "m4a" else "mp4"
        val outputFile = File(
            context.cacheDir,
            "media_export/${System.currentTimeMillis()}_$outputName.$extension",
        )
        outputFile.parentFile?.mkdirs()

        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(buildClipping(edit))
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(edit.mute && kind == TrimKind.VIDEO)
            .setEffects(if (kind == TrimKind.VIDEO) buildEffects(edit) else Effects(emptyList(), emptyList()))
            .build()

        val builder = Transformer.Builder(context)
        if (kind == TrimKind.VIDEO) builder.setVideoMimeType(MimeTypes.VIDEO_H264)

        val transformer = builder
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val uri = publishToMediaStore(outputFile, outputName, kind)
                    if (uri != null) {
                        trySend(MediaExportState.Success(uri))
                    } else {
                        trySend(MediaExportState.Failure("Could not publish the exported file"))
                    }
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException,
                ) {
                    outputFile.delete()
                    trySend(MediaExportState.Failure(exception.message ?: "Export failed"))
                    close()
                }
            })
            .build()

        val progressHolder = ProgressHolder()
        val progressJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(MediaExportState.Progress(progressHolder.progress))
                }
                delay(250)
            }
        }

        try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            outputFile.delete()
            trySend(MediaExportState.Failure(t.message ?: "Could not start export"))
            close()
        }

        awaitClose {
            progressJob.cancel()
            transformer.cancel()
            outputFile.delete()
        }
    }.flowOn(Dispatchers.Default)

    /** Clips to the trimmed range; leaves the end open when it is not set. */
    private fun buildClipping(edit: VideoEditState): MediaItem.ClippingConfiguration {
        val builder = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(edit.trimStartMs)
        if (edit.trimEndMs > 0) builder.setEndPositionMs(edit.trimEndMs)
        return builder.build()
    }

    private fun buildEffects(edit: VideoEditState): Effects {
        val effects = ArrayList<androidx.media3.common.Effect>()
        if (edit.rotationDegrees != 0) {
            effects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(edit.rotationDegrees.toFloat())
                .build()
        }
        if (edit.resolution != com.progallery.app.edit.video.ResolutionPreset.ORIGINAL) {
            effects += Presentation.createForHeight(edit.resolution.targetHeight)
        }
        return Effects(emptyList(), effects)
    }

    private fun publishToMediaStore(file: File, displayName: String, kind: TrimKind): Uri? {
        if (!file.exists()) return null
        val isAudio = kind == TrimKind.AUDIO
        val extension = if (isAudio) "m4a" else "mp4"
        val mime = if (isAudio) "audio/mp4" else "video/mp4"
        val directory = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/ProGallery")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values)
            if (uri == null) {
                null
            } else {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                file.delete()
                uri
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(directory), "ProGallery")
            if (!dir.exists() && !dir.mkdirs()) return null
            val dest = File(dir, "$displayName.$extension")
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
            file.delete()
            Uri.fromFile(dest)
        }
    }
}
