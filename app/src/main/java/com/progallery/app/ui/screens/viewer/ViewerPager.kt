package com.progallery.app.ui.screens.viewer

import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType

/**
 * What the viewer is allowed to swipe through.
 *
 * Only items that can actually be displayed, and only of the same kind as the one
 * the user tapped — opening a photo must never slide into a video. Pure, so the
 * rule is tested without a device.
 */
object ViewerPager {

    fun itemsFor(source: List<MediaItem>, type: MediaType): List<MediaItem> =
        source.filter { it.mediaType == type && it.isPreviewable }
}
