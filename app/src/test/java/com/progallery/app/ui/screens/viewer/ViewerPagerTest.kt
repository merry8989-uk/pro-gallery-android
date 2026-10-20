package com.progallery.app.ui.screens.viewer

import com.progallery.app.TestMedia
import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sliding through photos must show photos only — this was the reported bug: the
 * pager was built from the whole library, so videos appeared mid-swipe.
 */
class ViewerPagerTest {

    private val library = listOf(
        TestMedia.item(1, "a.jpg", type = MediaType.IMAGE),
        TestMedia.item(2, "clip.mp4", type = MediaType.VIDEO),
        TestMedia.item(3, "b.jpg", type = MediaType.IMAGE),
        TestMedia.item(4, "song.mp3", type = MediaType.AUDIO, mime = "audio/mpeg"),
        TestMedia.item(5, "doc.pdf", type = MediaType.DOCUMENT, mime = "application/pdf"),
        TestMedia.item(6, "clip2.mp4", type = MediaType.VIDEO),
    )

    @Test
    fun photosOnly_noVideosInThePager() {
        val photos = ViewerPager.itemsFor(library, MediaType.IMAGE)
        assertEquals(listOf("a.jpg", "b.jpg"), photos.map { it.displayName })
        assertTrue("no video may appear", photos.none { it.mediaType == MediaType.VIDEO })
    }

    @Test
    fun videosOnly_whenOpenedFromAVideo() {
        val videos = ViewerPager.itemsFor(library, MediaType.VIDEO)
        assertEquals(listOf("clip.mp4", "clip2.mp4"), videos.map { it.displayName })
    }

    @Test
    fun nonPreviewableKindsNeverEnterThePager() {
        assertTrue(ViewerPager.itemsFor(library, MediaType.AUDIO).isEmpty())
        assertTrue(ViewerPager.itemsFor(library, MediaType.DOCUMENT).isEmpty())
        assertTrue(ViewerPager.itemsFor(library, MediaType.ALL).isEmpty())
    }

    @Test
    fun orderOfTheSourceIsPreserved() {
        // The playlist follows the grid's own order, so "next" means next in line.
        val videos = ViewerPager.itemsFor(library, MediaType.VIDEO)
        assertEquals(library.filter { it.mediaType == MediaType.VIDEO }, videos)
    }
}
