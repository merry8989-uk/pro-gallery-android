package com.progallery.app.data.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The message for a file that will not open must say **which** of three problems it is.
 *
 * A real screenshot showed a video listed in the app, sitting in DCIM, and refusing to open with:
 * *"It has been moved or deleted, **or** it is in a folder Android does not let this app read."*
 * Both halves cannot be true, and the two have different fixes — re-scan versus grant the folder.
 * The message hedged because nothing had checked the file.
 */
class PlaybackFileStateTest {

    // The code itself is `private` inside `PlaybackFailure` — it mirrors Media3's own constant and
    // has no business being public. `2005` is `ERROR_CODE_IO_FILE_NOT_FOUND`, and it is the code the
    // exported diagnostics showed for the video that would not open.
    private val notFound = 2005

    @Test
    fun aFileThatIsThereAndReadableIsAnAddressProblemNotAMissingFile() {
        val text = PlaybackFailure.message(notFound, "VID_1.mp4", fileState = PlaybackFailure.FileState.Exists)
        assertTrue(text.contains("The file is there"))
        assertFalse("it must not claim the file is gone", text.contains("no longer there"))
        assertTrue("and it must say what to do", text.contains("Open with"))
    }

    @Test
    fun aFileThatIsThereOffersAnotherAppRatherThanOnlyARescan() {
        // The file exists; the **address** is what the player could not use. Another app may have
        // been granted access this one has not, and being able to watch the video matters more than
        // being right about whose fault it is.
        val explanation = PlaybackFailure.explain(notFound, fileState = PlaybackFailure.FileState.Exists)
        assertEquals(PlaybackFailure.Action.ANOTHER_APP, explanation.action)
        assertTrue("re-scanning is still mentioned", explanation.detail.contains("re-scan"))
    }

    @Test
    fun aFileThatCannotBeReadIsAPermissionProblem() {
        val text = PlaybackFailure.message(notFound, "VID_1.mp4", fileState = PlaybackFailure.FileState.Unreadable)
        assertTrue(text.contains("will not let this app read it"))
        assertTrue("it should name the folder that causes this", text.contains("WhatsApp"))
        assertFalse("it must not say the file is missing", text.contains("no longer there"))
    }

    @Test
    fun aFileThatIsGoneSaysSoPlainly() {
        val text = PlaybackFailure.message(notFound, "VID_1.mp4", fileState = PlaybackFailure.FileState.Missing)
        assertTrue(text.contains("no longer there"))
    }

    @Test
    fun whenNothingIsKnownItHedgesRatherThanGuessing() {
        // A `content://` address has no path to check. Hedging is honest here; claiming one of the
        // three would not be.
        val text = PlaybackFailure.message(notFound, "VID_1.mp4", fileState = PlaybackFailure.FileState.Unknown)
        assertTrue(text.contains("or it is in a folder"))
    }

    @Test
    fun theFileNameIsStillInTheMessage() {
        val text = PlaybackFailure.message(notFound, "VID_20261029_071314_043.mp4",
            fileState = PlaybackFailure.FileState.Exists)
        assertTrue(text.contains("VID_20261029_071314_043.mp4"))
    }

    @Test
    fun theDefaultIsTheHonestHedgeRatherThanAConfidentWrongAnswer() {
        // Every existing caller that does not pass a state keeps the old behaviour, which is the
        // vague-but-true one, not a new confident claim it has not checked.
        assertTrue(PlaybackFailure.message(notFound, "x.mp4").contains("or it is in a folder"))
    }
}
