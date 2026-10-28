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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.progallery.app.util.runCatchingCancellable

/**
 * How large an export may be.
 *
 * [ORIGINAL] keeps the source's own dimensions, so a 50 MP photo is not silently
 * shrunk. The smaller options exist because re-rendering a very large bitmap can
 * exhaust the heap.
 */
enum class ExportResolution(val label: String, val maxDimension: Int) {
    ORIGINAL("Original", Int.MAX_VALUE),
    LARGE("4096 px", 4096),
    MEDIUM("2048 px", 2048),
}

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
        runCatchingCancellable {
            when {
                overwriteUri != null -> overwrite(bitmap, compressFormat, quality, overwriteUri)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    writeToMediaStore(bitmap, compressFormat, quality, displayName, format)
                else -> writeLegacy(bitmap, compressFormat, quality, displayName, format)
            }
        }.getOrNull()
    }

    /**
     * Writes into an existing entry. Returns null when compression fails, so the
     * caller does not report a successful overwrite of a file that was not written.
     */
    /**
     * Writes over an existing entry — **without being able to destroy it**.
     *
     * The old version opened the destination with mode `"wt"`, which **truncates before anything is
     * written**. If compression then failed, the original was already gone: a person who edited a
     * photo and got an error had lost the photo. That is the worst failure this app can have, and it
     * was one line.
     *
     * Now the image is encoded **into memory first**. Only a complete, non-empty result is ever
     * opened over the destination, so a compression failure cannot touch the file at all. It costs
     * one image's worth of memory, which is the right price for never losing the original.
     */
    private fun overwrite(
        bitmap: Bitmap,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
        overwriteUri: Uri,
    ): Uri? {
        // Encode first. Nothing is opened, truncated or written until this has succeeded.
        val encoded = java.io.ByteArrayOutputStream()
        val wrote = runCatching { bitmap.compress(compressFormat, quality, encoded) }.getOrDefault(false)
        val bytes = encoded.toByteArray()
        if (!wrote || bytes.isEmpty()) {
            // The destination has not been touched. The original is intact.
            error("the image could not be encoded, so the original was left untouched")
        }

        val out = context.contentResolver.openOutputStream(overwriteUri, "wt")
            ?: error("the file could not be opened for writing")
        out.use { it.write(bytes) }
        return overwriteUri
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
        try {
            val stream: OutputStream = context.contentResolver.openOutputStream(uri)
                ?: error("the destination could not be opened")
            val wrote = stream.use { bitmap.compress(compressFormat, quality, it) }
            if (!wrote) error("the image could not be encoded")

            // Finalise, then verify: a row left pending (or finalised empty) is an
            // orphan the user can neither see nor clean up.
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)

            if (publishedSize(uri) <= 0L) error("the exported file came out empty")
            return uri
        } catch (t: Throwable) {
            runCatchingCancellable { context.contentResolver.delete(uri, null, null) }
            if (t is CancellationException) throw t
            return null
        }
    }

    /** The size MediaStore recorded for a published row, or 0 when unknown. */
    private fun publishedSize(uri: Uri): Long = runCatchingCancellable {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
            ?: 0L
    }.getOrDefault(0L)

    private fun writeLegacy(
        bitmap: Bitmap,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
        displayName: String,
        format: ExportFormat,
    ): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ProGallery")
        if (!dir.exists() && !dir.mkdirs()) return null
        // A name that does not exist yet, so an export can never overwrite something a person
        // already had. `file.outputStream()` truncated a same-named file, and the catch below then
        // **deleted** it — a failed export could destroy a file it had never written.
        val file = uniqueFile(dir, displayName, format.extension)
        var created = false
        try {
            // `createNewFile` is the guard: it fails rather than truncating, so a race with another
            // writer cannot silently take their file either.
            created = file.createNewFile()
            if (!created) error("a file with that name already exists")

            val encoded = java.io.ByteArrayOutputStream()
            val wrote = runCatching { bitmap.compress(compressFormat, quality, encoded) }.getOrDefault(false)
            val bytes = encoded.toByteArray()
            if (!wrote || bytes.isEmpty()) error("the image could not be encoded")

            file.outputStream().use { it.write(bytes) }
            if (file.length() <= 0L) error("nothing was written")

            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mimeType), null)
            return Uri.fromFile(file)
        } catch (t: Throwable) {
            // **Only a file this call created is ever deleted.** Anything that existed before this
            // operation is not ours to remove, whatever went wrong.
            if (created) file.delete()
            if (t is CancellationException) throw t
            return null
        }
    }

    /**
     * A name nothing is using: `photo.jpg`, then `photo (1).jpg`, `photo (2).jpg` …
     *
     * Bounded, because a folder full of thousands of same-named exports should fail with an answer
     * rather than spin.
     */
    private fun uniqueFile(dir: File, displayName: String, extension: String): File {
        val first = File(dir, "$displayName.$extension")
        if (!first.exists()) return first
        for (index in 1..MAX_NAME_ATTEMPTS) {
            val candidate = File(dir, "$displayName ($index).$extension")
            if (!candidate.exists()) return candidate
        }
        // Everything taken: fall back to a timestamp, which cannot collide in practice.
        return File(dir, "$displayName-${System.currentTimeMillis()}.$extension")
    }

    private companion object {
        const val MAX_NAME_ATTEMPTS = 500
    }
}
