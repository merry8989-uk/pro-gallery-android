package com.progallery.app.ui.screens.home

import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A disabled file type must disappear from Home, not sit there as an empty folder.
 */
class HomeEntriesTest {

    private val all = MediaType.entries.toSet()

    @Test
    fun everyCategoryEnabled_showsEveryTypeTile() {
        assertEquals(
            listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.DOCUMENT),
            HomeEntries.typeTiles(all),
        )
        assertTrue(HomeEntries.showCollections(all))
        assertFalse(HomeEntries.isEmpty(all))
    }

    @Test
    fun videosDisabled_hidesTheVideosTileOnly() {
        val tiles = HomeEntries.typeTiles(all - MediaType.VIDEO)
        assertFalse(MediaType.VIDEO in tiles)
        assertEquals(listOf(MediaType.IMAGE, MediaType.AUDIO, MediaType.DOCUMENT), tiles)
    }

    @Test
    fun documentsDisabled_hidesTheDocumentsTile() {
        assertFalse(MediaType.DOCUMENT in HomeEntries.typeTiles(all - MediaType.DOCUMENT))
    }

    @Test
    fun nothingEnabled_hidesTheCollectionsAndFlagsHomeAsEmpty() {
        assertFalse(HomeEntries.showCollections(emptySet()))
        assertTrue(HomeEntries.isEmpty(emptySet()))
        assertTrue(HomeEntries.typeTiles(emptySet()).isEmpty())
    }

    @Test
    fun aNonTileKindDoesNotProduceATile() {
        // ARCHIVE and OTHER have no Home tile of their own; they live in "All files".
        val tiles = HomeEntries.typeTiles(setOf(MediaType.ARCHIVE, MediaType.OTHER))
        assertTrue(tiles.isEmpty())
        // ...but the collection entry points stay, because those files are still shown.
        assertTrue(HomeEntries.showCollections(setOf(MediaType.ARCHIVE)))
    }
}
