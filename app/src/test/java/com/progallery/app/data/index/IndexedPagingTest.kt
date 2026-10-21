package com.progallery.app.data.index

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaSource
import com.progallery.app.data.model.SortOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merged grid's windowing.
 *
 * The fault this guards against is quiet: an item duplicated or dropped where one window meets
 * the next looks like a stutter while scrolling, not like a bug. So the property that matters is
 * checked directly — **paging through in small windows must produce exactly the list one big
 * window would**.
 */
class IndexedPagingTest {

    private var nextId = 1L

    private fun item(name: String, date: Long, source: MediaSource = MediaSource.MEDIA_STORE) =
        TestMedia.item(id = nextId++, name = name, date = date).copy(source = source)

    private val newest = IndexMerge.comparator(SortOrder.DATE_NEWEST)

    /**
     * Stands in for a MediaStore cursor.
     *
     * Items named `hidden-…` are rows the caller's filter rejects: they are counted in
     * [IndexedPaging.Batch.rowsRead] but not returned, exactly as a real cursor behaves. That is
     * the case a naive implementation gets wrong, so every test goes through it.
     */
    private class FakeCursor(rows: List<MediaItem>, val rowsPerRead: Int) {

        /**
         * Sorted, because that is what a real cursor returns: the query has an ORDER BY. A fake
         * that returned rows in list order would fail the merge for a reason the app never has.
         */
        private val ordered = rows.sortedWith(IndexMerge.comparator(SortOrder.DATE_NEWEST))
        private val rows: List<MediaItem> get() = ordered
        var reads = 0
            private set

        suspend fun read(row: Int, size: Int): IndexedPaging.Batch {
            reads++
            val window = ordered.drop(row).take(rowsPerRead.coerceAtMost(size))
            val visible = window.filterNot { it.displayName.startsWith("hidden-") }
            return IndexedPaging.Batch(
                items = visible,
                rowsRead = window.size,
                exhausted = window.size < rowsPerRead.coerceAtMost(size) || row + window.size >= ordered.size,
            )
        }
    }

    private fun paged(
        cursor: FakeCursor,
        indexed: List<MediaItem>,
        loadSize: Int,
    ): List<MediaItem> = runBlocking {
        val out = mutableListOf<MediaItem>()
        var key: IndexedPaging.Cursor? = IndexedPaging.Cursor.START
        var guard = 0
        while (key != null && guard++ < 500) {
            val window = IndexedPaging.load(key, loadSize, indexed, newest, cursor::read)
            out += window.items
            key = window.next
        }
        out
    }

    private fun oneWindow(cursor: FakeCursor, indexed: List<MediaItem>) = runBlocking {
        IndexedPaging.load(IndexedPaging.Cursor.START, 10_000, indexed, newest, cursor::read)
    }

    // ---- the property ----

    @Test
    fun pagingThroughInSmallWindowsMatchesOneBigWindow() {
        val rows = (1..40).map { item("s$it", date = (1_000 - it * 10).toLong()) }
        val indexed = (1..15).map { item("i$it", date = (1_000 - it * 7).toLong(), MediaSource.DEEP_SCAN) }

        val whole = oneWindow(FakeCursor(rows, 10), indexed)
        val streamed = paged(FakeCursor(rows, 10), indexed, loadSize = 3)

        assertEquals(whole.items.map { it.displayName }, streamed.map { it.displayName })
    }

    @Test
    fun filteredOutRowsDoNotCauseAnItemToBeSkippedOrDuplicated() {
        // Every third row is rejected by the caller's filter.
        val rows = (1..30).map { index ->
            if (index % 3 == 0) item("hidden-$index", date = (1_000 - index * 10).toLong())
            else item("s$index", date = (1_000 - index * 10).toLong())
        }
        val indexed = (1..10).map { item("i$it", date = (1_000 - it * 13).toLong(), MediaSource.DEEP_SCAN) }

        val whole = oneWindow(FakeCursor(rows, 7), indexed)
        val streamed = paged(FakeCursor(rows, 7), indexed, loadSize = 2)

        assertEquals(whole.items.map { it.displayName }, streamed.map { it.displayName })
        assertEquals("no rejected row may be shown", 0, streamed.count { it.displayName.startsWith("hidden-") })
        assertEquals("nothing may be duplicated", streamed.size, streamed.map { it.mediaKey }.toSet().size)
    }

    @Test
    fun manySmallWindowsAreStillExactlyRight() {
        // Load size 1 is the harshest case: every item is its own window.
        val rows = (1..12).map { item("s$it", date = (500 - it).toLong()) }
        val indexed = (1..12).map { item("i$it", date = (500 - it * 2).toLong(), MediaSource.DEEP_SCAN) }

        val whole = oneWindow(FakeCursor(rows, 4), indexed)
        val streamed = paged(FakeCursor(rows, 4), indexed, loadSize = 1)

        assertEquals(whole.items.map { it.displayName }, streamed.map { it.displayName })
    }

    // ---- the ordering ----

    @Test
    fun theMergedOrderIsNewestFirstAcrossBothSources() {
        val rows = listOf(item("store-old", date = 10))
        val indexed = listOf(item("index-new", date = 900, MediaSource.DEEP_SCAN))

        val window = oneWindow(FakeCursor(rows, 10), indexed)

        assertEquals(listOf("index-new", "store-old"), window.items.map { it.displayName })
    }

    @Test
    fun aFileBothSourcesKnowAppearsOnce() {
        val shared = TestMedia.item(id = 999, name = "shared", date = 50)
        val rows = listOf(shared)
        val indexed = listOf(shared.copy(source = MediaSource.DEEP_SCAN))

        val window = oneWindow(FakeCursor(rows, 10), indexed)

        assertEquals(1, window.items.size)
        assertEquals(MediaSource.MEDIA_STORE, window.items.single().source)
    }

    @Test
    fun aDuplicateAcrossAWindowBoundaryIsStillShownOnce() {
        // The duplicate is the last of one window and the first of the next source's list.
        val shared = TestMedia.item(id = 555, name = "shared", date = 500)
        val rows = (1..4).map { item("s$it", date = (400 - it).toLong()) }
        val indexed = listOf(shared.copy(source = MediaSource.DEEP_SCAN)) +
            (1..4).map { item("i$it", date = (300 - it).toLong(), MediaSource.DEEP_SCAN) }

        val streamed = paged(FakeCursor(rows + shared, 2), indexed, loadSize = 2)

        assertEquals(1, streamed.count { it.mediaKey == shared.mediaKey })
    }

    // ---- exhaustion and edges ----

    @Test
    fun theEndOfTheListHasNoNextKey() {
        val window = oneWindow(FakeCursor(listOf(item("only", date = 1)), 10), emptyList())
        assertNull(window.next)
    }

    @Test
    fun moreStoreRowsMeansThereIsANextKey() {
        val rows = (1..20).map { item("s$it", date = (100 - it).toLong()) }
        val window = runBlocking {
            IndexedPaging.load(IndexedPaging.Cursor.START, 5, emptyList(), newest, FakeCursor(rows, 5)::read)
        }
        assertEquals(5, window.items.size)
        assertTrue("there is more to read", window.next != null)
    }

    @Test
    fun anEmptyStoreStillPagesTheIndexedSide() {
        val indexed = (1..7).map { item("i$it", date = (100 - it).toLong(), MediaSource.DEEP_SCAN) }
        val streamed = paged(FakeCursor(emptyList(), 10), indexed, loadSize = 3)
        assertEquals(7, streamed.size)
        assertEquals(indexed.map { it.displayName }, streamed.map { it.displayName })
    }

    @Test
    fun bothSidesEmptyProducesNothing() {
        val window = oneWindow(FakeCursor(emptyList(), 10), emptyList())
        assertTrue(window.items.isEmpty())
        assertNull(window.next)
    }

    @Test
    fun theCursorIsOnlyAdvancedAsFarAsTheItemsTaken() {
        val rows = (1..10).map { item("s$it", date = (100 - it).toLong()) }
        val window = runBlocking {
            IndexedPaging.load(IndexedPaging.Cursor.START, 3, emptyList(), newest, FakeCursor(rows, 10)::read)
        }
        assertEquals(3, window.items.size)
        assertEquals(3, window.next?.consumedFromBatch)
    }

    @Test
    fun aWindowIsNeverShortWhileAnythingRemains() {
        val rows = (1..25).map { item("s$it", date = (100 - it).toLong()) }
        val indexed = (1..25).map { item("i$it", date = (100 - it * 2).toLong(), MediaSource.DEEP_SCAN) }

        runBlocking {
            var key: IndexedPaging.Cursor? = IndexedPaging.Cursor.START
            val cursor = FakeCursor(rows, 6)
            var total = 0
            var guard = 0
            while (key != null && guard++ < 200) {
                val window = IndexedPaging.load(key, 7, indexed, newest, cursor::read)
                if (window.next != null) {
                    assertEquals("a window with more to come must be full", 7, window.items.size)
                }
                total += window.items.size
                key = window.next
            }
            assertEquals(50, total)
        }
    }

    // ---- the fault this algorithm was rewritten for ----

    @Test
    fun anItemFromTheNextBatchIsNotEmittedOutOfOrder() {
        // The bug, named: a window's worth of the system's side was merged without reading
        // ahead, so when the batch ended the merge fell back to the app's index alone — and an
        // item from the *next* batch, newer than one already emitted, arrived afterwards. The
        // grid would show two neighbours swapped.
        //
        // Here the store has s1 (date 990) and s2 (date 960) with s3 (date 958) in the app's
        // index. With a batch of one row per read, s2 is only visible after s1 is taken.
        val rows = listOf(item("s1", date = 990), item("s2", date = 960))
        val indexed = listOf(item("s3", date = 958, source = MediaSource.DEEP_SCAN))

        val streamed = paged(FakeCursor(rows, 1), indexed, loadSize = 2)

        assertEquals(listOf("s1", "s2", "s3"), streamed.map { it.displayName })
    }

    @Test
    fun theOrderIsRightAtEveryWindowSize() {
        // The property, checked across the sizes the pager actually uses: a first load is twice
        // the page size, and later loads are the page size.
        val rows = (1..30).map { item("s$it", date = (900 - it * 11).toLong()) }
        val indexed = (1..20).map { item("i$it", date = (900 - it * 7).toLong(), MediaSource.DEEP_SCAN) }

        val whole = oneWindow(FakeCursor(rows, 9), indexed).items.map { it.displayName }

        listOf(1, 2, 3, 5, 8, 13, 30).forEach { size ->
            val streamed = paged(FakeCursor(rows, 9), indexed, loadSize = size)
            assertEquals("window size $size", whole, streamed.map { it.displayName })
        }
    }

    @Test
    fun readingAheadDoesNotChangeWhatIsShown() {
        // A bigger read-ahead must not add, drop or reorder anything.
        val rows = (1..18).map { item("s$it", date = (500 - it * 5).toLong()) }
        val indexed = (1..12).map { item("i$it", date = (500 - it * 3).toLong(), MediaSource.DEEP_SCAN) }

        val small = paged(FakeCursor(rows, 4), indexed, loadSize = 3).map { it.displayName }
        val large = paged(FakeCursor(rows, 12), indexed, loadSize = 3).map { it.displayName }

        assertEquals(small, large)
    }
}
