package com.progallery.app.data.archive

import java.io.InputStream
import java.util.zip.ZipInputStream

/** One part of an archive, as it is listed before anything is extracted. */
data class ArchiveEntry(
    /** The path inside the archive, with forward slashes. */
    val path: String,
    /** What the archive says it is. Not trusted for allocation — see [ArchiveReader]. */
    val declaredBytes: Long,
    val isDirectory: Boolean,
) {
    /** Just the last segment, which is all a file can be named when it is extracted. */
    val fileName: String
        get() = path.substringAfterLast('/').ifBlank { "file" }

    /** How deep it sits, for indenting a list. */
    val depth: Int
        get() = path.count { it == '/' }
}

/**
 * Lists an archive without unpacking it into memory.
 *
 * `ZipArchive` already reads a zip **whole**, and that is right for a `.docx`: a few hundred
 * kilobytes, every part needed at once. It is wrong for a `.zip` a person chose, which can be two
 * gigabytes — so this reads the stream and reports what is inside, holding nothing but the names.
 *
 * **Two things are enforced here rather than trusted.**
 *
 * - **A cap on how much can come out.** A zip declares how big its parts are and nothing stops it
 *   lying; a few kilobytes can expand to gigabytes. The limit is applied **while reading**, not
 *   checked against the declared size afterwards.
 * - **A sanitised name.** An entry may be called `../../something` — the "zip slip" trick — and an
 *   extractor that joined that to a folder would write outside it. Nothing here trusts a path: only
 *   [ArchiveEntry.fileName] is used for writing, and it can never contain a slash.
 *
 * Pure apart from the stream it is handed, so it is tested against archives the tests build.
 */
object ArchiveReader {

    /** More parts than any archive a person browses needs. */
    const val MAX_ENTRIES = 2_000

    /** The total that may be extracted from one archive. */
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    /** And the ceiling for any single part. */
    const val MAX_ENTRY_BYTES = 128L * 1024 * 1024

    class TooLarge(message: String) : Exception(message)

    /** Not a zip at all, or one whose stream cannot be read. */
    class Unreadable(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Every entry, in the order the archive lists them.
     *
     * Directory entries are **kept**, because a listing that shows the folders is what a person
     * expects when they open an archive. They are simply not extracted as files.
     */
    fun list(open: () -> InputStream?): List<ArchiveEntry> {
        val stream = open() ?: throw Unreadable("That file could not be opened.")
        return try {
            val buffered = java.io.BufferedInputStream(stream)
            // **The signature is checked before anything else**, because `ZipInputStream` does not
            // throw on a file that is not a zip — it simply finds no entries and ends. A text file
            // opened as an archive would then look like an *empty* archive, which is a worse answer
            // than saying it is not one.
            buffered.mark(4)
            val magic = ByteArray(4)
            val read = buffered.read(magic)
            buffered.reset()
            if (read < 4 || !isZipSignature(magic)) {
                throw Unreadable(
                    "This is not a zip archive. The app can list and unpack .zip, .jar and .apk; " +
                        ".rar, .7z and .tar are recognised but cannot be opened here yet.",
                )
            }
            ZipInputStream(buffered).use { zip ->
                val entries = ArrayList<ArchiveEntry>()
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entries.size >= MAX_ENTRIES) {
                        throw TooLarge("this archive has more than $MAX_ENTRIES parts")
                    }
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    if (path.isNotBlank()) {
                        entries += ArchiveEntry(
                            path = path,
                            declaredBytes = entry.size.coerceAtLeast(0L),
                            isDirectory = entry.isDirectory || path.endsWith("/"),
                        )
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                entries
            }
        } catch (tooLarge: TooLarge) {
            throw tooLarge
        } catch (error: Exception) {
            throw Unreadable(
                "This does not look like an archive this app can read. It may be a different " +
                    "compression format, or the file may be incomplete.",
                error,
            )
        }
    }

    /**
     * Whether these four bytes are the start of a zip.
     *
     * `PK\x03\x04` is a normal entry, `PK\x05\x06` an **empty** archive — which is a real archive
     * with nothing in it, and must not be reported as a broken file — and `PK\x07\x08` a spanned one.
     */
    private fun isZipSignature(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2].toInt() in 1..8 &&
            bytes[3].toInt() in 3..8
}
