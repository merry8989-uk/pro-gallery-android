package com.progallery.app.edit.photo

/**
 * Crop geometry for the aspect presets.
 *
 * Works in *pixel* space. A crop expressed on a unit square cannot honour a
 * requested aspect ratio, because the square says nothing about the photo's
 * shape — and for 1:1 it degenerates to "no crop at all".
 */
object CropMath {

    /**
     * A centred crop of [ratio] (width / height) for an image of
     * [imageWidth] x [imageHeight].
     *
     * [rotationDegrees] matters because the crop is applied *after* rotation in
     * the render pipeline, so a 90/270 rotation swaps the visible width and
     * height and the crop must be computed against the visible image.
     *
     * Returns a normalised [CropRect]; [CropRect.Full] when no ratio is asked
     * for or the dimensions are unusable.
     */
    fun cropRectFor(
        ratio: Float?,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int = 0,
    ): CropRect {
        if (ratio == null || ratio <= 0f) return CropRect.Full
        if (imageWidth <= 0 || imageHeight <= 0) return CropRect.Full

        val quarterTurn = rotationDegrees.mod(360) == 90 || rotationDegrees.mod(360) == 270
        val visibleWidth = if (quarterTurn) imageHeight.toFloat() else imageWidth.toFloat()
        val visibleHeight = if (quarterTurn) imageWidth.toFloat() else imageHeight.toFloat()

        val imageAspect = visibleWidth / visibleHeight
        return if (imageAspect > ratio) {
            // Wider than the target: keep full height, trim the sides.
            val widthFraction = (ratio / imageAspect).coerceIn(0f, 1f)
            val inset = (1f - widthFraction) / 2f
            CropRect(left = inset, top = 0f, right = 1f - inset, bottom = 1f)
        } else {
            // Taller than (or equal to) the target: keep full width, trim top and bottom.
            val heightFraction = (imageAspect / ratio).coerceIn(0f, 1f)
            val inset = (1f - heightFraction) / 2f
            CropRect(left = 0f, top = inset, right = 1f, bottom = 1f - inset)
        }
    }

    /** The width/height a [rect] produces for an image of the given size. */
    fun aspectOf(rect: CropRect, imageWidth: Int, imageHeight: Int): Float {
        val croppedWidth = (rect.right - rect.left) * imageWidth
        val croppedHeight = (rect.bottom - rect.top) * imageHeight
        if (croppedHeight <= 0f) return 0f
        return croppedWidth / croppedHeight
    }
}
