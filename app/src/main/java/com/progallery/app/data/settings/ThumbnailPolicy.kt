package com.progallery.app.data.settings

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
) {

    /** Whether a real preview may be produced for [item]. */
    fun allows(item: MediaItem): Boolean = when {
        !generateThumbnails -> false
        item.isImage -> true
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
        )
    }
}
