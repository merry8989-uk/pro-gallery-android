package com.progallery.app.data.recovery

import com.progallery.app.data.model.MediaType

/** The tabs on the recovery screen. */
enum class RecoveryTab(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    OTHER("Other"),
    TRASH("Recently Deleted"),
    THUMBNAILS("Thumbnails"),
}

/** How results can be ordered. */
enum class RecoverySort(val label: String) {
    RELEVANCE("Most recoverable"),
    NEWEST("Newest first"),
    LARGEST("Largest first"),
    NAME("Name"),
}

/**
 * Filtering and ordering for recovery results. Pure, so the rules are unit tested
 * rather than clicked through.
 */
object RecoveryQuery {

    fun apply(
        results: List<RecoveryResult>,
        tab: RecoveryTab,
        query: String,
        sort: RecoverySort,
    ): List<RecoveryResult> {
        val trimmed = query.trim()
        val filtered = results.asSequence()
            .filter { inTab(it, tab) }
            .filter { matches(it, trimmed) }
            .toList()

        return when (sort) {
            RecoverySort.RELEVANCE -> filtered.sortedWith(relevanceOrder)
            RecoverySort.NEWEST -> filtered.sortedByDescending { it.item.dateModifiedSeconds }
            RecoverySort.LARGEST -> filtered.sortedByDescending { it.item.sizeBytes }
            RecoverySort.NAME -> filtered.sortedBy { it.item.displayName.lowercase() }
        }
    }

    private fun inTab(result: RecoveryResult, tab: RecoveryTab): Boolean = when (tab) {
        RecoveryTab.ALL -> true
        RecoveryTab.PHOTOS -> result.item.mediaType == MediaType.IMAGE
        RecoveryTab.VIDEOS -> result.item.mediaType == MediaType.VIDEO
        RecoveryTab.AUDIO -> result.item.mediaType == MediaType.AUDIO
        RecoveryTab.DOCUMENTS -> result.item.mediaType == MediaType.DOCUMENT
        RecoveryTab.OTHER ->
            result.item.mediaType == MediaType.ARCHIVE || result.item.mediaType == MediaType.OTHER
        RecoveryTab.TRASH -> result.source == RecoverySource.TRASH
        RecoveryTab.THUMBNAILS -> result.isThumbnail
    }

    private fun matches(result: RecoveryResult, query: String): Boolean {
        if (query.isEmpty()) return true
        val haystack = buildString {
            append(result.item.displayName).append(' ')
            append(result.item.bucketName).append(' ')
            append(result.source.label).append(' ')
            append(result.item.mediaType.name).append(' ')
            append(result.evidence)
        }
        return haystack.contains(query, ignoreCase = true)
    }

    /**
     * "Most recoverable" puts what the app is actually holding first, then files it
     * found on disk, then things the system already indexes, and thumbnail
     * artefacts last — because those are not the original file.
     */
    private val relevanceOrder = compareBy<RecoveryResult>(
        { sourceRank(it.source) },
        { confidenceRank(it.confidence) },
        { -it.item.dateModifiedSeconds },
    )

    private fun sourceRank(source: RecoverySource): Int = when (source) {
        RecoverySource.TRASH -> 0
        RecoverySource.SAF_FOLDER -> 1
        RecoverySource.SIGNATURE -> 2
        RecoverySource.MEDIA_STORE -> 3
        RecoverySource.THUMBNAIL_CACHE -> 4
    }

    private fun confidenceRank(confidence: RecoveryConfidence): Int = when (confidence) {
        RecoveryConfidence.HIGH -> 0
        RecoveryConfidence.MEDIUM -> 1
        RecoveryConfidence.LOW -> 2
    }
}
