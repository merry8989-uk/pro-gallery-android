package com.progallery.app.data.deepscan

import com.progallery.app.data.model.DuplicateGroup
import com.progallery.app.data.model.MediaItem

/** What a duplicate pass did, so the cost can be reported honestly. */
data class DuplicateReport(
    val groups: List<DuplicateGroup> = emptyList(),
    /** Items that share a size with at least one other item. */
    val sizeCandidates: Int = 0,
    /** Items that were fully hashed (sample collisions only). */
    val fullHashes: Int = 0,
) {
    val duplicateFiles: Int get() = groups.sumOf { it.items.size - 1 }
    val wastedBytes: Long get() = groups.sumOf { it.wastedBytes }

    /** Full hashes the sampled pass made unnecessary. */
    val fullHashesAvoided: Int get() = (sizeCandidates - fullHashes).coerceAtLeast(0)
}

/**
 * Finds byte-identical files in three passes, cheapest first:
 *
 *  1. group by size — a file with a unique size cannot have a duplicate,
 *  2. compare a sampled hash (length + first and last 64 KB) within each size group,
 *  3. fully hash only what still collides, to confirm.
 *
 * Only step 3 can declare a duplicate, so the cheaper passes can never produce a
 * wrong answer — they only avoid work. Previously every same-size candidate was
 * fully hashed, which is slow on a library with many large videos.
 *
 * Both hashers are injected as functions, so the whole decision path is unit
 * testable without a device.
 */
class DuplicateDetector(
    private val sampledHasher: suspend (MediaItem) -> String?,
    private val fullHasher: suspend (MediaItem) -> String?,
) {

    /** Groups items whose sizes match — the candidate set for hashing. */
    fun sameSizeCandidates(items: List<MediaItem>): Map<Long, List<MediaItem>> =
        items.filter { it.sizeBytes > 0 }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }

    suspend fun findDuplicates(items: List<MediaItem>): DuplicateReport {
        val bySize = sameSizeCandidates(items)
        if (bySize.isEmpty()) return DuplicateReport()

        val groups = ArrayList<DuplicateGroup>()
        var sizeCandidates = 0
        var fullHashes = 0

        for ((size, candidates) in bySize) {
            sizeCandidates += candidates.size

            // Pass 2: the cheap sample, within this size group only.
            val bySample = HashMap<String, MutableList<MediaItem>>()
            for (item in candidates) {
                val sample = sampledHasher(item) ?: continue
                bySample.getOrPut(sample) { mutableListOf() }.add(item)
            }

            // Pass 3: only what still collides is fully hashed.
            for ((sample, stillSame) in bySample) {
                if (stillSame.size < 2) continue
                val byFull = HashMap<String, MutableList<MediaItem>>()
                for (item in stillSame) {
                    fullHashes++
                    val hash = fullHasher(item) ?: continue
                    byFull.getOrPut(hash) { mutableListOf() }.add(item)
                }
                byFull.forEach { (hash, duplicates) ->
                    if (duplicates.size > 1) {
                        val wasted = duplicates.drop(1).sumOf { it.sizeBytes }
                        groups += DuplicateGroup(
                            key = "$size:$sample:$hash",
                            items = duplicates,
                            wastedBytes = wasted,
                        )
                    }
                }
            }
        }

        return DuplicateReport(
            groups = groups.sortedByDescending { it.wastedBytes },
            sizeCandidates = sizeCandidates,
            fullHashes = fullHashes,
        )
    }
}
