package com.progallery.app.domain

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SortComparatorsTest {

    private val items = listOf(
        TestMedia.item(id = 1, name = "banana.jpg", size = 300, date = 10),
        TestMedia.item(id = 2, name = "apple.jpg", size = 100, date = 30),
        TestMedia.item(id = 3, name = "cherry.mp4", size = 200, date = 20, type = MediaType.VIDEO),
    )

    @Test
    fun dateNewest_ordersDescending() {
        val sorted = SortComparators.sort(items, SortOrder.DATE_NEWEST)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun dateOldest_ordersAscending() {
        val sorted = SortComparators.sort(items, SortOrder.DATE_OLDEST)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun nameAsc_isCaseInsensitive() {
        val sorted = SortComparators.sort(items, SortOrder.NAME_ASC)
        assertEquals(listOf("apple.jpg", "banana.jpg", "cherry.mp4"), sorted.map { it.displayName })
    }

    @Test
    fun sizeDesc_ordersLargestFirst() {
        val sorted = SortComparators.sort(items, SortOrder.SIZE_DESC)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun type_groupsImagesBeforeVideos() {
        val sorted = SortComparators.sort(items, SortOrder.TYPE)
        assertEquals(MediaType.VIDEO, sorted.last().mediaType)
    }
}
