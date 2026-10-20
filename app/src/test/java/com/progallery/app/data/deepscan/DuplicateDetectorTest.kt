package com.progallery.app.data.deepscan

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Duplicates must be found, and the expensive full hash must only run on files
 * that the cheap passes could not rule out.
 */
class DuplicateDetectorTest {

    private val sampledCalls = ArrayList<String>()
    private val fullCalls = ArrayList<String>()

    /** The sample agrees when the "sample" column matches; the full hash when "full" does. */
    private fun detector(samples: Map<String, String>, fulls: Map<String, String>) =
        DuplicateDetector(
            sampledHasher = { item ->
                sampledCalls += item.displayName
                samples[item.displayName]
            },
            fullHasher = { item ->
                fullCalls += item.displayName
                fulls[item.displayName]
            },
        )

    private fun item(name: String, size: Long) =
        TestMedia.item(id = name.hashCode().toLong(), name = name, size = size, type = MediaType.IMAGE)

    @Test
    fun uniqueSizesAreNeverHashedAtAll() = runTest {
        val items = listOf(item("a.jpg", 100), item("b.jpg", 200), item("c.jpg", 300))
        val report = detector(emptyMap(), emptyMap()).findDuplicates(items)

        assertTrue(report.groups.isEmpty())
        assertEquals(0, report.sizeCandidates)
        assertEquals(0, report.fullHashes)
        assertTrue("nothing should be read", sampledCalls.isEmpty())
        assertTrue(fullCalls.isEmpty())
    }

    @Test
    fun sameSizeButDifferentSampleIsNotFullyHashed() = runTest {
        val items = listOf(item("a.jpg", 500), item("b.jpg", 500))
        val report = detector(
            samples = mapOf("a.jpg" to "sample-a", "b.jpg" to "sample-b"),
            fulls = mapOf("a.jpg" to "full-a", "b.jpg" to "full-b"),
        ).findDuplicates(items)

        assertTrue(report.groups.isEmpty())
        assertEquals(2, report.sizeCandidates)
        assertEquals("the sample ruled them out", 0, report.fullHashes)
        assertTrue(fullCalls.isEmpty())
        assertEquals(2, report.fullHashesAvoided)
    }

    @Test
    fun sameSampleButDifferentBytesIsNotADuplicate() = runTest {
        val items = listOf(item("a.jpg", 500), item("b.jpg", 500))
        val report = detector(
            samples = mapOf("a.jpg" to "same", "b.jpg" to "same"),
            fulls = mapOf("a.jpg" to "full-a", "b.jpg" to "full-b"),
        ).findDuplicates(items)

        assertTrue("the full hash is the authority", report.groups.isEmpty())
        assertEquals(2, report.fullHashes)
        assertEquals(0, report.fullHashesAvoided)
    }

    @Test
    fun identicalFilesAreReportedAsDuplicates() = runTest {
        val items = listOf(item("a.jpg", 500), item("b.jpg", 500), item("c.jpg", 500))
        val report = detector(
            samples = mapOf("a.jpg" to "same", "b.jpg" to "same", "c.jpg" to "same"),
            fulls = mapOf("a.jpg" to "full", "b.jpg" to "full", "c.jpg" to "full"),
        ).findDuplicates(items)

        assertEquals(1, report.groups.size)
        assertEquals(3, report.groups.first().items.size)
        assertEquals(2, report.duplicateFiles)
        assertEquals(1000L, report.wastedBytes)
    }

    @Test
    fun aMixedLibraryOnlyFullyHashesWhatStillCollides() = runTest {
        val items = listOf(
            item("unique.jpg", 111),
            item("pairA.jpg", 999),
            item("pairB.jpg", 999),
            item("other1.jpg", 777),
            item("other2.jpg", 777),
        )
        val report = detector(
            samples = mapOf(
                "pairA.jpg" to "pair-sample", "pairB.jpg" to "pair-sample",
                "other1.jpg" to "x", "other2.jpg" to "y",
            ),
            fulls = mapOf("pairA.jpg" to "pair-full", "pairB.jpg" to "pair-full"),
        ).findDuplicates(items)

        assertEquals(1, report.groups.size)
        assertEquals(listOf("pairA.jpg", "pairB.jpg"), report.groups.first().items.map { it.displayName })
        assertEquals(4, report.sizeCandidates)
        assertEquals("only the sample collision was fully hashed", 2, report.fullHashes)
        assertEquals(2, report.fullHashesAvoided)
    }

    @Test
    fun unreadableFilesAreSkippedRatherThanCrashing() = runTest {
        val items = listOf(item("a.jpg", 500), item("b.jpg", 500))
        val report = detector(samples = emptyMap(), fulls = emptyMap()).findDuplicates(items)
        assertTrue(report.groups.isEmpty())
    }

    @Test
    fun zeroSizeFilesAreNotCandidates() = runTest {
        val items = listOf(item("a.jpg", 0), item("b.jpg", 0))
        val report = detector(emptyMap(), emptyMap()).findDuplicates(items)
        assertTrue(report.groups.isEmpty())
        assertEquals(0, report.sizeCandidates)
    }
}
