package com.progallery.app.data.storage

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the space went, checked as arithmetic.
 *
 * The cases were run against a faithful mirror before CI saw them. The one that matters most is
 * [theRowsAddUpToTheTotal]: a summary whose own rows do not sum to its own total is a summary
 * nobody can check, and it would look like an answer.
 */
class StorageSummaryTest {

    private fun item(id: Long, kind: MediaType, size: Long, path: String?) =
        TestMedia.item(id = id, name = "file_$id", size = size, type = kind).copy(relativePath = path)

    private val ordinary = listOf(
        item(1, MediaType.IMAGE, 500, "DCIM/Camera/a.jpg"),
        item(2, MediaType.IMAGE, 300, "DCIM/Screenshots/b.png"),
        item(3, MediaType.VIDEO, 900, "DCIM/Camera/c.mp4"),
        item(4, MediaType.AUDIO, 100, "Music/d.mp3"),
        item(5, MediaType.DOCUMENT, 200, "Download/e.pdf"),
        item(6, MediaType.OTHER, 50, null),
        item(7, MediaType.OTHER, 25, "loose.txt"),
    )

    @Test
    fun theKindsAreCountedAndSortedBySize() {
        val summary = StorageSummaryBuilder.build(ordinary)
        assertEquals(2075L, summary.totalBytes)
        assertEquals(7, summary.totalCount)
        // Largest first, so the answer to "what is using my space" is the first line.
        assertEquals(
            listOf(MediaType.VIDEO, MediaType.IMAGE, MediaType.DOCUMENT, MediaType.AUDIO, MediaType.OTHER),
            summary.byKind.map { it.kind },
        )
        assertEquals(2, summary.byKind.first { it.kind == MediaType.IMAGE }.count)
        assertEquals(800L, summary.byKind.first { it.kind == MediaType.IMAGE }.bytes)
    }

    @Test
    fun theRowsAddUpToTheTotal() {
        val summary = StorageSummaryBuilder.build(ordinary)
        assertEquals(
            "the kinds must sum to the total, or the summary is uncheckable",
            summary.totalBytes,
            summary.byKind.sumOf { it.bytes },
        )
        assertEquals(
            "and so must the folders, which is why a file with no path still has one",
            summary.totalBytes,
            summary.largestFolders.sumOf { it.bytes },
        )
        assertEquals(summary.totalCount, summary.byKind.sumOf { it.count })
        assertEquals(summary.totalCount, summary.largestFolders.sumOf { it.count })
    }

    @Test
    fun aKindWithNothingIsAbsentRatherThanAZeroRow() {
        val summary = StorageSummaryBuilder.build(listOf(item(1, MediaType.IMAGE, 10, "a.jpg")))
        assertEquals(listOf(MediaType.IMAGE), summary.byKind.map { it.kind })
    }

    @Test
    fun foldersAreCountedByTheirTopLevelName() {
        val summary = StorageSummaryBuilder.build(ordinary)
        val dcim = summary.largestFolders.first()
        // DCIM, not DCIM/Camera and DCIM/Screenshots as two separate answers.
        assertEquals("DCIM", dcim.name)
        assertEquals(3, dcim.count)
        assertEquals(1700L, dcim.bytes)
    }

    @Test
    fun aFileWithNoPathAndABareNameBothLandInUnknown() {
        val summary = StorageSummaryBuilder.build(
            listOf(
                item(1, MediaType.IMAGE, 10, "loose.jpg"),
                item(2, MediaType.IMAGE, 20, null),
                item(3, MediaType.IMAGE, 30, "DCIM/x.jpg"),
            ),
        )
        val unknown = summary.largestFolders.first { it.name == StorageSummaryBuilder.UNKNOWN_FOLDER }
        assertEquals(2, unknown.count)
        assertEquals(30L, unknown.bytes)
    }

    @Test
    fun nothingAtAllDoesNotDivideByZero() {
        val summary = StorageSummaryBuilder.build(emptyList())
        assertTrue(summary.isEmpty)
        assertEquals(0L, summary.totalBytes)
        assertTrue(summary.byKind.isEmpty())
        assertTrue(summary.largestFolders.isEmpty())
    }

    @Test
    fun theSharesAreFractionsOfTheWhole() {
        val summary = StorageSummaryBuilder.build(ordinary)
        val video = summary.byKind.first { it.kind == MediaType.VIDEO }
        assertEquals(900f / 2075f, video.fraction, 0.0001f)
        // And they add up, which is what a bar of them would need.
        assertEquals(1f, summary.byKind.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun onlyTheLargestFoldersAreKept() {
        val many = (1..10).map { item(it.toLong(), MediaType.IMAGE, it.toLong(), "folder$it/x.jpg") }
        val summary = StorageSummaryBuilder.build(many, folderLimit = 3)
        assertEquals(3, summary.largestFolders.size)
        assertEquals(listOf("folder10", "folder9", "folder8"), summary.largestFolders.map { it.name })
    }

    @Test
    fun theTrashIsCarriedThroughSeparately() {
        // It is recoverable because the app is holding it, which is a different thing from what the
        // device holds — so it is a field of its own and not folded into the total.
        val summary = StorageSummaryBuilder.build(ordinary, inTrashBytes = 4000, inTrashCount = 3)
        assertEquals(4000L, summary.inTrashBytes)
        assertEquals(3, summary.inTrashCount)
        assertEquals(2075L, summary.totalBytes)
    }
}
