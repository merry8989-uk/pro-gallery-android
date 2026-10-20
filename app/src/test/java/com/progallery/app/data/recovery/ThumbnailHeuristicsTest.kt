package com.progallery.app.data.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A thumbnail is not the original picture, so this must not over-report — and it
 * must not call an ordinary small photo a thumbnail either.
 */
class ThumbnailHeuristicsTest {

    @Test
    fun aNamedThumbnailAtThumbnailSizeIsHighConfidence() {
        val verdict = ThumbnailHeuristics.evaluate(
            name = "IMG_20240101_thumb.jpg",
            relativePath = "content://tree/photos",
            width = 320,
            height = 240,
        )
        assertTrue(verdict.isThumbnail)
        assertEquals(RecoveryConfidence.HIGH, verdict.confidence)
    }

    @Test
    fun aNamedThumbnailAtFullSizeIsStillFlaggedButOnlyMedium() {
        val verdict = ThumbnailHeuristics.evaluate(
            name = "photo-thumb.jpg",
            relativePath = null,
            width = 4000,
            height = 3000,
        )
        assertTrue("the name is a strong signal", verdict.isThumbnail)
        assertEquals(RecoveryConfidence.MEDIUM, verdict.confidence)
    }

    @Test
    fun aSmallFileInACacheFolderIsHighConfidence() {
        val verdict = ThumbnailHeuristics.evaluate(
            name = "0a1b2c3d",
            relativePath = "content://tree/DCIM/.thumbnails",
            width = 128,
            height = 128,
        )
        assertTrue(verdict.isThumbnail)
        assertEquals(RecoveryConfidence.HIGH, verdict.confidence)
    }

    @Test
    fun aFullSizeFileInACacheFolderIsOnlyMedium() {
        val verdict = ThumbnailHeuristics.evaluate(
            name = "IMG_1.jpg",
            relativePath = "content://tree/Download/cache",
            width = 4032,
            height = 3024,
        )
        assertTrue(verdict.isThumbnail)
        assertEquals(RecoveryConfidence.MEDIUM, verdict.confidence)
    }

    @Test
    fun aSizeWrittenIntoTheNamePlusASmallImageIsMedium() {
        val verdict = ThumbnailHeuristics.evaluate(
            name = "preview-320x240.jpg",
            relativePath = "content://tree/photos",
            width = 320,
            height = 240,
        )
        assertTrue(verdict.isThumbnail)
        assertEquals(RecoveryConfidence.MEDIUM, verdict.confidence)
    }

    @Test
    fun smallDimensionsAloneAreNotEvidence() {
        // A genuinely small photo is a small photo, not a thumbnail.
        val verdict = ThumbnailHeuristics.evaluate(
            name = "small-but-real.jpg",
            relativePath = "content://tree/Camera",
            width = 400,
            height = 300,
        )
        assertFalse(verdict.isThumbnail)
    }

    @Test
    fun aNormalPhotoIsNotAThumbnail() {
        val verdict = ThumbnailHeuristics.evaluate(
            name = "IMG_20240101_120000.jpg",
            relativePath = "content://tree/DCIM/Camera",
            width = 4032,
            height = 3024,
        )
        assertFalse(verdict.isThumbnail)
        assertEquals(RecoveryConfidence.LOW, verdict.confidence)
    }

    @Test
    fun unknownDimensionsDoNotProduceAFalsePositive() {
        // Dimensions we could not read must not be treated as "small".
        val verdict = ThumbnailHeuristics.evaluate(
            name = "IMG_1.jpg",
            relativePath = "content://tree/DCIM/Camera",
            width = 0,
            height = 0,
        )
        assertFalse(verdict.isThumbnail)
    }

    @Test
    fun aSizeInTheNameWithoutASmallImageIsNotEnough() {
        val verdict = ThumbnailHeuristics.evaluate(
            name = "wallpaper-1920x1080.jpg",
            relativePath = "content://tree/Pictures",
            width = 1920,
            height = 1080,
        )
        assertFalse(verdict.isThumbnail)
    }

    @Test
    fun everyVerdictCarriesAReason() {
        val verdict = ThumbnailHeuristics.evaluate("a_thumb.jpg", null, 64, 64)
        assertTrue("the user must be told why", verdict.reason.isNotBlank())
    }
}
