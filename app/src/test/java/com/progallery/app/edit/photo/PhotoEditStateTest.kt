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

    @Test
    fun isIdentity_trueForDefaultParams() {
        assertTrue(EditParams().isIdentity)
        assertFalse(EditParams(grayscale = true).isIdentity)
        assertFalse(EditParams(rotationDegrees = 90).isIdentity)
    }
}
