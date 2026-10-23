package com.progallery.app.data.detection

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import com.progallery.app.data.model.FileTypeRegistry
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.recovery.FileSignatureDetector
import com.progallery.app.data.settings.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a camera recorded about a picture.
 *
 * **No location.** `ExifInterface` will hand over GPS coordinates as readily as a date, and reading
 * them into a result card would put where somebody was into a screen they may well screenshot and
 * share. The master prompt asks for metadata "where available"; it does not ask for that, and a
 * recovery tool has no use for it. It is a deliberate omission rather than an unimplemented one.
 */
data class MediaExif(
    val takenAt: Long? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
) {
    val isEmpty: Boolean get() = takenAt == null && cameraMake == null && cameraModel == null
}

/**
 * Reads the few bytes needed to say whether a file is intact and what it actually is.
 *
 * The reads are **bounded and separate**: the signature detector already reads a header, and this
 * adds only the trailing bytes that the formats with their own end marker need. Nothing reads a
 * whole file, and every failure is a null rather than an exception — a scan must not stop because
 * one file could not be opened.
 */
@Singleton
class MediaInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        /** Enough for a JPEG's `FFD9` or a PNG's `IEND` chunk, and no more. */
        const val TRAILER_BYTES = 64
    }

    suspend fun inspect(item: MediaItem, settings: AppSettings): Pair<IntegrityReport?, MediaExif?> =
        withContext(Dispatchers.IO) {
            val integrity = if (settings.detectCorruptMedia || settings.detectWrongExtensions) {
                runCatching { analyse(item, settings) }.getOrNull()
            } else {
                null
            }
            val exif = if (settings.extractExif && item.isImage) {
                runCatching { readExif(item) }.getOrNull()
            } else {
                null
            }
            integrity to exif
        }

    private fun analyse(item: MediaItem, settings: AppSettings): IntegrityReport {
        val extension = item.extension
        val knownMedia = FileTypeRegistry.kindFor(null, "file.$extension") != MediaType.OTHER

        val header = runCatching {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                FileSignatureDetector.readHeader(stream, settings.effectiveSignatureBytes)
            }
        }.getOrNull()

        val trailer = if (extension.equals("jpg", true) || extension.equals("jpeg", true) ||
            extension.equals("png", true)
        ) {
            runCatching { readTrailer(item.uri) }.getOrNull()
        } else {
            null
        }

        val match = header?.let { FileSignatureDetector.detect(it) }
        val matchesExtension = match?.let {
            // The detector's kind against what the app thinks the extension means.
            it.kind == FileTypeRegistry.kindFor(null, "file.$extension")
        }

        return MediaIntegrity.analyse(
            MediaIntegrity.Input(
                extension = extension,
                declaredSize = item.sizeBytes,
                header = header,
                trailer = trailer,
                signatureFormatName = match?.formatName,
                signatureMatchesExtension = matchesExtension,
                extensionIsKnownMedia = knownMedia,
            ),
        ).let { report ->
            // A check the person turned off must not report anything, even if the other one is on.
            when {
                !settings.detectCorruptMedia && !settings.detectWrongExtensions -> IntegrityReport()
                !settings.detectCorruptMedia -> report.copy(
                    issues = report.issues.filter { it == IntegrityIssue.EXTENSION_MISMATCH },
                )
                !settings.detectWrongExtensions -> report.copy(
                    issues = report.issues.filter { it != IntegrityIssue.EXTENSION_MISMATCH },
                )
                else -> report
            }
        }
    }

    /** The last bytes of a document, which is where the formats that end with a marker keep it. */
    private fun readTrailer(uri: Uri): ByteArray? {
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: return null
        if (size <= 0L) return null

        val skip = (size - TRAILER_BYTES).coerceAtLeast(0L)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var remaining = skip
                while (remaining > 0) {
                    val skipped = stream.skip(remaining)
                    if (skipped <= 0) break
                    remaining -= skipped
                }
                stream.readBytes().take(TRAILER_BYTES.toInt())
            }
        }.getOrNull()
    }

    private fun readExif(item: MediaItem): MediaExif? {
        val exif = context.contentResolver.openInputStream(item.uri)?.use { ExifInterface(it) } ?: return null
        val taken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.let { parseExifDate(it) }
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { parseExifDate(it) }

        return MediaExif(
            takenAt = taken,
            cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.takeIf { it.isNotBlank() },
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.takeIf { it.isNotBlank() },
        ).takeIf { !it.isEmpty }
    }

    /** EXIF dates are `yyyy:MM:dd HH:mm:ss` — colons in the date, which no standard parser takes. */
    private fun parseExifDate(raw: String): Long? = runCatching {
        val normalised = raw.trim().let {
            if (it.length >= 10) it.substring(0, 4) + "-" + it.substring(5, 7) + "-" + it.substring(8) else it
        }
        java.time.LocalDateTime.parse(normalised.replace(' ', 'T'))
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
