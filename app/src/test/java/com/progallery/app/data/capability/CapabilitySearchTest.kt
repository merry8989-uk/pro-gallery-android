package com.progallery.app.data.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen's search and its counts.
 *
 * A capability screen that disagrees with itself is worse than none, so what "searching" means
 * lives here rather than in the UI.
 */
class CapabilitySearchTest {

    private val all = CapabilityMatrix.all()

    @Test
    fun anEmptyQueryAndNoVerdictKeepEverything() {
        assertEquals(all.size, CapabilityMatrix.search(all).size)
    }

    @Test
    fun anExtensionIsFound() {
        val hits = CapabilityMatrix.search(all, query = "wmv")
        assertTrue(hits.any { it.extension == "wmv" })
    }

    @Test
    fun theReasonIsSearchableToo() {
        // Someone looking for "Windows Media" and someone looking for ".wmv" are asking the same
        // question, and both should find it.
        val hits = CapabilityMatrix.search(all, query = "Windows Media")
        assertTrue("expected the ASF family", hits.any { it.extension == "wmv" })
        assertTrue(hits.all { it.reason?.contains("Windows Media") == true || it.extension.contains("wmv") })
    }

    @Test
    fun theVerdictsOwnWordingIsSearchable() {
        val hits = CapabilityMatrix.search(all, query = "needs another app")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.verdict == Capability.NEEDS_ANOTHER_APP })
    }

    @Test
    fun aVerdictNarrowsTheList() {
        val opens = CapabilityMatrix.search(all, verdict = Capability.OPENS_HERE)
        assertTrue(opens.isNotEmpty())
        assertTrue(opens.all { it.verdict == Capability.OPENS_HERE })
        assertEquals(all.count { it.verdict == Capability.OPENS_HERE }, opens.size)
    }

    @Test
    fun theQueryAndTheVerdictCombine() {
        val hits = CapabilityMatrix.search(all, query = "mp", verdict = Capability.NEEDS_ANOTHER_APP)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.verdict == Capability.NEEDS_ANOTHER_APP })
        assertTrue(hits.all { it.extension.contains("mp") || it.reason?.contains("mp", true) == true })
    }

    @Test
    fun aQueryThatMatchesNothingReturnsNothing() {
        assertTrue(CapabilityMatrix.search(all, query = "qqqqqq").isEmpty())
    }

    @Test
    fun theCountsAddUpToTheWholeList() {
        val counts = CapabilityMatrix.counts(all)
        assertEquals(Capability.entries.size, counts.size)
        assertEquals(all.size, counts.values.sum())
    }

    @Test
    fun everyVerdictInTheListIsCounted() {
        val counts = CapabilityMatrix.counts(all)
        Capability.entries.forEach { verdict ->
            assertEquals(
                "$verdict count",
                all.count { it.verdict == verdict },
                counts[verdict],
            )
        }
    }
}
