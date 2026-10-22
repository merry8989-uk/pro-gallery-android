package com.progallery.app.data.documents

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the parts out of a zip container.
 *
 * A `.docx`, an `.xlsx`, an `.odt` and an `.epub` are all zips with XML inside, which is the
 * whole reason they needed a reader of their own: showing the bytes as text shows compressed
 * noise, and the text reader deliberately refuses them for that reason.
 *
 * **The limits are the point.** A zip declares how big its parts are and nothing stops it from
 * lying — a few kilobytes can expand to gigabytes. This app opens files a person chose, including
 * ones that arrived by message, so the expansion is capped and the cap is enforced **while
 * reading**, not checked against the declared size afterwards.
 *
 * Pure JVM, so it is unit tested against containers the tests build themselves.
 */
object ZipArchive {

    /** More parts than any document needs. */
    const val MAX_ENTRIES = 512

    /** The total expansion allowed across all parts. */
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    /** And the ceiling for any single part. */
    const val MAX_ENTRY_BYTES = 32L * 1024 * 1024

    class TooLarge(message: String) : Exception(message)

    /**
     * Every readable entry, keyed by its path with forward slashes.
     *
     * Directory entries are skipped, and an entry that would take the total past the cap stops
     * the read with [TooLarge] rather than being silently dropped — a document that cannot be
     * read honestly is reported, not half-read.
     */
    fun read(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entries.size >= MAX_ENTRIES) {
                    throw TooLarge("this archive has more than $MAX_ENTRIES parts")
                }
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    val content = readBounded(zip, MAX_ENTRY_BYTES)
                    total += content.size
                    if (total > MAX_TOTAL_BYTES) {
                        throw TooLarge("this archive expands to more than 64 MB")
                    }
                    entries[name] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    /** Reads one entry, refusing to grow past [limit] rather than allocating on trust. */
    private fun readBounded(zip: ZipInputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            if (out.size() + read > limit) {
                throw TooLarge("a part of this archive is larger than 32 MB")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** The first entry whose path ends with [suffix], case-insensitively. */
    fun find(entries: Map<String, ByteArray>, suffix: String): ByteArray? =
        entries.entries.firstOrNull { it.key.lowercase().endsWith(suffix.lowercase()) }?.value

    /** Every entry whose path ends with [suffix], in path order. */
    fun findAll(entries: Map<String, ByteArray>, suffix: String): List<Pair<String, ByteArray>> =
        entries.entries
            .filter { it.key.lowercase().endsWith(suffix.lowercase()) }
            .sortedBy { it.key }
            .map { it.key to it.value }
}
