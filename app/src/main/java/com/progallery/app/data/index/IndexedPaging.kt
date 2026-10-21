package com.progallery.app.data.index

import com.progallery.app.data.model.MediaItem

/**
 * The window-producing half of the merged gallery, kept separate from the paging library.
 *
 * It is separated because it is the part that can be wrong in a way nobody notices: an item out
 * of order, duplicated or skipped at a window boundary looks like a stutter while scrolling, not
 * like a bug. As a plain function taking a batch reader, every boundary can be exercised in a
 * unit test — which the `PagingSource` around it, needing a real cursor, cannot be.
 */
object IndexedPaging {

    /** One window of the system's index, with how many rows it consumed. */
    data class Batch(val items: List<MediaItem>, val rowsRead: Int, val exhausted: Boolean)

    /** Where to resume: where the items in hand began, how many were taken, and how far into the app's index. */
    data class Cursor(val storeRow: Int, val consumedFromBatch: Int, val indexedAt: Int) {
        companion object {
            val START = Cursor(0, 0, 0)
        }
    }

    data class Window(val items: List<MediaItem>, val next: Cursor?)

    /**
     * Produces the next [loadSize] items of the merged order.
     *
     * **Why the system's side is read ahead before anything is merged.** The merge decides an
     * item's position by comparing the next item of each side, so it can only be right if it can
     * see far enough into both. Merging against a single batch was wrong: the batch ended, the
     * merge fell back to the app's index alone, and an item from the *next* batch — newer than
     * the one just emitted — arrived afterwards, out of order. Reading ahead until the system's
     * side holds at least a window's worth (or the cursor is finished) is what makes the order
     * correct at every boundary, and the unit tests page through at every window size to prove
     * it.
     *
     * The read-ahead is deterministic, so resuming from a cursor re-reads the same items and
     * skips the same number — no hidden buffer that a refresh could leave stale.
     */
    suspend fun load(
        cursor: Cursor,
        loadSize: Int,
        candidates: List<MediaItem>,
        comparator: Comparator<MediaItem>,
        readBatch: suspend (row: Int, size: Int) -> Batch,
    ): Window {
        // ---- read the system's side ahead ----
        val inHand = ArrayList<MediaItem>()
        var rowAfter = cursor.storeRow
        var exhausted = false
        while (inHand.size - cursor.consumedFromBatch < loadSize && !exhausted) {
            val batch = readBatch(rowAfter, loadSize)
            rowAfter += batch.rowsRead
            inHand += batch.items
            exhausted = batch.exhausted
        }
        var takenFromStore = cursor.consumedFromBatch.coerceAtMost(inHand.size)

        // ---- merge ----
        val out = ArrayList<MediaItem>(loadSize)
        var indexedAt = cursor.indexedAt
        while (out.size < loadSize) {
            val haveStore = takenFromStore < inHand.size
            val remainingIndexed = candidates.drop(indexedAt)
            if (!haveStore && remainingIndexed.isEmpty()) break

            val step = IndexMerge.merge(
                store = if (haveStore) inHand.drop(takenFromStore) else emptyList(),
                indexed = remainingIndexed,
                want = loadSize - out.size,
                comparator = comparator,
            )
            if (step.items.isEmpty()) break

            out += step.items
            takenFromStore += step.storeConsumed
            indexedAt += step.indexedConsumed
        }

        val moreStore = takenFromStore < inHand.size || !exhausted
        val moreIndexed = indexedAt < candidates.size

        return Window(
            items = out,
            next = when {
                !moreStore && !moreIndexed -> null
                // Everything in hand has been taken, so the next read starts after it.
                takenFromStore >= inHand.size -> Cursor(rowAfter, 0, indexedAt)
                // Some is left, so resuming re-reads from where this read began and skips it.
                else -> Cursor(cursor.storeRow, takenFromStore, indexedAt)
            },
        )
    }
}
