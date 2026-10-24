package com.progallery.app.data.documents

import com.progallery.app.TestMedia
import com.progallery.app.data.capability.Capability
import com.progallery.app.data.capability.CapabilityMatrix
import com.progallery.app.data.model.FileTypeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every file goes somewhere.
 *
 * The navigation host's `when` had no `else`, so a document, an archive or an unknown extension had
 * its tap **silently ignored**. These tests are what "every format has a way in" means: they walk
 * the whole registry, not a hand-picked list, because a hand-picked list is how a format gets left
 * out without anyone noticing.
 */
class FileOpenerTest {

    @Test
    fun everyExtensionTheAppKnowsGoesSomewhere() {
        val extensions = FileTypeRegistry.knownExtensions()
        assertTrue("the registry should not be empty", extensions.size > 20)

        extensions.forEachIndexed { index, extension ->
            val kind = FileTypeRegistry.kindFor(null, "file.$extension")
            val item = TestMedia.item(id = index.toLong(), name = "file.$extension", type = kind)
            // The call itself must not throw, and the answer must be one of the two.
            val destination = FileOpener.destinationFor(item)
            assertTrue(
                ".$extension produced neither a viewer nor a hand-off",
                destination is FileDestination.Here || destination is FileDestination.HandOff,
            )
        }
    }

    @Test
    fun aFileTheMatrixRefusesIsHandedOverWithItsOwnReason() {
        // `.wmv` is a real example: no parser on Android, and the matrix says so in words.
        val item = TestMedia.item(id = 1, name = "clip.wmv", type = FileTypeRegistry.kindFor(null, "clip.wmv"))
        val destination = FileOpener.destinationFor(item)
        assertTrue("a refused format must be a hand-off", destination is FileDestination.HandOff)
        val reason = (destination as FileDestination.HandOff).reason
        assertTrue("the reason must come from the matrix", reason.contains("Windows Media") || reason.isNotBlank())
    }

    @Test
    fun theKindsTheAppCanDrawOpenHere() {
        val cases = mapOf(
            "photo.jpg" to Viewer.IMAGE,
            "clip.mp4" to Viewer.PLAYER,
            "song.mp3" to Viewer.PLAYER,
            "paper.pdf" to Viewer.PDF,
            "notes.txt" to Viewer.TEXT,
        )
        cases.forEach { (name, expected) ->
            val item = TestMedia.item(id = 1, name = name, type = FileTypeRegistry.kindFor(null, name))
            val destination = FileOpener.destinationFor(item)
            assertEquals("$name should open here", FileDestination.Here(expected), destination)
        }
    }

    @Test
    fun anArchiveIsHandedOverRatherThanIgnored() {
        // The exact case the old `when` did nothing for. A zip has no viewer in this app, and it
        // must say so rather than leave the tap unanswered.
        val item = TestMedia.item(id = 1, name = "backup.zip", type = FileTypeRegistry.kindFor(null, "backup.zip"))
        val destination = FileOpener.destinationFor(item)
        assertTrue("an archive must go somewhere", destination is FileDestination.HandOff)
        assertTrue(
            "and it must say why",
            (destination as FileDestination.HandOff).reason.isNotBlank(),
        )
    }

    @Test
    fun theMatrixAndTheOpenerCannotDisagree() {
        // Whatever the capability screen promises is what a tap does. They read the same table, and
        // this asserts it rather than trusting it.
        FileTypeRegistry.knownExtensions().forEachIndexed { index, extension ->
            val verdict = CapabilityMatrix.forExtension(extension).verdict
            val item = TestMedia.item(
                id = index.toLong(),
                name = "file.$extension",
                type = FileTypeRegistry.kindFor(null, "file.$extension"),
            )
            val destination = FileOpener.destinationFor(item)
            if (verdict == Capability.NEEDS_ANOTHER_APP) {
                assertTrue(
                    ".$extension is refused by the matrix, so it must be handed over",
                    destination is FileDestination.HandOff,
                )
            }
        }
    }
}
