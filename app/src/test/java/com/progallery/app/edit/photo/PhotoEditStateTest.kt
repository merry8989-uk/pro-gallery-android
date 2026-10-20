package com.progallery.app.edit.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditStateTest {

    @Test
    fun commit_pushesHistoryAndEnablesUndo() {
        val state = PhotoEditState().commit(EditParams(brightness = 0.5f))
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        assertEquals(0.5f, state.current.brightness)
    }

    @Test
    fun undo_restoresPreviousParams() {
        val state = PhotoEditState()
            .commit(EditParams(brightness = 0.5f))
            .commit(EditParams(brightness = 0.9f))
            .undo()
        assertEquals(0.5f, state.current.brightness)
        assertTrue(state.canRedo)
    }

    @Test
    fun redo_reappliesUndoneParams() {
        val state = PhotoEditState()
            .commit(EditParams(contrast = 1.5f))
            .undo()
            .redo()
        assertEquals(1.5f, state.current.contrast)
    }

    @Test
    fun commit_sameParamsIsNoOp() {
        val base = PhotoEditState().commit(EditParams(saturation = 1.2f))
        val again = base.commit(EditParams(saturation = 1.2f))
        assertEquals(base, again)
    }

    @Test
    fun commit_afterUndoClearsRedoStack() {
        val state = PhotoEditState()
            .commit(EditParams(brightness = 0.5f))
            .undo()
            .commit(EditParams(warmth = 0.3f))
        assertFalse(state.canRedo)
    }

    // ---- Undo checkpoints: a slider drag is one step, not one per pixel ----

    @Test
    fun draggingASlider_isOneUndoStep_notOnePerPixel() {
        var state = PhotoEditState()
        state = state.preview(EditParams(brightness = 0.1f))
        state = state.preview(EditParams(brightness = 0.2f))
        state = state.preview(EditParams(brightness = 0.3f))

        assertFalse("a drag must not fill the history", state.canUndo)
        assertEquals(0.3f, state.current.brightness, 0.001f)

        state = state.endPreview()
        assertTrue(state.canUndo)
        // Undo returns to where the drag started, not to an intermediate value.
        assertEquals(0f, state.undo().current.brightness, 0.001f)
    }

    @Test
    fun twoDrags_produceTwoUndoSteps() {
        var state = PhotoEditState()
            .preview(EditParams(brightness = 0.4f)).endPreview()
            .preview(EditParams(contrast = 1.4f)).endPreview()

        assertEquals(1.4f, state.current.contrast, 0.001f)
        state = state.undo()
        assertEquals(0.4f, state.current.brightness, 0.001f)
        assertEquals(1f, state.current.contrast, 0.001f)
        state = state.undo()
        assertEquals(0f, state.current.brightness, 0.001f)
        assertFalse(state.canUndo)
    }

    @Test
    fun aDragThatEndsWhereItStarted_addsNoHistory() {
        val state = PhotoEditState()
            .preview(EditParams(brightness = 0.5f))
            .preview(EditParams())
            .endPreview()
        assertFalse(state.canUndo)
    }

    @Test
    fun aDiscreteCommitAfterADrag_keepsBothSteps() {
        val state = PhotoEditState()
            .preview(EditParams(brightness = 0.5f)).endPreview()
            .commit(EditParams(rotationDegrees = 90))

        assertEquals(90, state.current.rotationDegrees)
        val once = state.undo()
        assertEquals(0.5f, once.current.brightness, 0.001f)
        assertEquals(0, once.current.rotationDegrees)
        assertEquals(0f, once.undo().current.brightness, 0.001f)
    }

    @Test
    fun undoDuringADrag_discardsTheLiveValue() {
        val state = PhotoEditState()
            .commit(EditParams(brightness = 0.5f))
            .preview(EditParams(brightness = 0.9f))
            .undo()
        assertEquals(0f, state.current.brightness, 0.001f)
        assertFalse(state.canUndo)
    }

    @Test
    fun isIdentity_trueForDefaultParams() {
        assertTrue(EditParams().isIdentity)
        assertFalse(EditParams(grayscale = true).isIdentity)
        assertFalse(EditParams(rotationDegrees = 90).isIdentity)
    }
}
