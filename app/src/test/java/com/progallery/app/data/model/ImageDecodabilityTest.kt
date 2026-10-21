package com.progallery.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app used to call anything with an image extension an image and then ask the platform
 * to draw it, so formats Android has no decoder for left a blank tile. These are the claims
 * the UI now makes instead.
 */
class ImageDecodabilityTest {

    @Test
    fun everydayFormatsRenderOnEverySupportedVersion() {
        listOf("jpg", "jpeg", "jpe", "jfif", "png", "apng", "gif", "webp", "bmp", "dib", "wbmp")
            .forEach { ext ->
                assertTrue("$ext should render", ImageDecodability.canRender(ext, sdkInt = 26))
                assertEquals(DecoderSupport.ALWAYS, ImageDecodability.support(ext, 26))
            }
    }

    @Test
    fun theExtensionCaseDoesNotMatter() {
        assertTrue(ImageDecodability.canRender("JPG", 26))
        assertTrue(ImageDecodability.canRender("WebP", 26))
        assertFalse(ImageDecodability.canRender("PSD", 36))
    }

    @Test
    fun heicNeedsAndroidNine() {
        assertFalse("Android 8 has no HEIF decoder", ImageDecodability.canRender("heic", sdkInt = 26))
        assertTrue(ImageDecodability.canRender("heic", sdkInt = 28))
        assertTrue(ImageDecodability.canRender("heif", sdkInt = 28))
    }

    @Test
    fun avifNeedsAndroidTwelve() {
        assertFalse(ImageDecodability.canRender("avif", sdkInt = 30))
        assertTrue(ImageDecodability.canRender("avif", sdkInt = 31))
    }

    @Test
    fun formatsWithNoAndroidDecoderAreNeverPromised() {
        listOf(
            "psd", "psb", "xcf", "tif", "tiff", "jxl",
            "jp2", "j2k", "ico", "cur", "tga", "pcx", "ppm", "ai", "eps",
        ).forEach { ext ->
            assertFalse("$ext must never be promised", ImageDecodability.canRender(ext, sdkInt = 36))
            assertEquals(DecoderSupport.NEVER, ImageDecodability.support(ext, 36))
        }
    }

    @Test
    fun svgIsRenderableBecauseTheAppShipsADecoder() {
        // This changed with the app's own SVG decoder. The platform still has none — which is
        // why the answer is BY_APP and not ALWAYS — but the file now shows a picture, and a
        // capability screen that still said "no decoder" would be wrong.
        assertTrue(ImageDecodability.canRender("svg", sdkInt = 26))
        assertEquals(DecoderSupport.BY_APP, ImageDecodability.support("svg", 26))
        assertNull("there is nothing to warn about", ImageDecodability.reason("svg", 26))
    }

    @Test
    fun aFormatTheAppRendersIsDistinctFromOneThePlatformDoes() {
        // The distinction is worth keeping: one works on every device, the other works because
        // of a decision made here, and a future change to that decision should be visible.
        assertEquals(DecoderSupport.ALWAYS, ImageDecodability.support("png", 26))
        assertEquals(DecoderSupport.BY_APP, ImageDecodability.support("svgz", 26))
    }

    @Test
    fun anUnknownExtensionIsNotPromisedEither() {
        assertFalse(ImageDecodability.canRender("qqq", 36))
        assertFalse(ImageDecodability.canRender("", 36))
    }

    @Test
    fun aMissingPreviewIsExplainedRatherThanBlank() {
        assertEquals(
            "Android has no decoder for .psd files",
            ImageDecodability.reason("psd", 36),
        )
        assertEquals(
            "Needs Android 12 or newer to preview",
            ImageDecodability.reason("avif", 30),
        )
    }

    @Test
    fun thereIsNoReasonToGiveWhenItRenders() {
        assertNull(ImageDecodability.reason("jpg", 26))
        assertNull("HEIC renders on Android 9", ImageDecodability.reason("heic", 28))
    }

    @Test
    fun theVersionNamesAreRealAndroidVersions() {
        assertEquals("9", ImageDecodability.androidName(28))
        assertEquals("10", ImageDecodability.androidName(29))
        assertEquals("12", ImageDecodability.androidName(31))
        assertEquals("12L", ImageDecodability.androidName(32))
        assertEquals("14", ImageDecodability.androidName(34))
    }

    @Test
    fun animatedFormatsAreRecognised() {
        // These need a multi-frame decoder, which is what the sticker viewer is for.
        assertTrue(ImageDecodability.isAnimated("webp"))
        assertTrue(ImageDecodability.isAnimated("gif"))
        assertFalse(ImageDecodability.isAnimated("jpg"))
        assertFalse(ImageDecodability.isAnimated("png"))
    }

    @Test
    fun theRequiredApiIsReportedForGatedFormats() {
        assertEquals(28, ImageDecodability.requiredApi("heic"))
        assertEquals(31, ImageDecodability.requiredApi("avif"))
        assertNull(ImageDecodability.requiredApi("jpg"))
        assertNull(ImageDecodability.requiredApi("psd"))
    }
}
