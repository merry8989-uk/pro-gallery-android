package com.progallery.app.data.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What counts as a sticker, and what can honestly be done with each.
 *
 * The fault these guard against is the one the user reported: stickers that show nothing. Three
 * separate causes were behind it — the folder, the format, and transparency on a flat
 * background — so each is checked.
 */
class StickersTest {

    private val whatsappStickers =
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"

    // ---- what is a sticker ----

    @Test
    fun aWebpInTheWhatsAppStickerFolderIsASticker() {
        assertTrue(Stickers.isSticker("webp", "$whatsappStickers/cat.webp"))
    }

    @Test
    fun theFolderMatchIgnoresCaseAndLanguage() {
        // WhatsApp names this folder in the device's language.
        assertTrue(Stickers.isSticker("webp", "WhatsApp/Media/WhatsApp Stickers/a.webp"))
        assertTrue(Stickers.isSticker("webp", "Android/media/x/Media/figurinhas/a.webp"))
        assertTrue(Stickers.isSticker("webp", "Telegram/Stickers/a.webp"))
    }

    @Test
    fun anOrdinaryPhotoIsNotASticker() {
        assertFalse(Stickers.isSticker("jpg", "DCIM/Camera/IMG_0001.jpg"))
        assertFalse(Stickers.isSticker("webp", "Pictures/holiday.webp"))
        assertFalse(Stickers.isSticker("png", "Download/screenshot.png"))
    }

    @Test
    fun aTgsIsAStickerWhereverItIs() {
        // The extension is only ever used for stickers, so the folder does not have to say so.
        assertTrue(Stickers.isSticker("tgs", "Telegram/a.tgs"))
        assertTrue(Stickers.isSticker("tgs", null))
    }

    @Test
    fun anUnknownFormatInAStickerFolderIsNotCalledASticker() {
        assertFalse(Stickers.isSticker("xyz", "$whatsappStickers/a.xyz"))
        assertFalse(Stickers.isSticker("", "$whatsappStickers/a"))
    }

    @Test
    fun aStickerExtensionWithNoPathIsNotASticker() {
        assertFalse(Stickers.isSticker("webp", null))
        assertFalse(Stickers.isSticker("webp", ""))
    }

    // ---- what each kind is ----

    @Test
    fun everyKindIsIdentified() {
        assertEquals(StickerKind.ANIMATED, Stickers.kind("webp", whatsappStickers))
        assertEquals(StickerKind.IMAGE, Stickers.kind("png", whatsappStickers))
        assertEquals(StickerKind.VECTOR, Stickers.kind("svg", whatsappStickers))
        assertEquals(StickerKind.VIDEO, Stickers.kind("webm", whatsappStickers))
        assertEquals(StickerKind.LOTTIE, Stickers.kind("tgs", "Telegram"))
        assertEquals(StickerKind.UNKNOWN, Stickers.kind("xyz", whatsappStickers))
    }

    @Test
    fun webpIsTreatedAsAnimatedBecauseTheDecoderHandlesBoth() {
        // There is no cheap way to know whether a WebP is animated without decoding it, and the
        // decoder animates it either way — so it is shown rather than guessed at.
        assertEquals(StickerKind.ANIMATED, Stickers.kind("webp", whatsappStickers))
    }

    // ---- what can be shown, and what cannot ----

    @Test
    fun aTgsCannotBeShownAndSaysWhy() {
        val kind = Stickers.kind("tgs", "Telegram")
        assertFalse("there is no renderer for it", Stickers.canShow(kind))
        val reason = Stickers.reason(kind)!!
        assertTrue("it must name the format", reason.contains(".tgs"))
        assertTrue("it must say what it actually is", reason.contains("animation"))
        assertTrue("and offer a way out", reason.contains("another app"))
    }

    @Test
    fun everyOtherKindCanBeShown() {
        listOf(
            StickerKind.IMAGE, StickerKind.ANIMATED, StickerKind.VIDEO, StickerKind.VECTOR,
        ).forEach { kind ->
            assertTrue("$kind should be showable", Stickers.canShow(kind))
            assertNull("$kind has nothing to explain", Stickers.reason(kind))
        }
    }

    @Test
    fun anUnrecognisedKindCannotBeShownAndSaysSo() {
        assertFalse(Stickers.canShow(StickerKind.UNKNOWN))
        assertNotNull(Stickers.reason(StickerKind.UNKNOWN))
    }

    @Test
    fun aVectorStickerIsShowableBecauseTheAppShipsADecoder() {
        // The platform cannot render SVG; the app can. That is the whole point of the change.
        assertEquals(StickerKind.VECTOR, Stickers.kind("svg", whatsappStickers))
        assertTrue(Stickers.canShow(StickerKind.VECTOR))
    }

    @Test
    fun transparencyIsAcknowledged() {
        // The reason stickers looked missing: transparent artwork on a flat tile is invisible.
        assertTrue(Stickers.NEEDS_CHEQUERBOARD_BACKGROUND)
    }

    @Test
    fun everyKindHasALabelToShow() {
        StickerKind.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
    }
}
