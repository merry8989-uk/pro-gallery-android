package com.progallery.app.data.archive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What an extraction did, so the screen can say it rather than guess. */
data class ExtractionResult(
    val written: Int,
    val skipped: Int,
    val bytes: Long,
    /** Entries that could not be written, each with its reason. */
    val failures: List<String> = emptyList(),
)

/**
 * Unpacks an archive into a folder the person chose.
 *
 * **Every file is written by document id, under a name that cannot contain a slash.** The archive's
 * own paths are never used to build a location — only [ArchiveEntry.fileName] — so the "zip slip"
 * trick (`../../something`) cannot write outside the folder, by construction rather than by a check
 * that could be forgotten.
 *
 * **A part that is too big is refused, not truncated.** A zip declares its own sizes and can lie; the
 * cap is applied while reading. A truncated file that looks whole is worse than a refused one.
 *
 * **Nothing is written before the destination is known**, which is why this takes a granted tree
 * rather than a path: a `.zip` cannot be unpacked into `MediaStore`, because MediaStore has no
 * folders to unpack into.
 */
@Singleton
class ArchiveExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Extracts every file entry into [parentDocumentUri].
     *
     * [onProgress] reports how many have been written, so a large archive shows something moving.
     */
    fun extract(
        open: () -> InputStream?,
        parentDocumentUri: Uri,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
    ): ExtractionResult {
        val stream = open() ?: return ExtractionResult(0, 0, 0, listOf("That file could not be opened."))

        var written = 0
        var skipped = 0
        var bytes = 0L
        val failures = ArrayList<String>()
        var total = 0

        try {
            ZipInputStream(stream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    val isDirectory = entry.isDirectory || path.endsWith("/")

                    // Folders are made so a nested file lands somewhere sensible — but with a name
                    // that cannot escape, because it is the last segment and nothing else.
                    if (isDirectory) {
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }
                    if (path.isBlank()) {
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }

                    total += 1
                    val name = ArchiveEntry(path, 0, false).fileName
                    val mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                        ?: "application/octet-stream"

                    val writtenThis = runCatching {
                        val document = DocumentsContract.createDocument(
                            context.contentResolver,
                            parentDocumentUri,
                            mime,
                            name,
                        ) ?: return@runCatching null

                        var count = 0L
                        context.contentResolver.openOutputStream(document)?.use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                count += read
                                if (count > ArchiveReader.MAX_ENTRY_BYTES) {
                                    throw ArchiveReader.TooLarge(
                                        "\"$name\" is bigger than " +
                                            "${ArchiveReader.MAX_ENTRY_BYTES / (1024 * 1024)} MB",
                                    )
                                }
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        } ?: return@runCatching null

                        // A part that failed halfway is deleted rather than left behind: a
                        // truncated file that looks whole is worse than a missing one.
                        if (count == 0L) {
                            runCatching { DocumentsContract.deleteDocument(context.contentResolver, document) }
                            return@runCatching null
                        }
                        count
                    }.getOrNull()

                    if (writtenThis == null) {
                        skipped += 1
                    } else {
                        written += 1
                        bytes += writtenThis
                        onProgress(written, total)
                        if (bytes > ArchiveReader.MAX_TOTAL_BYTES) {
                            failures += "stopped at ${ArchiveReader.MAX_TOTAL_BYTES / (1024 * 1024)} MB"
                            break
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (tooLarge: ArchiveReader.TooLarge) {
            failures += tooLarge.message ?: "a part was too large"
        } catch (error: Exception) {
            failures += error.message ?: "the archive could not be read to the end"
        }

        return ExtractionResult(written = written, skipped = skipped, bytes = bytes, failures = failures)
    }
}
