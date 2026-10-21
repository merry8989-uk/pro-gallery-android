package com.progallery.app.data.stickers

/** What a sticker file actually is, which decides how — or whether — it can be shown. */
enum class StickerKind(val label: String) {
    /** A single-frame image: PNG, or a WebP that is not animated. */
    IMAGE("Sticker"),

    /** A WebP the decoder can animate. */
    ANIMATED("Animated sticker"),

    /** A video sticker (Telegram's `.webm`), which the player can play. */
    VIDEO("Video sticker"),

    /** Telegram's `.tgs`: a gzipped Lottie animation, which is not an image at all. */
    LOTTIE("Lottie animation"),

    /** A vector, drawn by the app's own decoder. */
    VECTOR("Vector sticker"),

    /** Something in a sticker folder the app does not recognise. */
    UNKNOWN("Unrecognised"),
}

/**
 * Which files are stickers, and what can be done with each.
 *
 * **Why this is its own thing.** A sticker is not just an image: it is transparent, often
 * animated, sometimes not an image at all (`.tgs` is a gzipped animation description), and it
 * lives in a folder whose name says what it is. Treating them as ordinary pictures is why they
 * appeared to be missing — a transparent sticker drawn on the tile's own dark background is
 * genuinely invisible, and a `.tgs` file has no pixels to draw in the first place.
 *
 * Pure, so every claim is unit tested.
 */
object Stickers {

    /** Folder names that mean "this holds stickers", from the apps that use them. */
    private val STICKER_FOLDERS = listOf("sticker", "stickers", "figurinhas", "pegatinas", "autocollants")

    /** Extensions that are only ever stickers, wherever they are found. */
    private val ALWAYS_STICKERS = setOf("tgs")

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "avif")
    private val VECTOR_EXTENSIONS = setOf("svg", "svgz")
    private val VIDEO_EXTENSIONS = setOf("webm", "mp4", "gif")

    /**
     * Whether a file is a sticker.
     *
     * Two ways to qualify, and both are needed: the folder it is in, because that is how the
     * apps that make them organise them, and the extension, because `.tgs` is a sticker
     * wherever it happens to sit.
     */
    fun isSticker(extension: String, relativePath: String?): Boolean {
        val ext = extension.lowercase().trim()
        if (ext in ALWAYS_STICKERS) return true
        if (relativePath.isNullOrBlank()) return false
        if (!inStickerFolder(relativePath)) return false
        return ext in IMAGE_EXTENSIONS || ext in VECTOR_EXTENSIONS || ext in VIDEO_EXTENSIONS
    }

    /** True when the path runs through a folder named for stickers. */
    fun inStickerFolder(relativePath: String): Boolean {
        val lower = relativePath.lowercase()
        return STICKER_FOLDERS.any { lower.contains(it) }
    }

    fun kind(extension: String, relativePath: String?): StickerKind {
        val ext = extension.lowercase().trim()
        return when {
            ext == "tgs" -> StickerKind.LOTTIE
            ext in VECTOR_EXTENSIONS -> StickerKind.VECTOR
            // WebP may or may not be animated and there is no cheap way to know without
            // decoding it; the decoder animates it either way, so it is shown as a sticker
            // rather than being guessed at.
            ext == "webp" -> StickerKind.ANIMATED
            ext in VIDEO_EXTENSIONS -> StickerKind.VIDEO
            ext in IMAGE_EXTENSIONS -> StickerKind.IMAGE
            else -> StickerKind.UNKNOWN
        }
    }

    /**
     * Whether the app can show this kind.
     *
     * `.tgs` is the one it cannot: it is a gzipped Lottie animation, not a picture, and drawing
     * it would need a Lottie renderer the app does not carry. Saying so is the honest answer —
     * and the screen offers another app instead.
     */
    fun canShow(kind: StickerKind): Boolean = kind != StickerKind.LOTTIE && kind != StickerKind.UNKNOWN

    /** Why not, when it cannot. Null when there is nothing to explain. */
    fun reason(kind: StickerKind): String? = when (kind) {
        StickerKind.LOTTIE ->
            "Telegram's .tgs stickers are animations, not pictures — this app has no animation " +
                "renderer for them. Open with another app to see it."
        StickerKind.UNKNOWN ->
            "This file is in a sticker folder but its format is not one the app recognises."
        else -> null
    }

    /**
     * The background a sticker must be shown on.
     *
     * A sticker is transparent by design. Drawn on a single flat colour it can be invisible —
     * a white line drawing on a light tile, a black one on a dark tile — which is exactly what
     * "the stickers show nothing" looked like. A chequerboard is never the same colour as the
     * artwork, so the shape is always visible whatever colour it is.
     */
    const val NEEDS_CHEQUERBOARD_BACKGROUND = true
}
