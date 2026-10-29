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

    private val notFound = PlaybackFailure.IO_FILE_NOT_FOUND

    @Test
    fun aFileThatIsThereAndReadableIsAnAddressProblemNotAMissingFile() {
        val text = PlaybackFailure.message(notFound, "VID_1.mp4", fileState = PlaybackFailure.FileState.Exists)
        assertTrue(text.contains("The file is there"))
        assertFalse("it must not claim the file is gone", text.contains("no longer there"))
        assertTrue("and it must say what to do", text.contains("Re-scanning"))
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
