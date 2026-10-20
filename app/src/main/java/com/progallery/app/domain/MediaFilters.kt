package com.progallery.app.domain

import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType

/**
 * Pure filtering helpers. Kept free of Android dependencies so they can be
 * unit tested on the JVM without an emulator.
 */
object MediaFilters {

    fun byType(items: List<MediaItem>, type: MediaType): List<MediaItem> = when (type) {
        MediaType.ALL -> items
        MediaType.IMAGE -> items.filter { it.mediaType == MediaType.IMAGE }
        MediaType.VIDEO -> items.filter { it.mediaType == MediaType.VIDEO }
    }

    /**
     * Case-insensitive search across filename, album/folder name and MIME type.
     * A blank query returns everything.
     */
    fun search(items: List<MediaItem>, query: String): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { item ->
            item.displayName.lowercase().contains(q) ||
                item.bucketName.lowercase().contains(q) ||
                item.mimeType.lowercase().contains(q)
        }
    }

    fun favourites(items: List<MediaItem>): List<MediaItem> = items.filter { it.isFavourite }

    /** Applies [hiddenIds] exclusion; hidden items never appear in the main gallery. */
    fun excludeHidden(items: List<MediaItem>, hiddenIds: Set<Long>): List<MediaItem> =
        if (hiddenIds.isEmpty()) items else items.filterNot { it.id in hiddenIds }
}
