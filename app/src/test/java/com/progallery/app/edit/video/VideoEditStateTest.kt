package com.progallery.app.edit.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEditStateTest {

    @Test
    fun normalized_clampsEndToDuration() {
        val state = VideoEditState(trimStartMs = 1000, trimEndMs = 99999).normalized(5000)
        assertEquals(5000, state.trimEndMs)
    }

    @Test
    fun normalized_keepsStartBeforeEnd() {
        val state = VideoEditState(trimStartMs = 8000, trimEndMs = 5000).normalized(10000)
        assertEquals(4999, state.trimStartMs)
        assertEquals(5000, state.trimEndMs)
    }

    @Test
    fun normalized_defaultsEndToDurationWhenZero() {
        val state = VideoEditState(trimStartMs = 500, trimEndMs = 0).normalized(4000)
        assertEquals(4000, state.trimEndMs)
    }

    @Test
    fun trimDuration_isNonNegative() {
        val state = VideoEditState(trimStartMs = 100, trimEndMs = 100)
        assertEquals(0, state.trimDurationMs)
    }
}
