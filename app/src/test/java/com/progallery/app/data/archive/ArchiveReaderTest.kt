package com.progallery.app.data.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The archive reader, against archives the test builds itself.
 *
 * The case that matters most is [aZipSlipNameCannotEscape] — the "zip slip" trick, where an entry is
 * called `../../something` so that an extractor joining it to a folder writes outside it. Nothing
 * here trusts a path: only `fileName` is used for writing, and it can never contain a slash.
 */
class ArchiveReaderTest {

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun listOf(bytes: ByteArray) = ArchiveReader.list { ByteArrayInputStream(bytes) }

    @Test
    fun anOrdinaryArchiveIsListed() {
        val entries = listOf(zipOf("a.txt" to "hello", "dir/b.txt" to "world"))
        assertEquals(listOf("a.txt", "dir/b.txt"), entries.map { it.path })
        assertFalse(entries.any { it.isDirectory })
    }

    @Test
    fun directoriesAreKeptSoTheShapeIsVisible() {
        val entries = listOf(zipOf("dir/" to "", "dir/a.txt" to "x"))
        assertTrue("the folder itself should be listed", entries.any { it.path == "dir/" && it.isDirectory })
    }

    @Test
    fun aZipSlipNameCannotEscape() {
        // The entry is called `../../evil.txt`. Its **fileName** is `evil.txt` — no slash, no
        // parent — which is all an extractor is allowed to use for a name.
        val entry = listOf(zipOf("../../evil.txt" to "pwn")).first { it.path.contains("evil") }
        assertEquals("evil.txt", entry.fileName)
        assertFalse("a file name must never contain a slash", entry.fileName.contains('/'))
        assertFalse(entry.fileName.contains(".."))
    }

    @Test
    fun aDeepPathGivesDepthForIndenting() {
        val entries = listOf(zipOf("a/b/c/d.txt" to "x"))
        assertEquals(3, entries.first().depth)
        assertEquals("d.txt", entries.first().fileName)
    }

    @Test
    fun somethingThatIsNotAnArchiveIsReportedInWords() {
        val failure = runCatching { listOf("this is not a zip at all".toByteArray()) }.exceptionOrNull()
        assertTrue(failure is ArchiveReader.Unreadable)
        assertTrue("the message should be readable", failure!!.message.orEmpty().isNotBlank())
    }

    @Test
    fun anArchiveWithTooManyPartsIsRefusedRatherThanOpened() {
        val many = (1..ArchiveReader.MAX_ENTRIES + 5).map { "f$it.txt" to "x" }.toTypedArray()
        val failure = runCatching { listOf(zipOf(*many)) }.exceptionOrNull()
        assertTrue("a zip with too many parts must be refused", failure is ArchiveReader.TooLarge)
    }

    @Test
    fun anEmptyArchiveIsAnArchiveWithNothingInIt() {
        // `PK\x05\x06` is a real, valid, empty zip. It must not be reported as a broken file — the
        // signature is what tells the two apart, because `ZipInputStream` returns no entries for both.
        val empty = zipOf()
        val entries = listOf(empty)
        assertTrue("an empty zip is empty, not broken", entries.isEmpty())
    }

    @Test
    fun aMissingStreamIsAnErrorRatherThanAnEmptyList() {
        val failure = runCatching { ArchiveReader.list { null } }.exceptionOrNull()
        assertTrue(failure is ArchiveReader.Unreadable)
    }
}
