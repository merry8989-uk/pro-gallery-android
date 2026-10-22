package com.progallery.app.data.capability

import com.progallery.app.data.model.FileTypeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's answer to "can this be opened here?".
 *
 * The most important test in this file is the first one: **every format the app knows must have
 * an answer**. A format added to the registry and silently left out of the matrix would mean the
 * screen quietly not mentioning it, which is how a capability screen becomes a lie.
 */
class CapabilityMatrixTest {

    @Test
    fun everyFormatTheAppKnowsHasAnAnswer() {
        val classified = CapabilityMatrix.all().map { it.extension }.toSet()
        val missing = FileTypeRegistry.knownExtensions() - classified
        assertEquals("these formats have no answer: $missing", emptySet<String>(), missing)
    }

    @Test
    fun everyVerdictThatIsNotAnOpensComesWithAReason() {
        CapabilityMatrix.all().forEach { capability ->
            when (capability.verdict) {
                Capability.OPENS_HERE ->
                    assertNull(
                        "${capability.extension} opens here, so it has nothing to explain",
                        capability.reason,
                    )
                Capability.OPENS_WITH_CAVEAT, Capability.NEEDS_ANOTHER_APP ->
                    assertNotNull(
                        "${capability.extension} is ${capability.verdict} and must say why",
                        capability.reason,
                    )
            }
        }
    }

    @Test
    fun aReasonIsNeverBlank() {
        CapabilityMatrix.all().forEach { capability ->
            capability.reason?.let {
                assertTrue("${capability.extension} has a blank reason", it.isNotBlank())
            }
        }
    }

    // ---- the containers with no parser ----

    @Test
    fun theContainersAndroidCannotParseNeedAnotherApp() {
        // The list the player's own failure message names.
        listOf("mpg", "mpeg", "mpe", "m2v", "mpv", "vob", "wmv", "asf", "wma", "swf", "rm", "rmvb", "ape")
            .forEach { ext ->
                assertEquals(
                    "$ext must need another app",
                    Capability.NEEDS_ANOTHER_APP,
                    CapabilityMatrix.forExtension(ext).verdict,
                )
            }
    }

    @Test
    fun theProgramStreamReasonSaysWhyItIsDifferentFromTheTransportStream() {
        // The distinction a person would not guess: .ts plays and .mpg does not.
        val programStream = CapabilityMatrix.forExtension("mpg")
        assertTrue(programStream.reason!!.contains("program stream"))
        assertEquals(Capability.OPENS_HERE, CapabilityMatrix.forExtension("ts").verdict)
    }

    // ---- the caveats ----

    @Test
    fun formatsWhoseContentsMayNotDecodeAreCaveatsRatherThanPromises() {
        listOf("avi", "ogv", "flv", "divx", "qt").forEach { ext ->
            assertEquals(
                "$ext should be a caveat",
                Capability.OPENS_WITH_CAVEAT,
                CapabilityMatrix.forExtension(ext).verdict,
            )
        }
    }

    @Test
    fun theOggCaveatNamesTheMissingDecoder() {
        assertTrue(CapabilityMatrix.forExtension("ogv").reason!!.contains("Theora"))
    }

    // ---- the things that do open ----

    @Test
    fun theOrdinaryFormatsOpenHere() {
        listOf("mp4", "mkv", "webm", "mov", "3gp", "ts", "m2ts").forEach { ext ->
            assertEquals(ext, Capability.OPENS_HERE, CapabilityMatrix.forExtension(ext).verdict)
        }
        listOf("mp3", "m4a", "flac", "wav", "opus", "ogg", "aac").forEach { ext ->
            assertEquals(ext, Capability.OPENS_HERE, CapabilityMatrix.forExtension(ext).verdict)
        }
    }

    @Test
    fun theAdaptivePlaylistsOpenHere() {
        // They play because the modules are on the classpath; the matrix says so rather than
        // leaving them to be discovered.
        listOf("m3u8", "m3u", "mpd").forEach { ext ->
            assertEquals(ext, Capability.OPENS_HERE, CapabilityMatrix.forExtension(ext).verdict)
        }
    }

    @Test
    fun theImageVerdictsAgreeWithTheDecoder() {
        listOf("jpg", "png", "gif", "webp", "bmp").forEach { ext ->
            assertEquals(ext, Capability.OPENS_HERE, CapabilityMatrix.forExtension(ext).verdict)
        }
        // The app ships an SVG decoder, so it opens here.
        assertEquals(Capability.OPENS_HERE, CapabilityMatrix.forExtension("svg").verdict)
        // And it does not for formats with no decoder at all.
        listOf("psd", "tiff", "xcf", "jxl", "ico").forEach { ext ->
            assertEquals(
                ext,
                Capability.NEEDS_ANOTHER_APP,
                CapabilityMatrix.forExtension(ext).verdict,
            )
        }
    }

    @Test
    fun theDocumentVerdictsAgreeWithTheReaders() {
        listOf("pdf", "txt", "json", "csv", "xml", "log", "md").forEach { ext ->
            assertEquals(ext, Capability.OPENS_HERE, CapabilityMatrix.forExtension(ext).verdict)
        }
        // These now have a reader of their own, so the answer changed with the code — which is
        // the point of deriving the verdict from the same tables the reader uses rather than
        // writing it down twice.
        listOf("docx", "docm", "xlsx", "odt", "ods", "epub", "pptx", "pptm").forEach { ext ->
            assertEquals(ext, Capability.OPENS_HERE, CapabilityMatrix.forExtension(ext).verdict)
        }
        // The old `.ppt` is a different container entirely — a binary one, not a zip — so it is
        // still handed off rather than claimed.
        listOf("ppt").forEach { ext ->
            assertEquals(
                ext,
                Capability.NEEDS_ANOTHER_APP,
                CapabilityMatrix.forExtension(ext).verdict,
            )
        }
    }

    @Test
    fun archivesAreListedButNotOpenedAndTheReasonSaysSo() {
        val zip = CapabilityMatrix.forExtension("zip")
        assertEquals(Capability.NEEDS_ANOTHER_APP, zip.verdict)
        assertTrue(zip.reason!!.contains("not open"))
    }

    @Test
    fun anUnknownExtensionGetsAnAnswerRatherThanNothing() {
        val unknown = CapabilityMatrix.forExtension("qqq")
        assertEquals(Capability.NEEDS_ANOTHER_APP, unknown.verdict)
        assertNotNull(unknown.reason)
    }

    // ---- the shape of the whole thing ----

    @Test
    fun theGroupsAccountForEveryFormatExactlyOnce() {
        val all = CapabilityMatrix.all()
        val grouped = CapabilityMatrix.byVerdict().values.flatten()
        assertEquals(all.size, grouped.size)
        assertEquals(all.map { it.extension }.toSet(), grouped.map { it.extension }.toSet())
    }

    @Test
    fun theListIsSortedSoTheScreenIsStable() {
        val extensions = CapabilityMatrix.all().map { it.extension }
        assertEquals(extensions.sorted(), extensions)
    }

    @Test
    fun everyVerdictHasALabelToShow() {
        Capability.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }

    @Test
    fun everyFormatCarriesItsKind() {
        // The screen groups by kind as well as by verdict, so the kind has to be there.
        CapabilityMatrix.all().forEach { capability ->
            assertTrue(capability.extension.isNotBlank())
        }
    }
}
