package com.progallery.app.data.model

import android.net.Uri

/** Broad media category used for filtering. */
enum class MediaType { IMAGE, VIDEO, ALL }

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
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
    val isImage: Boolean get() = mediaType == MediaType.IMAGE

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
