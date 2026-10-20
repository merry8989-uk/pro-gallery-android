package com.progallery.app.ui.screens.viewer

import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sliding through photos must show photos only — this was the reported bug: the
 * pager was built from the whole library, so videos appeared mid-swipe.
 */
class ViewerPagerTest {

    private fun item(name: String, type: MediaType, previewable: Boolean) = MediaItem(
        id = name.hashCode().toLong(),
        uri = android.net.Uri.parse("content://media/$name"),
        displayName = name,
        mimeType = when (type) {
            MediaType.IMAGE -> "image/jpeg"
            MediaType.VIDEO -> "video/mp4"
            MediaType.AUDIO -> "audio/mpeg"
            else -> "application/pdf"
        },
        sizeBytes = 1_000,
        dateModifiedSeconds = 0,
        width = 0,
        height = 0,
        durationMs = 0,
        bucketId = 0,
        bucketName = "",
        relativePath = null,
        mediaType = type,
    )

    private val library = listOf(
        item("a.jpg", MediaType.IMAGE, true),
        item("clip.mp4", MediaType.VIDEO, true),
        item("b.jpg", MediaType.IMAGE, true),
        item("song.mp3", MediaType.AUDIO, false),
        item("doc.pdf", MediaType.DOCUMENT, false),
        item("clip2.mp4", MediaType.VIDEO, true),
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
        val photos = ViewerPager.itemsFor(library, MediaType.IMAGE)
        assertEquals(library.filter { it.mediaType == MediaType.IMAGE }, photos)
    }
}
