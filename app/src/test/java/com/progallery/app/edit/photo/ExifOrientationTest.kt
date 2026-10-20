package com.progallery.app.edit.photo

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All eight EXIF orientation values must map to a real transform. Transpose (5)
 * and transverse (7) were previously unhandled, so those photos stayed sideways.
 */
class ExifOrientationTest {

    @Test
    fun normalNeedsNoTransform() {
        val t = ExifOrientation.transformFor(ExifInterface.ORIENTATION_NORMAL)
        assertTrue(t.isIdentity)
        assertFalse(ExifOrientation.swapsDimensions(ExifInterface.ORIENTATION_NORMAL))
    }

    @Test
    fun undefinedAndUnknownValuesAreTreatedAsNormal() {
        assertTrue(ExifOrientation.transformFor(ExifInterface.ORIENTATION_UNDEFINED).isIdentity)
        assertTrue(ExifOrientation.transformFor(999).isIdentity)
    }

    @Test
    fun rotationsSwapDimensionsOnlyFor90And270() {
        assertFalse(ExifOrientation.swapsDimensions(ExifInterface.ORIENTATION_ROTATE_180))
        assertTrue(ExifOrientation.swapsDimensions(ExifInterface.ORIENTATION_ROTATE_90))
        assertTrue(ExifOrientation.swapsDimensions(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun mirroredOrientationsAreDetected() {
        // 2, 4, 5 and 7 all involve a mirror.
        assertTrue(ExifOrientation.transformFor(ExifInterface.ORIENTATION_FLIP_HORIZONTAL).mirrorHorizontally)
        assertTrue(ExifOrientation.transformFor(ExifInterface.ORIENTATION_FLIP_VERTICAL).mirrorHorizontally)
        assertTrue(ExifOrientation.transformFor(ExifInterface.ORIENTATION_TRANSPOSE).mirrorHorizontally)
        assertTrue(ExifOrientation.transformFor(ExifInterface.ORIENTATION_TRANSVERSE).mirrorHorizontally)
        // ...and 3, 6 and 8 do not.
        assertFalse(ExifOrientation.transformFor(ExifInterface.ORIENTATION_ROTATE_180).mirrorHorizontally)
        assertFalse(ExifOrientation.transformFor(ExifInterface.ORIENTATION_ROTATE_90).mirrorHorizontally)
        assertFalse(ExifOrientation.transformFor(ExifInterface.ORIENTATION_ROTATE_270).mirrorHorizontally)
    }

    @Test
    fun transposeAndTransverseAreRealTransforms() {
        val transpose = ExifOrientation.transformFor(ExifInterface.ORIENTATION_TRANSPOSE)
        assertFalse("transpose must not be a no-op", transpose.isIdentity)
        assertEquals(90f, transpose.rotationDegrees, 0.01f)
        assertTrue(ExifOrientation.swapsDimensions(ExifInterface.ORIENTATION_TRANSPOSE))

        val transverse = ExifOrientation.transformFor(ExifInterface.ORIENTATION_TRANSVERSE)
        assertFalse("transverse must not be a no-op", transverse.isIdentity)
        assertEquals(-90f, transverse.rotationDegrees, 0.01f)
        assertTrue(ExifOrientation.swapsDimensions(ExifInterface.ORIENTATION_TRANSVERSE))
    }

    @Test
    fun flipVerticalEqualsRotate180PlusMirror() {
        val flipVertical = ExifOrientation.transformFor(ExifInterface.ORIENTATION_FLIP_VERTICAL)
        val rotate180 = ExifOrientation.transformFor(ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(rotate180.rotationDegrees, flipVertical.rotationDegrees, 0.01f)
        assertTrue(flipVertical.mirrorHorizontally)
    }
}
