package com.progallery.app.data.storage

import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType

/** One kind of file, and how much of what the app can see it accounts for. */
data class KindShare(
    val kind: MediaType,
    val count: Int,
    val bytes: Long,
    /** This kind's share of the total, from 0 to 1. Zero when there is nothing at all. */
    val fraction: Float,
)

/** One folder, and how much sits in it. */
data class FolderShare(
    /** The top-level folder — `DCIM`, `Download` — not the whole path. */
    val name: String,
    val count: Int,
    val bytes: Long,
    val fraction: Float,
)

/**
 * Where the space went, as far as the app can tell.
 *
 * **"As far as the app can tell" is the whole of it.** This is built from the app's own index — the
 * files it was allowed to read — and not from the device. A summary that added up to "your phone is
 * full" while the app could see a tenth of it would be worse than no summary, because it would look
 * like an answer. The screen says so in the same breath as the numbers.
 */
data class StorageSummary(
    val totalCount: Int,
    val totalBytes: Long,
    /** Every kind with something in it, largest first. */
    val byKind: List<KindShare>,
    /** The folders holding the most, largest first. */
    val largestFolders: List<FolderShare>,
    /** Bytes in the app's own trash, which is recoverable because the app is holding it. */
    val inTrashBytes: Long = 0,
    val inTrashCount: Int = 0,
) {
    val isEmpty: Boolean get() = totalCount == 0
}

/**
 * Builds the summary from the files the app can see.
 *
 * Pure, so every figure is tested rather than read off a screen and believed. Two decisions worth
 * naming:
 *
 * - **A file with no path counts under "Unknown"** rather than being left out, so the parts always
 *   add up to the total. A summary whose rows do not sum to its own total is a summary nobody can
 *   check.
 * - **Folders are counted by their top-level name.** "DCIM" is the answer to "where did my space
 *   go"; "DCIM/Camera/2026/October" is not, and a list of those is a list of every folder.
 */
object StorageSummaryBuilder {

    /** What a file with no recorded path is filed under, so the rows still add up. */
    const val UNKNOWN_FOLDER = "Unknown"

    fun build(
        items: List<MediaItem>,
        inTrashBytes: Long = 0,
        inTrashCount: Int = 0,
        folderLimit: Int = 5,
    ): StorageSummary {
        val totalBytes = items.sumOf { it.sizeBytes }

        val byKind = MediaType.entries
            .filter { it != MediaType.ALL }
            .mapNotNull { kind ->
                val of = items.filter { it.mediaType == kind }
                if (of.isEmpty()) {
                    null
                } else {
                    val bytes = of.sumOf { it.sizeBytes }
                    KindShare(kind, of.size, bytes, fraction(bytes, totalBytes))
                }
            }
            .sortedByDescending { it.bytes }

        val largestFolders = items
            .groupBy { topLevelFolderOf(it) }
            .map { (name, of) ->
                val bytes = of.sumOf { it.sizeBytes }
                FolderShare(name, of.size, bytes, fraction(bytes, totalBytes))
            }
            .sortedByDescending { it.bytes }
            .take(folderLimit)

        return StorageSummary(
            totalCount = items.size,
            totalBytes = totalBytes,
            byKind = byKind,
            largestFolders = largestFolders,
            inTrashBytes = inTrashBytes,
            inTrashCount = inTrashCount,
        )
    }

    /**
     * The first segment of a file's path, or "Unknown".
     *
     * `DCIM/Camera/a.jpg` is in `DCIM`. A path that is only a file name is at the root, and the root
     * is not a folder anybody thinks about — so it is grouped with the files that have no path at
     * all rather than given a name of its own.
     */
    private fun topLevelFolderOf(item: MediaItem): String {
        val path = item.relativePath?.takeIf { it.isNotBlank() } ?: return UNKNOWN_FOLDER
        val first = path.substringBefore('/').takeIf { it.isNotBlank() && it != path }
        return first ?: UNKNOWN_FOLDER
    }

    /** A share of the whole, and zero rather than a division by nothing when there is nothing. */
    private fun fraction(part: Long, whole: Long): Float =
        if (whole <= 0L) 0f else (part.toDouble() / whole.toDouble()).toFloat()
}
