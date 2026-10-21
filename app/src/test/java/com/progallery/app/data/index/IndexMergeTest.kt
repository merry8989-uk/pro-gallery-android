package com.progallery.app.data.index

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge decides what the grid shows and in what order. An error here is invisible until the
 * grid has been scrolled, which is exactly the kind of fault a device is bad at finding — so
 * every boundary is pinned here instead.
 */
class IndexMergeTest {

    /** Distinct ids give distinct keys, exactly as on a device. */
    private var nextId = 1L

    private fun item(
        name: String,
        date: Long = 0,
        size: Long = 0,
        type: MediaType = MediaType.IMAGE,
        source: MediaSource = MediaSource.MEDIA_STORE,
        /** Pass the same id twice to build two records of the same file. */
        id: Long = 0L,
    ): MediaItem {
        val resolved = if (id != 0L) id else nextId++
        return TestMedia.item(id = resolved, name = name, date = date, size = size, type = type)
            .copy(source = source)
    }

    private val newest = IndexMerge.comparator(SortOrder.DATE_NEWEST)

    private fun names(items: List<MediaItem>) = items.map { it.displayName }

    // ---- the ordinary case ----

    @Test
    fun twoOrderedListsComeOutAsOneOrderedList() {
        val store = listOf(item("s1", date = 500), item("s2", date = 300))
        val indexed = listOf(item("i1", date = 400), item("i2", date = 200))

        val result = IndexMerge.merge(store, indexed, want = 10, comparator = newest)

        assertEquals(listOf("s1", "i1", "s2", "i2"), names(result.items))
    }

    @Test
    fun anExtraFileTakenTodayComesBeforeAnOldStoreFile() {
        // The reason this is a merge and not an append: a WhatsApp photo from this morning must
        // not sort below a photo from 2014.
        val store = listOf(item("old", date = 1))
        val indexed = listOf(item("today", date = 9_999))

        val result = IndexMerge.merge(store, indexed, want = 10, comparator = newest)

        assertEquals(listOf("today", "old"), names(result.items))
    }

    // ---- the window ----

    @Test
    fun onlyTheRequestedNumberOfItemsIsTaken() {
        val store = (1..10).map { item("s$it", date = (100 - it).toLong()) }
        val result = IndexMerge.merge(store, emptyList(), want = 3, comparator = newest)
        assertEquals(3, result.items.size)
        assertEquals(3, result.storeConsumed)
    }

    @Test
    fun theConsumedCountsAdvanceBothCursorsCorrectly() {
        val store = listOf(item("s1", date = 500), item("s2", date = 300))
        val indexed = listOf(item("i1", date = 400), item("i2", date = 200))

        val first = IndexMerge.merge(store, indexed, want = 2, comparator = newest)
        assertEquals(listOf("s1", "i1"), names(first.items))
        assertEquals(1, first.storeConsumed)
        assertEquals(1, first.indexedConsumed)

        // The next window continues exactly where the first stopped.
        val second = IndexMerge.merge(
            store.drop(first.storeConsumed),
            indexed.drop(first.indexedConsumed),
            want = 2,
            comparator = newest,
        )
        assertEquals(listOf("s2", "i2"), names(second.items))
    }

    @Test
    fun pagingThroughInSmallWindowsMatchesOneBigWindow() {
        // The property that matters most: scrolling must not change the list.
        val store = (1..7).map { item("s$it", date = (100 - it * 3).toLong()) }
        val indexed = (1..5).map { item("i$it", date = (100 - it * 4).toLong()) }

        val whole = IndexMerge.merge(store, indexed, want = 100, comparator = newest)

        val paged = mutableListOf<MediaItem>()
        var storeAt = 0
        var indexedAt = 0
        while (paged.size < whole.items.size) {
            val step = IndexMerge.merge(
                store.drop(storeAt),
                indexed.drop(indexedAt),
                want = 3,
                comparator = newest,
            )
            if (step.items.isEmpty()) break
            paged += step.items
            storeAt += step.storeConsumed
            indexedAt += step.indexedConsumed
        }

        assertEquals(names(whole.items), names(paged))
    }

    // ---- one source exhausted ----

    @Test
    fun anEmptyIndexGivesBackTheStoreList() {
        val store = listOf(item("s1", date = 2), item("s2", date = 1))
        val result = IndexMerge.merge(store, emptyList(), want = 10, comparator = newest)
        assertEquals(listOf("s1", "s2"), names(result.items))
        assertEquals(2, result.storeConsumed)
        assertEquals(0, result.indexedConsumed)
    }

    @Test
    fun anEmptyStoreGivesBackTheIndexedList() {
        val indexed = listOf(item("i1", date = 2), item("i2", date = 1))
        val result = IndexMerge.merge(emptyList(), indexed, want = 10, comparator = newest)
        assertEquals(listOf("i1", "i2"), names(result.items))
    }

    @Test
    fun bothEmptyGivesNothing() {
        val result = IndexMerge.merge(emptyList(), emptyList(), want = 10, comparator = newest)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.storeConsumed)
        assertEquals(0, result.indexedConsumed)
    }

    @Test
    fun newerIndexedItemsAreEmittedBeforeOlderStoreItems() {
        val store = listOf(item("s1", date = 1))
        val indexed = listOf(item("i1", date = 900), item("i2", date = 800))
        val result = IndexMerge.merge(store, indexed, want = 10, comparator = newest)
        assertEquals(listOf("i1", "i2", "s1"), names(result.items))
    }

    // ---- duplicates ----

    @Test
    fun aFileBothSourcesKnowAppearsOnce() {
        // The system's copy wins, because it carries the row id that favourites, hidden items
        // and the viewer's swipe list are all keyed on. Emitting the app's own copy instead
        // would silently break those.
        val store = listOf(item("same", date = 5, source = MediaSource.MEDIA_STORE, id = 42L))
        val indexed = listOf(item("same", date = 5, source = MediaSource.DEEP_SCAN, id = 42L))

        val result = IndexMerge.merge(store, indexed, want = 10, comparator = newest)

        assertEquals(1, result.items.size)
        assertEquals(MediaSource.MEDIA_STORE, result.items.single().source)
    }

    @Test
    fun aDuplicateDoesNotMakeThePageShort() {
        // The duplicate is skipped without being counted against the page size, so a full page
        // is still returned and the grid has no gap in it.
        val store = listOf(item("a", date = 9), item("dup", date = 8, id = 77L))
        val indexed = listOf(item("dup", date = 8, id = 77L), item("b", date = 7))

        val result = IndexMerge.merge(store, indexed, want = 3, comparator = newest)

        assertEquals(3, result.items.size)
        assertEquals(listOf("a", "dup", "b"), names(result.items))
    }

    @Test
    fun aFileOnlyTheIndexKnowsIsEmitted() {
        val indexed = listOf(item("only", date = 1, source = MediaSource.DEEP_SCAN))
        val result = IndexMerge.merge(emptyList(), indexed, want = 5, comparator = newest)
        assertEquals(MediaSource.DEEP_SCAN, result.items.single().source)
    }

    // ---- the ordering itself ----

    @Test
    fun theOrderIsTotalSoTiesCannotShuffle() {
        // Same date, different files: the tie-break must give the same order every time, or the
        // list would reshuffle as the user scrolled.
        val store = listOf(item("b", date = 5), item("a", date = 5))
        val indexed = listOf(item("c", date = 5))

        // Both inputs must be sorted, as they are in the app: the system's side comes from a
        // sorted query and the app's side is sorted with the same comparator. Feeding them in
        // a different *order* is fine; feeding them unsorted is not what the merge is for.
        val first = IndexMerge.merge(store, indexed, 10, newest)
        val second = IndexMerge.merge(store.reversed().sortedWith(newest), indexed, 10, newest)

        assertEquals(names(first.items), names(second.items))
    }

    @Test
    fun everySortOrderIsSupported() {
        val store = listOf(item("b", date = 1, size = 10))
        val indexed = listOf(item("a", date = 2, size = 20))

        fun merged(sort: SortOrder) =
            names(IndexMerge.merge(store, indexed, 10, IndexMerge.comparator(sort)).items)

        // "a" is newer and larger; "b" is older and smaller, so newest/largest put "a" first and
        // their opposites put "b" first.
        assertEquals(listOf("a", "b"), merged(SortOrder.DATE_NEWEST))
        assertEquals(listOf("b", "a"), merged(SortOrder.DATE_OLDEST))
        assertEquals(listOf("a", "b"), merged(SortOrder.NAME_ASC))
        assertEquals(listOf("b", "a"), merged(SortOrder.NAME_DESC))
        assertEquals(listOf("a", "b"), merged(SortOrder.SIZE_DESC))
        assertEquals(listOf("b", "a"), merged(SortOrder.SIZE_ASC))
    }

    @Test
    fun aZeroSizedWindowTakesNothing() {
        val result = IndexMerge.merge(listOf(item("a", date = 1)), emptyList(), 0, newest)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.storeConsumed)
    }
}
