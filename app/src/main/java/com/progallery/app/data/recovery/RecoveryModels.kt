package com.progallery.app.data.recovery

import com.progallery.app.data.model.MediaItem

/** Where a discovered file came from. Shown on every result card. */
enum class RecoverySource(val label: String) {
    /** Bytes this app is holding in its own Recently Deleted. */
    TRASH("Recently Deleted"),

    /** Listed by the system's own media index. */
    MEDIA_STORE("System index"),

    /** Found by walking a folder the user granted. */
    SAF_FOLDER("Granted folder"),

    /** A thumbnail or cache artefact — never the original. */
    THUMBNAIL_CACHE("Thumbnail / cache"),

    /** Identified only from its bytes. */
    SIGNATURE("Signature only"),
}

/** How sure we are that a discovered file is what it appears to be. */
enum class RecoveryConfidence(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
}

/** What can actually be done with a result. */
enum class RecoveryStatus(val label: String) {
    /** Readable and can be exported. */
    AVAILABLE("Available"),

    /** Can be looked at, but not recovered as a file. */
    PREVIEW_ONLY("Preview only"),

    /** Already exported by the user. */
    EXPORTED("Exported"),

    /** The last attempt failed. */
    FAILED("Failed"),
}

/**
 * One discovered file, with the evidence behind its classification.
 *
 * An indexed file is deliberately **not** called a deleted file: [source] and
 * [evidence] say where it came from, so the user is never told that an ordinary
 * gallery item is a recovery.
 */
data class RecoveryResult(
    /** Stable key: the source plus the URI, so list keys never collide. */
    val id: String,
    val item: MediaItem,
    val source: RecoverySource,
    val confidence: RecoveryConfidence,
    val status: RecoveryStatus,
    /** Why this confidence was chosen, in plain words. */
    val evidence: String,
    /** True only for thumbnail/cache artefacts, which are never the original. */
    val isThumbnail: Boolean = false,
    /** Where it was found, for the card subtitle. */
    val originLabel: String = "",
) {
    /**
     * Whether the bytes can be copied out.
     *
     * A thumbnail can: it is a real, readable file — it is just not the original,
     * so the export names it as a thumbnail. A preview-only result that is *not* a
     * thumbnail cannot be read at all, so there is nothing to copy.
     */
    val canExport: Boolean
        get() = status == RecoveryStatus.AVAILABLE ||
            (status == RecoveryStatus.PREVIEW_ONLY && isThumbnail)
}

/** Confidence, evidence and status derived from what we actually know. */
data class RecoveryJudgement(
    val confidence: RecoveryConfidence,
    val status: RecoveryStatus,
    val evidence: String,
)

/** A quick scan's tallies, so the screen can explain what was really looked at. */
data class RecoveryScanSummary(
    val indexedFound: Int = 0,
    val trashFound: Int = 0,
    val unindexedFound: Int = 0,
    val foldersScanned: Int = 0,
    val skippedAlreadyIndexed: Int = 0,
    val errors: List<String> = emptyList(),
)

/** The two scan modes the recovery screen offers. */
enum class RecoveryScanMode(val label: String, val explanation: String) {
    QUICK(
        label = "Quick Scan",
        explanation = "Everything the app can reach: the system index, its own " +
            "Recently Deleted, and folders you granted.",
    ),
    THUMBNAIL_CACHE(
        label = "Thumbnail & cache",
        explanation = "Only thumbnails and cached previews found in folders you granted. " +
            "These are small preview files, never the full-resolution original.",
    ),
}

sealed interface RecoveryScanState {
    data object Idle : RecoveryScanState
    data class Scanning(val stage: String, val found: Int) : RecoveryScanState
    data class Completed(
        val results: List<RecoveryResult>,
        val summary: RecoveryScanSummary,
    ) : RecoveryScanState
    data class Failed(val message: String) : RecoveryScanState
}

/** The outcome of exporting one result. */
sealed interface RecoveryExportOutcome {
    data class Success(val displayName: String, val sizeBytes: Long) : RecoveryExportOutcome

    /**
     * Nothing was copied, and that was the intended outcome.
     *
     * Kept separate from [Failed] so the history does not report a deliberate skip as
     * something that went wrong.
     */
    data class Skipped(val displayName: String, val reason: String) : RecoveryExportOutcome

    data class Failed(val message: String) : RecoveryExportOutcome
}
