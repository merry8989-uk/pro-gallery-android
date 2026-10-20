package com.progallery.app.edit.photo

/** Normalised crop rectangle (0..1 of the source image). */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    companion object { val Full = CropRect() }
}

/**
 * The complete, non-destructive description of a photo edit. Nothing here
 * mutates the original file — it is re-applied to the source bitmap on export.
 */
data class EditParams(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropRect = CropRect.Full,
    val brightness: Float = 0f,     // -1..1
    val contrast: Float = 1f,       // 0..2
    val saturation: Float = 1f,     // 0..2
    val warmth: Float = 0f,         // -1..1
    val exposure: Float = 0f,       // -1..1
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val blurRadius: Float = 0f,     // 0..25
    val sharpenAmount: Float = 0f,  // 0..1
) {
    val isIdentity: Boolean
        get() = rotationDegrees == 0 && !flipHorizontal && !flipVertical && crop.isFull &&
            brightness == 0f && contrast == 1f && saturation == 1f && warmth == 0f &&
            exposure == 0f && !grayscale && !sepia && blurRadius == 0f && sharpenAmount == 0f
}

/**
 * Immutable edit session with undo/redo history. Pure Kotlin so the history
 * logic is unit-testable without Android.
 */
data class PhotoEditState(
    val current: EditParams = EditParams(),
    private val undoStack: List<EditParams> = emptyList(),
    private val redoStack: List<EditParams> = emptyList(),
    /** The value a dragged control started from, held until the drag ends. */
    private val dragBase: EditParams? = null,
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Commits [params] as a new state, pushing the previous one onto the undo stack. */
    fun commit(params: EditParams): PhotoEditState {
        if (params == current) return this
        return copy(
            current = params,
            undoStack = undoStack + current,
            redoStack = emptyList(),
            dragBase = null,
        )
    }

    /**
     * Shows [params] while a slider is being dragged. The preview updates live but
     * the history is left alone, so undo returns to a real edit instead of
     * stepping back through every pixel of one movement.
     */
    fun preview(params: EditParams): PhotoEditState {
        if (params == current) return this
        return copy(current = params, dragBase = dragBase ?: current)
    }

    /** Ends a drag, turning the value it started from into a single undoable step. */
    fun endPreview(): PhotoEditState {
        val base = dragBase ?: return this
        if (base == current) return copy(dragBase = null)
        return copy(undoStack = undoStack + base, redoStack = emptyList(), dragBase = null)
    }

    fun undo(): PhotoEditState {
        if (undoStack.isEmpty()) return this
        val previous = undoStack.last()
        return copy(
            current = previous,
            undoStack = undoStack.dropLast(1),
            redoStack = redoStack + current,
            dragBase = null,
        )
    }

    fun redo(): PhotoEditState {
        if (redoStack.isEmpty()) return this
        val next = redoStack.last()
        return copy(
            current = next,
            undoStack = undoStack + current,
            redoStack = redoStack.dropLast(1),
            dragBase = null,
        )
    }

    fun reset(): PhotoEditState = commit(EditParams())
}
