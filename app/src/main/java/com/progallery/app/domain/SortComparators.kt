package com.progallery.app.domain

import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.SortOrder

/** Maps a [SortOrder] to a stable comparator over [MediaItem]. */
object SortComparators {

    fun comparatorFor(order: SortOrder): Comparator<MediaItem> = when (order) {
        SortOrder.DATE_NEWEST -> compareByDescending { it.dateModifiedSeconds }
        SortOrder.DATE_OLDEST -> compareBy { it.dateModifiedSeconds }
        SortOrder.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        SortOrder.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        SortOrder.SIZE_DESC -> compareByDescending { it.sizeBytes }
        SortOrder.SIZE_ASC -> compareBy { it.sizeBytes }
        SortOrder.TYPE -> compareBy<MediaItem> { it.mediaType.ordinal }
            .thenByDescending { it.dateModifiedSeconds }
    }

    fun sort(items: List<MediaItem>, order: SortOrder): List<MediaItem> =
        items.sortedWith(comparatorFor(order))
}
