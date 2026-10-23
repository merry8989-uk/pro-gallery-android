package com.progallery.app.data.settings

import com.progallery.app.data.model.ImageDecodability
import com.progallery.app.data.model.MediaItem

/**
 * Which kinds may show a real preview, rather than a type icon.
 *
 * A refinement of "generate thumbnails", not an alternative to it: with thumbnails
 * off nothing is decoded at all. The per-type switches exist because a real preview
 * costs something different per kind — a video frame and a PDF first page are both far
 * more expensive than an image tile.
 *
 * Pure, so the whole decision is unit tested.
 */
data class ThumbnailPolicy(
    val generateThumbnails: Boolean = true,
    val videoThumbnails: Boolean = true,
    val audioArtwork: Boolean = true,
    val documentPreviews: Boolean = true,
    /** Whether a file identified only from its bytes may show a real preview. */
    val signatureThumbnails: Boolean = true,
    /** The longest edge a decoded preview may have. */
    val maxDimension: Int = 512,
    /**
     * Whether a decoded preview is kept in the caches.
     *
     * Off is applied **per request** rather than by rebuilding the loader: Coil's loader is a
     * singleton built at startup, so a setting that only took effect after a restart would be a
     * switch that appears not to work. `CachePolicy.DISABLED` is immediate.
     */
    val cache: Boolean = true,
) {

    /**
     * Whether a real preview may be produced for [item].
     *
     * An image is only previewed when the platform can actually decode its format —
     * otherwise the tile would be promised a picture and left blank. See
     * [ImageDecodability] for which formats those are.
     */
    fun allows(item: MediaItem, identifiedBySignature: Boolean = false): Boolean = when {
        !generateThumbnails -> false
        // First, because a file identified from its bytes has a kind the bytes decided — so the
        // branches below would answer for it using an extension nobody should trust.
        identifiedBySignature -> signatureThumbnails
        item.isImage -> ImageDecodability.canRender(item.extension)
        item.isVideo -> videoThumbnails
        item.isAudio -> audioArtwork
        item.isDocument -> documentPreviews
        else -> false
    }

    /** Whether [item]'s preview comes from something other than the file's own pixels. */
    fun needsEmbeddedPreview(item: MediaItem): Boolean =
        allows(item) && (item.isAudio || item.isDocument)

    companion object {
        fun from(settings: AppSettings): ThumbnailPolicy = ThumbnailPolicy(
            generateThumbnails = settings.generateThumbnails,
            videoThumbnails = settings.videoThumbnails,
            audioArtwork = settings.audioArtwork,
            documentPreviews = settings.documentPreviews,
            signatureThumbnails = settings.signatureThumbnails,
            maxDimension = settings.effectiveThumbnailDimension,
            cache = settings.cacheThumbnails,
        )
    }
}
