package com.progallery.app.data.mediastore

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import kotlinx.coroutines.CancellationException

/**
 * Pages the MediaStore index.
 *
 * The index is read in windows of cursor **rows**, classified with the same
 * [MediaStoreDataSource.RowReader] the rest of the app uses, so a paged grid can
 * never disagree with the full list about what a file is. Because a window can hold
 * rows of kinds the caller filtered out, the source keeps asking for windows until
 * the page is full — otherwise a page would arrive nearly empty and the grid would
 * look broken.
 *
 * Keys are row offsets. The source only appends ([PagingSource.LoadResult.Page.prevKey]
 * is null), which is what a cursor can honestly support: there is no stable cursor
 * to page backwards from.
 */
class MediaStorePagingSource(
    private val dataSource: MediaStoreDataSource,
    private val kinds: Set<MediaType>,
    private val sort: SortOrder,
    private val query: String,
    /** Items the user hid are excluded here, so they never enter the pager. */
    private val hiddenKeys: Set<String> = emptySet(),
    /**
     * The size range from settings, so a paged list applies the same rule as every
     * other list. Unreadable sizes are never treated as too small.
     */
    private val sizeFilter: (Long) -> Boolean = { true },
    /**
     * When set, only these items are returned. The Favourites screen needs this:
     * favourites are a Room set, not a MediaStore column, so the filter cannot be
     * expressed in the query.
     */
    private val requiredKeys: Set<String>? = null,
) : PagingSource<Int, MediaItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val startRow = params.key ?: 0
        return try {
            val collected = ArrayList<MediaItem>(params.loadSize)
            var row = startRow
            var exhausted = false

            // Fill the page: keep reading windows until there are enough items or the
            // cursor is finished. Bounded by the cursor, so this always terminates.
            while (collected.size < params.loadSize && !exhausted) {
                val page = dataSource.page(kinds, sort, query, row, params.loadSize)
                page.items
                    .filterNot { it.mediaKey in hiddenKeys }
                    .filter { requiredKeys == null || it.mediaKey in requiredKeys }
                    .filter { sizeFilter(it.sizeBytes) }
                    .forEach { collected += it }
                row += page.rowsRead
                exhausted = page.exhausted || page.rowsRead == 0
            }

            LoadResult.Page(
                data = collected,
                // Append-only: a cursor gives no honest way to page backwards.
                prevKey = null,
                nextKey = if (exhausted) null else row,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    /**
     * A refresh restarts from the top.
     *
     * Row offsets are only meaningful for the query that produced them, so reusing
     * one after a change (a different sort, a new search) would land the user at an
     * arbitrary place.
     */
    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? = null
}
