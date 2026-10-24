package com.progallery.app.data.tree

import com.progallery.app.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tree, checked as a shape rather than eyeballed on a device.
 *
 * The cases were run against a faithful mirror of this logic before CI saw them, which is how the
 * eight below were chosen: each one is a way a path can surprise you.
 */
class FileTreeTest {

    private fun item(id: Long, name: String, size: Long, path: String?) =
        TestMedia.item(id = id, name = name, size = size).copy(relativePath = path)

    @Test
    fun aNestedPathBuildsNestedFolders() {
        val rows = FileTree.rows(
            listOf(
                item(1, "b.jpg", 100, "DCIM/Camera/b.jpg"),
                item(2, "a.jpg", 200, "DCIM/Camera/a.jpg"),
            ),
        )
        assertEquals(
            listOf("DCIM" to 0, "Camera" to 1, "a.jpg" to 2, "b.jpg" to 2),
            rows.map { it.node.name to it.depth },
        )
    }

    @Test
    fun aFolderCarriesEverythingUnderIt() {
        val tree = FileTree.build(
            listOf(
                item(1, "b.jpg", 100, "DCIM/Camera/b.jpg"),
                item(2, "a.jpg", 200, "DCIM/Camera/a.jpg"),
                item(3, "s.png", 50, "DCIM/Screenshots/s.png"),
            ),
        )
        val dcim = tree.single() as TreeNode.Folder
        // Not 0 and not the 0 files directly inside it: the whole of DCIM.
        assertEquals(3, dcim.itemCount)
        assertEquals(350L, dcim.sizeBytes)
    }

    @Test
    fun foldersComeBeforeFilesAndBothAreAlphabetical() {
        val names = FileTree.rows(
            listOf(
                item(1, "zebra.jpg", 1, "root/zebra.jpg"),
                item(2, "Apple.jpg", 1, "root/Apple.jpg"),
                item(3, "Mango.jpg", 1, "root/Mango.jpg"),
                item(4, "x.jpg", 1, "root/banana/x.jpg"),
            ),
        ).map { it.node.name }
        // The folder first, then the files case-insensitively — not the order they arrived in.
        assertEquals(listOf("root", "banana", "x.jpg", "Apple.jpg", "Mango.jpg", "zebra.jpg"), names)
    }

    @Test
    fun aFileWithNoPathGoesToTheRootRatherThanIntoAFolderCalledNull() {
        val rows = FileTree.rows(listOf(item(1, "orphan.jpg", 7, null)))
        assertEquals(1, rows.size)
        assertEquals("orphan.jpg", rows.single().node.name)
        assertEquals(0, rows.single().depth)
        assertTrue("it must be a file", rows.single().node is TreeNode.File)
    }

    @Test
    fun aBlankPathIsTreatedTheSameWay() {
        val rows = FileTree.rows(listOf(item(1, "x.jpg", 1, "   ")))
        assertEquals("x.jpg", rows.single().node.name)
        assertEquals(0, rows.single().depth)
    }

    @Test
    fun anEmptySegmentIsDroppedRatherThanBecomingANamelessFolder() {
        val rows = FileTree.rows(listOf(item(1, "c.jpg", 9, "a//b/c.jpg")))
        assertEquals(listOf("a" to 0, "b" to 1, "c.jpg" to 2), rows.map { it.node.name to it.depth })
    }

    @Test
    fun aPathThatIsOnlyANameSitsAtTheRoot() {
        val rows = FileTree.rows(listOf(item(1, "photo.jpg", 3, "photo.jpg")))
        assertEquals(listOf("photo.jpg" to 0), rows.map { it.node.name to it.depth })
    }

    @Test
    fun deepNestingKeepsEveryPathRight() {
        val rows = FileTree.rows(listOf(item(1, "deep.jpg", 1, "a/b/c/d/e/deep.jpg")))
        assertEquals(
            listOf("a", "a/b", "a/b/c", "a/b/c/d", "a/b/c/d/e", "a/b/c/d/e/deep.jpg"),
            rows.map { it.node.path },
        )
        assertEquals(5, rows.last().depth)
    }

    @Test
    fun nothingInNothingOut() {
        assertTrue(FileTree.rows(emptyList()).isEmpty())
        assertTrue(FileTree.build(emptyList()).isEmpty())
    }

    @Test
    fun twoFilesWithTheSameNameInDifferentFoldersStayApart() {
        val rows = FileTree.rows(
            listOf(
                item(1, "photo.jpg", 10, "A/photo.jpg"),
                item(2, "photo.jpg", 20, "B/photo.jpg"),
            ),
        )
        // The folder, then its file, then the next folder and its file — the files are in the rows
        // too, which the first version of this test forgot.
        assertEquals(listOf("A", "photo.jpg", "B", "photo.jpg"), rows.map { it.node.name })
        // The paths differ, which is what the key of a list row uses — same name, different row.
        assertEquals(
            listOf("A/photo.jpg", "B/photo.jpg"),
            rows.filter { it.node is TreeNode.File }.map { it.node.path },
        )
    }
}
