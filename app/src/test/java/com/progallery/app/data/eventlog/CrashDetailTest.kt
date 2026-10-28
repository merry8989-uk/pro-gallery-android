package com.progallery.app.data.eventlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a crash line must say to be worth reading.
 *
 * The log used to record `exception_type=IllegalArgumentException; fatal=true` and nothing else. Three
 * crashes in one exported log, all identical, all unactionable — it said the app died, not why, not
 * where, and not what was being done.
 */
class CrashDetailTest {

    private fun crash(
        message: String? = "boom",
        screen: String? = "Photos",
        cause: Throwable? = null,
    ): Map<String, String> {
        val throwable = IllegalArgumentException(message, cause).apply {
            stackTrace = arrayOf(
                StackTraceElement("androidx.media3.exoplayer.ExoPlayer", "prepare", "ExoPlayer.kt", 100),
                StackTraceElement("com.progallery.app.ui.screens.player.MediaPlayerViewModel", "load", "MediaPlayerViewModel.kt", 88),
                StackTraceElement("com.progallery.app.ui.screens.player.MediaPlayerScreen", "onCreate", "MediaPlayerScreen.kt", 40),
            )
        }
        return CrashDetail.of(throwable, screen = screen, threadName = "main")
    }

    @Test
    fun aCrashSaysWhyItHappened() {
        assertEquals("boom", crash()["message"])
    }

    @Test
    fun itSaysWhereInsideThisApp() {
        // The top frame is a library's. Naming that tells a reader nothing about where **this** app
        // went wrong, so the first frame belonging to the app is the one recorded.
        assertEquals("MediaPlayerViewModel.load:88", crash()["where"])
    }

    @Test
    fun itSaysWhichScreenAndWhichThread() {
        assertEquals("Photos", crash()["screen"])
        assertEquals("main", crash()["thread"])
    }

    @Test
    fun itKeepsTheHeadOfTheStack() {
        val stack = crash()["stack"].orEmpty()
        assertTrue("the app frame should be in it", stack.contains("MediaPlayerViewModel.load"))
        assertTrue("and it should not be the whole stack", stack.length < 300)
    }

    @Test
    fun itRecordsTheCauseUnderneathTheWrapper() {
        val details = crash(message = "failed to load", cause = IllegalStateException("the uri was empty"))
        assertEquals("IllegalStateException", details["cause_type"])
        assertEquals("the uri was empty", details["cause_message"])
    }

    @Test
    fun aMissingMessageStillSaysSomething() {
        assertEquals("no message", crash(message = null)["message"])
        assertEquals("no message", crash(message = "   ")["message"])
    }

    @Test
    fun pathsAreRemovedUnlessThePersonAskedForThem() {
        // The logging rules forbid a file address by default; the app's own switch exists to allow
        // it. A crash message can carry one, so it is redacted here too.
        val throwable = IllegalStateException("cannot read content://media/external/file/42")
        throwable.stackTrace = arrayOf(StackTraceElement("com.progallery.app.X", "y", "X.kt", 1))

        val byDefault = CrashDetail.of(throwable, screen = "Photos", threadName = "main")
        assertFalse(byDefault["message"]!!.contains("content://"))
        assertTrue(byDefault["message"]!!.contains("<path>"))

        val asked = CrashDetail.of(throwable, screen = "Photos", threadName = "main", includePaths = true)
        assertTrue("the switch turns it back on", asked["message"]!!.contains("content://media"))
    }

    @Test
    fun aCrashWithNoAppFrameStillNamesSomething() {
        val throwable = IllegalStateException("boom")
        throwable.stackTrace = arrayOf(StackTraceElement("android.os.Handler", "run", "Handler.java", 1))
        val details = CrashDetail.of(throwable, screen = null, threadName = null)
        assertEquals("Handler.run:1", details["where"])
        assertEquals("unknown", details["screen"])
        assertEquals("unknown", details["thread"])
    }
}
