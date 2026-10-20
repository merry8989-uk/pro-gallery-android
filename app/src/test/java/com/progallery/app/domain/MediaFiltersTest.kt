package com.progallery.app.domain

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFiltersTest {

    private val items = listOf(
        TestMedia.item(id = 1, name = "Holiday.jpg", type = MediaType.IMAGE, bucketName = "Camera"),
        TestMedia.item(id = 2, name = "clip.mp4", type = MediaType.VIDEO, bucketName = "Downloads"),
        TestMedia.item(id = 3, name = "sunset.png", type = MediaType.IMAGE, bucketName = "Screenshots", favourite = true),
    )

    @Test
    fun byType_returnsOnlyMatching() {
        assertEquals(listOf(1L, 3L), MediaFilters.byType(items, MediaType.IMAGE).map { it.id })
        assertEquals(listOf(2L), MediaFilters.byType(items, MediaType.VIDEO).map { it.id })
        assertEquals(3, MediaFilters.byType(items, MediaType.ALL).size)
    }

    @Test
    fun search_matchesNameCaseInsensitively() {
        assertEquals(listOf(1L), MediaFilters.search(items, "holiday").map { it.id })
    }

    @Test
    fun search_matchesAlbumName() {
        assertEquals(listOf(2L), MediaFilters.search(items, "downloads").map { it.id })
    }

    @Test
    fun search_blankReturnsAll() {
        assertEquals(3, MediaFilters.search(items, "   ").size)
    }

    @Test
    fun favourites_returnsOnlyFavourites() {
        assertEquals(listOf(3L), MediaFilters.favourites(items).map { it.id })
    }

    @Test
    fun excludeHidden_removesIds() {
        assertEquals(listOf(2L, 3L), MediaFilters.excludeHidden(items, setOf(1L)).map { it.id })
    }
}
