package com.progallery.app.data.index

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.progallery.app.data.mediastore.MediaStoreDataSource
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import kotlinx.coroutines.CancellationException

/**
 * The gallery's grid, over the system's index **and** the app's own.
 *
 * This is what puts a messaging app's photo among the photos instead of leaving it out: the
 * system's rows and the app's own index are merged into one ordered list, so the grid's sort
 * applies to everything the app can see.
 *
 * The work is done by [IndexedPaging], which is a plain function so its page boundaries can be
 * tested; this class only adapts a MediaStore cursor to it and reports the result to the paging
 * library. Append-only, like the source it wraps: a cursor offers no honest way to page
 * backwards, so no backwards key is offered.
 */
class IndexedPagingSource(
    private val dataSource: MediaStoreDataSource,
    /** The app's own index, already read. Small enough to hold, which is what allows any sort. */
    private val indexed: List<MediaItem>,
    private val kinds: Set<MediaType>,
    private val sort: SortOrder,
    private val query: String,
    private val hiddenKeys: Set<String> = emptySet(),
    private val sizeFilter: (Long) -> Boolean = { true },
    private val requiredKeys: Set<String>? = null,
) : PagingSource<IndexedPaging.Cursor, MediaItem>() {

    override suspend fun load(params: LoadParams<IndexedPaging.Cursor>): LoadResult<IndexedPaging.Cursor, MediaItem> {
        val cursor = params.key ?: IndexedPaging.Cursor.START

        // The app's own index is filtered and sorted with the same comparator the grid uses, so
        // it takes part in whatever order the user chose — not only the date orders.
        val comparator = IndexMerge.comparator(sort)
        val candidates = indexed.asSequence()
            .filter { kinds.isEmpty() || it.mediaType in kinds }
            .filterNot { it.mediaKey in hiddenKeys }
            .filter { requiredKeys == null || it.mediaKey in requiredKeys }
            .filter { sizeFilter(it.sizeBytes) }
            .sortedWith(comparator)
            .toList()

        return try {
            val window = IndexedPaging.load(
                cursor = cursor,
                loadSize = params.loadSize,
                candidates = candidates,
                comparator = comparator,
                readBatch = ::readBatch,
            )
            LoadResult.Page(data = window.items, prevKey = null, nextKey = window.next)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    /** One window of the system's index, filtered exactly as the un-merged source filtered it. */
    private suspend fun readBatch(row: Int, size: Int): IndexedPaging.Batch {
        val page = dataSource.page(kinds, sort, query, row, size)
        return IndexedPaging.Batch(
            items = page.items
                .filterNot { it.mediaKey in hiddenKeys }
                .filter { requiredKeys == null || it.mediaKey in requiredKeys }
                .filter { sizeFilter(it.sizeBytes) },
            rowsRead = page.rowsRead,
            exhausted = page.exhausted || page.rowsRead == 0,
        )
    }

    override fun getRefreshKey(state: PagingState<IndexedPaging.Cursor, MediaItem>): IndexedPaging.Cursor? = null
}
