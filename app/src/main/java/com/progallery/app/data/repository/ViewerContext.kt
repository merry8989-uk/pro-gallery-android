package com.progallery.app.data.repository

import com.progallery.app.data.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The list that was on screen when the user opened the full-screen viewer.
 *
 * The pager must swipe through exactly what the grid showed — same items, same
 * order, same filters. Re-deriving it from the library instead is what made
 * videos appear while sliding through photos. Held in memory only; a stale list
 * is harmless because the viewer checks that the tapped item is actually in it.
 */
@Singleton
class ViewerContext @Inject constructor() {

    @Volatile
    private var items: List<MediaItem> = emptyList()

    fun set(list: List<MediaItem>) {
        items = list
    }

    fun current(): List<MediaItem> = items
}
