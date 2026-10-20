package com.progallery.app.data.recovery

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tab filtering, search and ordering for recovery results.
 */
class RecoveryQueryTest {

    private fun result(
        name: String,
        type: MediaType = MediaType.IMAGE,
        source: RecoverySource = RecoverySource.MEDIA_STORE,
        confidence: RecoveryConfidence = RecoveryConfidence.HIGH,
        status: RecoveryStatus = RecoveryStatus.AVAILABLE,
        isThumbnail: Boolean = false,
        size: Long = 1000,
        date: Long = 0,
    ) = RecoveryResult(
        id = "$source|$name",
        item = TestMedia.item(
            id = name.hashCode().toLong(),
            name = name,
            size = size,
            date = date,
            type = type,
        ),
        source = source,
        confidence = confidence,
        status = status,
        evidence = "evidence for $name",
        isThumbnail = isThumbnail,
        originLabel = "origin",
    )

    private val results = listOf(
        result("a.jpg", date = 10),
        result("b.mp4", type = MediaType.VIDEO, date = 30),
        result("c.mp3", type = MediaType.AUDIO, date = 20),
        result("d.pdf", type = MediaType.DOCUMENT, date = 5),
        result("e.zip", type = MediaType.ARCHIVE, date = 1),
        result("trashed.jpg", source = RecoverySource.TRASH, date = 99),
        result("found.png", source = RecoverySource.SAF_FOLDER, date = 50),
        result("thumb.jpg", isThumbnail = true, status = RecoveryStatus.PREVIEW_ONLY, date = 60),
    )

    private fun names(tab: RecoveryTab) =
        RecoveryQuery.apply(results, tab, "", RecoverySort.RELEVANCE).map { it.item.displayName }

    @Test
    fun allTabKeepsEverything() {
        assertEquals(8, RecoveryQuery.apply(results, RecoveryTab.ALL, "", RecoverySort.RELEVANCE).size)
    }

    @Test
    fun typeTabsSelectOnlyTheirKind() {
        assertEquals(listOf("a.jpg"), names(RecoveryTab.PHOTOS).filter { it == "a.jpg" })
        assertTrue(RecoveryQuery.apply(results, RecoveryTab.VIDEOS, "", RecoverySort.RELEVANCE)
            .all { it.item.mediaType == MediaType.VIDEO })
        assertTrue(RecoveryQuery.apply(results, RecoveryTab.AUDIO, "", RecoverySort.RELEVANCE)
            .all { it.item.mediaType == MediaType.AUDIO })
        assertTrue(RecoveryQuery.apply(results, RecoveryTab.DOCUMENTS, "", RecoverySort.RELEVANCE)
            .all { it.item.mediaType == MediaType.DOCUMENT })
    }

    @Test
    fun trashAndThumbnailTabsAreAboutProvenanceNotType() {
        assertEquals(listOf("trashed.jpg"), names(RecoveryTab.TRASH))
        assertEquals(listOf("thumb.jpg"), names(RecoveryTab.THUMBNAILS))
    }

    @Test
    fun searchMatchesNameSourceAndEvidence() {
        assertEquals(
            listOf("found.png"),
            RecoveryQuery.apply(results, RecoveryTab.ALL, "found", RecoverySort.NAME)
                .map { it.item.displayName },
        )
        assertTrue(
            RecoveryQuery.apply(results, RecoveryTab.ALL, "Recently Deleted", RecoverySort.NAME)
                .isNotEmpty(),
        )
        assertTrue(
            RecoveryQuery.apply(results, RecoveryTab.ALL, "evidence for a.jpg", RecoverySort.NAME)
                .any { it.item.displayName == "a.jpg" },
        )
    }

    @Test
    fun relevancePutsWhatTheAppHoldsFirstAndThumbnailsLast() {
        val ordered = RecoveryQuery.apply(results, RecoveryTab.ALL, "", RecoverySort.RELEVANCE)
        assertEquals("trashed.jpg", ordered.first().item.displayName)
        assertEquals("thumb.jpg", ordered.last().item.displayName)
    }

    @Test
    fun sortingOptionsBehave() {
        val newest = RecoveryQuery.apply(results, RecoveryTab.ALL, "", RecoverySort.NEWEST)
        assertEquals(99, newest.first().item.dateModifiedSeconds)

        val largest = RecoveryQuery.apply(
            listOf(result("small.jpg", size = 10), result("big.jpg", size = 9000)),
            RecoveryTab.ALL,
            "",
            RecoverySort.LARGEST,
        )
        assertEquals("big.jpg", largest.first().item.displayName)

        val byName = RecoveryQuery.apply(results, RecoveryTab.ALL, "", RecoverySort.NAME)
        assertEquals(byName.map { it.item.displayName }.sorted(), byName.map { it.item.displayName })
    }
}
