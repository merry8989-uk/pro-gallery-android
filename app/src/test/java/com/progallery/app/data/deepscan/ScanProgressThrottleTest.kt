package com.progallery.app.data.deepscan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Progress must be published often enough that a long scan never looks frozen —
 * whether the folder holds thousands of small files or a few huge ones.
 */
class ScanProgressThrottleTest {

    @Test
    fun theFirstCallAlwaysPublishes() {
        val throttle = ScanProgressThrottle()
        assertTrue(throttle.shouldPublish(filesExamined = 0, now = 0))
    }

    @Test
    fun theFileCountAloneIsEnough() {
        val throttle = ScanProgressThrottle(everyFiles = 25, everyMillis = 10_000)
        throttle.shouldPublish(0, now = 0)

        assertFalse("10 files is not enough", throttle.shouldPublish(10, now = 1))
        assertFalse("24 files is not enough", throttle.shouldPublish(24, now = 2))
        assertTrue("25 files is", throttle.shouldPublish(25, now = 3))
    }

    @Test
    fun theClockAloneIsEnoughForAFewVeryLargeFiles() {
        // A folder with three huge videos: only three files, but minutes apart.
        val throttle = ScanProgressThrottle(everyFiles = 25, everyMillis = 250)
        throttle.shouldPublish(0, now = 0)

        assertFalse("still within the window", throttle.shouldPublish(1, now = 100))
        assertTrue("the clock catches it", throttle.shouldPublish(1, now = 300))
    }

    @Test
    fun publishingResetsBothCounters() {
        val throttle = ScanProgressThrottle(everyFiles = 10, everyMillis = 250)
        throttle.shouldPublish(0, now = 0)
        assertTrue(throttle.shouldPublish(10, now = 10))

        // The count restarts from here, so 9 more files is not enough...
        assertFalse(throttle.shouldPublish(19, now = 20))
        // ...but 10 more is.
        assertTrue(throttle.shouldPublish(20, now = 30))
    }

    @Test
    fun aLongRunOfFilesPublishesRepeatedly() {
        val throttle = ScanProgressThrottle(everyFiles = 25, everyMillis = 100_000)
        var published = 0
        for (examined in 0..1000) {
            if (throttle.shouldPublish(examined, now = examined.toLong())) published++
        }
        // 1001 files, one publication per 25 files.
        assertTrue("should publish many times, got $published", published >= 40)
    }
}
