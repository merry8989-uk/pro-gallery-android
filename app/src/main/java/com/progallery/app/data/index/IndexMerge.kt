package com.progallery.app.data.index

import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.SortOrder

/** How much of each source a merge consumed, so the caller can advance its cursors. */
data class MergeResult(
    val items: List<MediaItem>,
    val storeConsumed: Int,
    val indexedConsumed: Int,
)

/**
 * Puts the files the app found itself into the same ordered list as the system's index.
 *
 * **Why this is a merge and not an append.** The gallery is sorted, and the user expects the
 * sort to be the sort. Dropping the extra files at the end would put a photo taken this morning
 * below a photo from 2014 whenever it came from a messaging app. So the two ordered sources are
 * interleaved by the same comparator, and the result reads as one list.
 *
 * **Why both sides are compared with one comparator rather than two queries.** The extra files
 * are few enough to hold in memory, so they can be ordered with exactly the comparator the grid
 * is using — for every sort order, not just the date ones. A second SQL query per sort order
 * would have been three more places to get the ordering subtly different.
 *
 * Pure, and tested hard: an off-by-one here is invisible on screen until the grid has scrolled,
 * which is exactly the kind of fault a device is bad at finding.
 */
object IndexMerge {

    /**
     * The ordering both sources are read in.
     *
     * Ties are broken by [MediaItem.mediaKey], which makes the order **total** — without a
     * tie-breaker, two items with the same date could come back in either order on different
     * calls and the merged list would shuffle as the user scrolled.
     */
    fun comparator(sort: SortOrder): Comparator<MediaItem> = when (sort) {
        SortOrder.DATE_NEWEST -> compareByDescending<MediaItem> { it.dateModifiedSeconds }
        SortOrder.DATE_OLDEST -> compareBy { it.dateModifiedSeconds }
        SortOrder.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        SortOrder.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        SortOrder.SIZE_DESC -> compareByDescending { it.sizeBytes }
        SortOrder.SIZE_ASC -> compareBy { it.sizeBytes }
        SortOrder.TYPE -> compareBy<MediaItem> { it.mediaType.ordinal }.thenBy { it.displayName }
    }.thenBy { it.mediaKey }

    /**
     * Takes the first [want] items of the merged order from two already-sorted lists.
     *
     * Both inputs must be sorted by [comparator]; the extra files are sorted by the caller, and
     * the system's index is read in that order by its own query.
     *
     * An item present in both is emitted **once**, and the system's copy wins — it carries the
     * MediaStore row id, so favourites, hidden items and the viewer's swipe list all keep
     * working on it. Emitting the copy from the app's own index instead would silently break
     * those, because nothing else knows that file by that identity.
     */
    fun merge(
        store: List<MediaItem>,
        indexed: List<MediaItem>,
        want: Int,
        comparator: Comparator<MediaItem>,
    ): MergeResult {
        if (want <= 0) return MergeResult(emptyList(), 0, 0)

        val out = ArrayList<MediaItem>(want)
        val seen = HashSet<String>(want * 2)
        var storeIndex = 0
        var indexedIndex = 0

        while (out.size < want && (storeIndex < store.size || indexedIndex < indexed.size)) {
            val takeStore = when {
                storeIndex >= store.size -> false
                indexedIndex >= indexed.size -> true
                else -> comparator.compare(store[storeIndex], indexed[indexedIndex]) <= 0
            }

            val candidate = if (takeStore) store[storeIndex++] else indexed[indexedIndex++]

            // A file both sources know about appears once. The store's copy is kept, and the
            // duplicate from the index is skipped rather than counted against the page size —
            // otherwise a page would come back short and the grid would show a gap.
            if (seen.add(candidate.mediaKey)) {
                out += candidate
            }
        }

        return MergeResult(out, storeIndex, indexedIndex)
    }
}
