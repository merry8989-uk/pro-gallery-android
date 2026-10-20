package com.progallery.app.data.deepscan

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun sameSizeCandidates_onlyReturnsSizeCollisions() {
        val detector = DuplicateDetector { "x" }
        val items = listOf(
            TestMedia.item(id = 1, size = 500),
            TestMedia.item(id = 2, size = 500),
            TestMedia.item(id = 3, size = 999),
        )
        val candidates = detector.sameSizeCandidates(items)
        assertEquals(1, candidates.size)
        assertEquals(2, candidates[500]?.size)
    }

    @Test
    fun findDuplicates_confirmsWithHash() = runTest {
        // Same size but different content -> not duplicates.
        val content = mapOf(1L to "aaa", 2L to "bbb", 3L to "aaa")
        val detector = DuplicateDetector { item -> content[item.id] }
        val items = listOf(
            TestMedia.item(id = 1, size = 500),
            TestMedia.item(id = 2, size = 500),
            TestMedia.item(id = 3, size = 500),
        )
        val groups = detector.findDuplicates(items)
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 3L), groups.first().items.map { it.id }.toSet())
        assertEquals(500L, groups.first().wastedBytes)
    }

    @Test
    fun findDuplicates_ignoresUnreadableFiles() = runTest {
        val detector = DuplicateDetector { item: MediaItem -> if (item.id == 1L) null else "same" }
        val items = listOf(
            TestMedia.item(id = 1, size = 500),
            TestMedia.item(id = 2, size = 500),
            TestMedia.item(id = 3, size = 500),
        )
        val groups = detector.findDuplicates(items)
        assertEquals(1, groups.size)
        assertTrue(groups.first().items.none { it.id == 1L })
    }
}
