package com.progallery.app.edit.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException

/**
 * Applies [EditParams] to a bitmap. Everything is done on a downscaled working
 * copy for the live preview and on the full-resolution source at export time, so
 * editing a 50 MP photo never risks an OutOfMemoryError.
 */
@Singleton
class BitmapEditor @Inject constructor() {

    /** Decodes a URI, honouring EXIF orientation and sampling down to [maxDimension]. */
    suspend fun decodeOriented(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048,
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            applyExifOrientation(bitmap, orientation)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    /** Full pipeline: geometry first, then colour, then blur/sharpen. */
    suspend fun render(source: Bitmap, params: EditParams): Bitmap =
        withContext(Dispatchers.Default) {
            var working = applyGeometry(source, params)
            working = applyColor(working, params)
            if (params.blurRadius > 0.5f) working = boxBlur(working, params.blurRadius.toInt())
            if (params.sharpenAmount > 0.01f) working = sharpen(working, params.sharpenAmount)
            working
        }

    private fun applyGeometry(source: Bitmap, params: EditParams): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(params.rotationDegrees.toFloat())
        if (params.flipHorizontal) matrix.postScale(-1f, 1f)
        if (params.flipVertical) matrix.postScale(1f, -1f)
        var result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

        if (!params.crop.isFull) {
            val left = (params.crop.left * result.width).toInt().coerceIn(0, result.width - 1)
            val top = (params.crop.top * result.height).toInt().coerceIn(0, result.height - 1)
            val right = (params.crop.right * result.width).toInt().coerceIn(left + 1, result.width)
            val bottom = (params.crop.bottom * result.height).toInt().coerceIn(top + 1, result.height)
            result = Bitmap.createBitmap(result, left, top, right - left, bottom - top)
        }
        return result
    }

    private fun applyColor(source: Bitmap, params: EditParams): Bitmap {
        val matrix = ColorMatrix()
        // Brightness / exposure / contrast
        val brightness = (params.brightness + params.exposure) * 255f
        val contrast = params.contrast
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        // Saturation
        matrix.postConcat(ColorMatrix().apply { setSaturation(params.saturation) })
        // Warmth: push red up and blue down (or the reverse)
        val warmth = params.warmth * 40f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, warmth,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, -warmth,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        if (params.grayscale) matrix.postConcat(ColorMatrix().apply { setSaturation(0f) })
        if (params.sepia) matrix.postConcat(SEPIA_MATRIX)

        val output = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /** Separable box blur — O(n) per pass, safe for large bitmaps. */
    private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val temp = IntArray(width * height)

        // Horizontal pass
        for (y in 0 until height) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (x in -r..r) {
                val cx = x.coerceIn(0, width - 1)
                val p = pixels[y * width + cx]
                sumR += (p shr 16) and 0xFF; sumG += (p shr 8) and 0xFF; sumB += p and 0xFF; count++
            }
            for (x in 0 until width) {
                temp[y * width + x] = (0xFF shl 24) or ((sumR / count) shl 16) or
                    ((sumG / count) shl 8) or (sumB / count)
                val outX = (x - r).coerceIn(0, width - 1)
                val inX = (x + r + 1).coerceIn(0, width - 1)
                val outP = pixels[y * width + outX]
                val inP = pixels[y * width + inX]
                sumR += ((inP shr 16) and 0xFF) - ((outP shr 16) and 0xFF)
                sumG += ((inP shr 8) and 0xFF) - ((outP shr 8) and 0xFF)
                sumB += (inP and 0xFF) - (outP and 0xFF)
            }
        }
        // Vertical pass
        for (x in 0 until width) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (y in -r..r) {
                val cy = y.coerceIn(0, height - 1)
                val p = temp[cy * width + x]
                sumR += (p shr 16) and 0xFF; sumG += (p shr 8) and 0xFF; sumB += p and 0xFF; count++
            }
            for (y in 0 until height) {
                pixels[y * width + x] = (0xFF shl 24) or ((sumR / count) shl 16) or
                    ((sumG / count) shl 8) or (sumB / count)
                val outY = (y - r).coerceIn(0, height - 1)
                val inY = (y + r + 1).coerceIn(0, height - 1)
                val outP = temp[outY * width + x]
                val inP = temp[inY * width + x]
                sumR += ((inP shr 16) and 0xFF) - ((outP shr 16) and 0xFF)
                sumG += ((inP shr 8) and 0xFF) - ((outP shr 8) and 0xFF)
                sumB += (inP and 0xFF) - (outP and 0xFF)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** 3x3 unsharp convolution, amount 0..1. */
    private fun sharpen(source: Bitmap, amount: Float): Bitmap {
        val width = source.width
        val height = source.height
        val src = IntArray(width * height)
        source.getPixels(src, 0, width, 0, 0, width, height)
        val out = IntArray(width * height)
        val a = amount.coerceIn(0f, 1f)
        val center = 1f + 4f * a
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    out[idx] = src[idx]; continue
                }
                fun channel(shift: Int): Int {
                    val c = (src[idx] shr shift) and 0xFF
                    val up = (src[idx - width] shr shift) and 0xFF
                    val down = (src[idx + width] shr shift) and 0xFF
                    val left = (src[idx - 1] shr shift) and 0xFF
                    val right = (src[idx + 1] shr shift) and 0xFF
                    val value = center * c - a * (up + down + left + right)
                    return value.toInt().coerceIn(0, 255)
                }
                out[idx] = (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var largest = max(width, height)
        while (largest / sample > maxDimension) sample *= 2
        return sample
    }

    companion object {
        private val SEPIA_MATRIX = ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}
