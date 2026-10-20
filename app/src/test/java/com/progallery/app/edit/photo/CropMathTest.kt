package com.progallery.app.edit.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Crop presets must produce the requested aspect ratio for any photo shape.
 * These cases are the ones the previous unit-square maths got wrong.
 */
class CropMathTest {

    private val landscape = 2000 to 1000
    private val portrait = 1000 to 2000
    private val square = 1000 to 1000

    private fun assertAspect(
        ratio: Float,
        size: Pair<Int, Int>,
        rotation: Int = 0,
    ) {
        val (w, h) = size
        val rect = CropMath.cropRectFor(ratio, w, h, rotation)
        val visibleW = if (rotation == 90 || rotation == 270) h else w
        val visibleH = if (rotation == 90 || rotation == 270) w else h
        val actual = CropMath.aspectOf(rect, visibleW, visibleH)
        assertEquals("ratio $ratio on ${w}x$h rot$rotation", ratio, actual, 0.01f)
    }

    @Test
    fun oneToOne_landscapeTrimsSides() {
        assertAspect(1f, landscape)
        val rect = CropMath.cropRectFor(1f, landscape.first, landscape.second)
        assertTrue("must actually crop", !rect.isFull)
    }

    @Test
    fun oneToOne_portraitTrimsTopAndBottom() {
        assertAspect(1f, portrait)
        val rect = CropMath.cropRectFor(1f, portrait.first, portrait.second)
        assertTrue("must actually crop", !rect.isFull)
    }

    @Test
    fun oneToOne_squareIsAlreadyCorrect() {
        assertAspect(1f, square)
        assertTrue(CropMath.cropRectFor(1f, square.first, square.second).isFull)
    }

    @Test
    fun fourThree_allShapes() {
        listOf(landscape, portrait, square).forEach { assertAspect(4f / 3f, it) }
    }

    @Test
    fun sixteenNine_allShapes() {
        listOf(landscape, portrait, square).forEach { assertAspect(16f / 9f, it) }
    }

    @Test
    fun rotation_usesTheVisibleDimensions() {
        assertAspect(1f, landscape, rotation = 90)
        assertAspect(16f / 9f, portrait, rotation = 270)
    }

    @Test
    fun nullRatio_returnsFullFrame() {
        assertTrue(CropMath.cropRectFor(null, 2000, 1000).isFull)
    }

    @Test
    fun unusableDimensions_returnFullFrame() {
        assertTrue(CropMath.cropRectFor(1f, 0, 1000).isFull)
    }
}
