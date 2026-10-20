package com.progallery.app.edit.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Output format + quality the user chose for an export. */
enum class ExportFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
}

/**
 * Writes an edited bitmap back to storage. By default a NEW file is created
 * (the original is preserved). An explicit overwrite writes into the original
 * MediaStore entry, which the caller must have consent for on Android 11+.
 */
@Singleton
class PhotoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun export(
        bitmap: Bitmap,
        displayName: String,
        format: ExportFormat,
        quality: Int,
        overwriteUri: Uri? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        val compressFormat = when (format) {
            ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP
        }
        runCatching {
            if (overwriteUri != null) {
                context.contentResolver.openOutputStream(overwriteUri)?.use { out ->
                    bitmap.compress(compressFormat, quality, out)
                }
                overwriteUri
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToMediaStore(bitmap, compressFormat, quality, displayName, format)
            } else {
                writeLegacy(bitmap, compressFormat, quality, displayName, format)
            }
        }.getOrNull()
    }

    private fun writeToMediaStore(
        bitmap: Bitmap,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
        displayName: String,
        format: ExportFormat,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ProGallery")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return null
        val stream: OutputStream = context.contentResolver.openOutputStream(uri) ?: return null
        stream.use { bitmap.compress(compressFormat, quality, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }

    private fun writeLegacy(
        bitmap: Bitmap,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
        displayName: String,
        format: ExportFormat,
    ): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ProGallery")
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, "$displayName.${format.extension}")
        file.outputStream().use { bitmap.compress(compressFormat, quality, it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mimeType), null)
        return Uri.fromFile(file)
    }
}
