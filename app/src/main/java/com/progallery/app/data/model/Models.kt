package com.progallery.app.data.model

import android.net.Uri
import com.progallery.app.data.text.TextFormats

/**
 * Broad category of a file, used for browsing and filtering.
 *
 * [ALL] is the "no filter" value. Anything the registry cannot identify is
 * [OTHER] rather than being dropped, so every file the app can see is browsable.
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    OTHER,

    /** No filter — every kind. */
    ALL,
}

/** Where a media item was discovered. */
enum class MediaSource { MEDIA_STORE, DEEP_SCAN }

/**
 * A single photo or video, normalised from either MediaStore or a deep scan.
 * This is the primary model rendered everywhere in the UI.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    /**
     * When the picture was taken, from the file's own properties.
     *
     * Zero when the file carries no capture date, in which case [dateModifiedSeconds] is the
     * fallback. Kept separate rather than merged, because the two answer different questions
     * and only this one is a memory.
     */
    val dateTakenSeconds: Long = 0L,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String?,
    val mediaType: MediaType,
    val source: MediaSource = MediaSource.MEDIA_STORE,
    val isFavourite: Boolean = false,
) {
    /**
     * Stable identity for this item, and the key used for favourites, hidden
     * items and custom-album membership.
     *
     * The numeric [id] is NOT usable as identity: photo and video ids come from
     * different MediaStore collections and collide, and a deep-scanned file has
     * no MediaStore id at all. The content URI carries the volume, the media
     * type and the row id, so it stays unique across internal and removable
     * storage. It is derived, so it can never drift from [uri].
     */
    val mediaKey: String get() = uri.toString()

    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
    val isImage: Boolean get() = mediaType == MediaType.IMAGE
    val isAudio: Boolean get() = mediaType == MediaType.AUDIO
    val isDocument: Boolean get() = mediaType == MediaType.DOCUMENT
    val isArchive: Boolean get() = mediaType == MediaType.ARCHIVE

    /** True for PDFs, which have their own in-app viewer. */
    val isPdf: Boolean
        get() = mimeType.equals("application/pdf", ignoreCase = true) || extension == "pdf"

    /**
     * True when the app has its own reader for this file's text.
     *
     * A document, not just any file: the reader only claims the formats it actually knows, so
     * an office file is left to another app rather than shown as noise.
     */
    val isTextReadable: Boolean
        get() = TextFormats.canRead(extension)

    /** Whether the app can render an inline preview (image or video). */
    val isPreviewable: Boolean
        get() = mediaType == MediaType.IMAGE || mediaType == MediaType.VIDEO

    /** File extension, lowercased and without the dot. */
    val extension: String get() = FileTypeRegistry.extensionOf(displayName)

    /** Human readable dimensions, e.g. "1920 × 1080". */
    val dimensionLabel: String get() = if (width > 0 && height > 0) "$width × $height" else "—"
}

/** A folder-derived or user-created album. */
data class Album(
    val id: Long,
    val name: String,
    val coverUri: Uri?,
    val itemCount: Int,
    val totalBytes: Long,
    val isCustom: Boolean = false,
)

/** Result of a deep scan pass over one or more user-authorised trees. */
data class ScanSummary(
    val directoriesScanned: Int,
    val filesExamined: Int,
    val mediaFound: Int,
    val duplicatesFound: Int,
    val errors: List<String>,
    /** Files identified from their bytes rather than their extension. */
    val signatureIdentified: Int = 0,
    /** True when a configured limit stopped the scan early. */
    val truncated: Boolean = false,
    /**
     * How many files were fully hashed while looking for duplicates. The rest were
     * ruled out by size and a cheap sampled hash.
     */
    val fullHashesRun: Int = 0,
)

/** A set of files that are byte-identical (or same-size candidates). */
data class DuplicateGroup(
    val key: String,
    val items: List<MediaItem>,
    val wastedBytes: Long,
)

/** Sort options exposed in the UI. */
enum class SortOrder(val label: String) {
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    TYPE("Media type"),
}

/** View density for the media grid. */
enum class ViewMode { GRID, LIST }
