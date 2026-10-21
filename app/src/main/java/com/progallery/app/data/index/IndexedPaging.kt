package com.progallery.app.data.index

import com.progallery.app.data.model.MediaItem

/**
 * The window-producing half of the merged gallery, kept separate from the paging library.
 *
 * It is separated because it is the part that can be wrong in a way nobody notices: an item
 * duplicated or skipped at a page boundary looks like a stutter while scrolling, not like a bug.
 * As a plain function taking a batch reader, every boundary can be exercised in a unit test —
 * which the `PagingSource` around it, needing a real cursor, cannot be.
 */
object IndexedPaging {

    /** One window of the system's index, with how many rows it consumed. */
    data class Batch(val items: List<MediaItem>, val rowsRead: Int, val exhausted: Boolean)

    /** Where to resume. See [IndexedPagingSource.Cursor]. */
    data class Cursor(val storeRow: Int, val consumedFromBatch: Int, val indexedAt: Int) {
        companion object {
            val START = Cursor(0, 0, 0)
        }
    }

    data class Window(val items: List<MediaItem>, val next: Cursor?)

    /**
     * Produces the next [loadSize] items of the merged order.
     *
     * The system's side is read through [readBatch], which is called at most a few times per
     * window and always with a row offset — so a cursor that cannot be rewound is enough.
     */
    suspend fun load(
        cursor: Cursor,
        loadSize: Int,
        candidates: List<MediaItem>,
        comparator: Comparator<MediaItem>,
        readBatch: suspend (row: Int, size: Int) -> Batch,
    ): Window {
        val out = ArrayList<MediaItem>(loadSize)
        var storeRow = cursor.storeRow
        var consumedFromBatch = cursor.consumedFromBatch
        var indexedAt = cursor.indexedAt
        var batch = readBatch(storeRow, loadSize)
        var exhausted = false

        while (out.size < loadSize) {
            // The batch is used up: move to the next one.
            if (consumedFromBatch >= batch.items.size && !exhausted) {
                if (batch.exhausted) {
                    exhausted = true
                } else {
                    storeRow += batch.rowsRead
                    consumedFromBatch = 0
                    batch = readBatch(storeRow, loadSize)
                }
            }

            val haveStore = !exhausted && consumedFromBatch < batch.items.size
            val remainingIndexed = candidates.drop(indexedAt)
            if (!haveStore && remainingIndexed.isEmpty()) break

            val step = IndexMerge.merge(
                store = if (haveStore) batch.items.drop(consumedFromBatch) else emptyList(),
                indexed = remainingIndexed,
                want = loadSize - out.size,
                comparator = comparator,
            )
            if (step.items.isEmpty()) break

            out += step.items
            consumedFromBatch += step.storeConsumed
            indexedAt += step.indexedConsumed
        }

        val moreStore = !exhausted && consumedFromBatch < batch.items.size
        val moreIndexed = indexedAt < candidates.size
        return Window(
            items = out,
            next = if (moreStore || moreIndexed) Cursor(storeRow, consumedFromBatch, indexedAt) else null,
        )
    }
}
