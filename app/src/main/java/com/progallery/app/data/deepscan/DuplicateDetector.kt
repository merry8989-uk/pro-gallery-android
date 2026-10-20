package com.progallery.app.data.deepscan

import com.progallery.app.data.model.DuplicateGroup
import com.progallery.app.data.model.MediaItem

/**
 * Finds duplicate media. It first narrows candidates by exact file size (cheap),
 * then confirms with a content hash (accurate). The hash function is injected so
 * the logic is unit-testable without touching the filesystem.
 */
class DuplicateDetector(
    private val hasher: suspend (MediaItem) -> String?,
) {

    /** Groups items whose sizes match — the candidate set for hashing. */
    fun sameSizeCandidates(items: List<MediaItem>): Map<Long, List<MediaItem>> =
        items.filter { it.sizeBytes > 0 }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }

    /**
     * Returns confirmed duplicate groups. Only size-collision candidates are
     * hashed, so a large library with few duplicates stays fast.
     */
    suspend fun findDuplicates(items: List<MediaItem>): List<DuplicateGroup> {
        val groups = ArrayList<DuplicateGroup>()
        for ((_, candidates) in sameSizeCandidates(items)) {
            val byHash = HashMap<String, MutableList<MediaItem>>()
            for (item in candidates) {
                val hash = hasher(item) ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(item)
            }
            byHash.forEach { (hash, dupes) ->
                if (dupes.size > 1) {
                    val wasted = dupes.drop(1).sumOf { it.sizeBytes }
                    groups += DuplicateGroup(key = hash, items = dupes, wastedBytes = wasted)
                }
            }
        }
        return groups.sortedByDescending { it.wastedBytes }
    }
}
