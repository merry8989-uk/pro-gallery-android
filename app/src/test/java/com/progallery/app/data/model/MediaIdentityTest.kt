package com.progallery.app.data.model

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Identity must be the content URI, not the numeric MediaStore id. Photos and
 * videos come from different collections, so their ids collide; and a file on a
 * removable volume shares ids with one on internal storage.
 */
class MediaIdentityTest {

    private fun itemWith(uriString: String, type: MediaType, id: Long = 7L): MediaItem =
        MediaItem(
            id = id,
            uri = mockk<Uri>().also { every { it.toString() } returns uriString },
            displayName = "file",
            mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
            sizeBytes = 1,
            dateModifiedSeconds = 0,
            width = 1,
            height = 1,
            durationMs = 0,
            bucketId = 1,
            bucketName = "Camera",
            relativePath = null,
            mediaType = type,
        )

    @Test
    fun sameNumericId_acrossMediaTypes_isNotTheSameIdentity() {
        val image = itemWith("content://media/external/images/media/7", MediaType.IMAGE)
        val video = itemWith("content://media/external/video/media/7", MediaType.VIDEO)

        assertEquals(7L, image.id)
        assertEquals(7L, video.id)
        assertNotEquals(
            "an image and a video sharing an id must not share identity",
            image.mediaKey,
            video.mediaKey,
        )
    }

    @Test
    fun mediaKey_isTheContentUri() {
        val uri = "content://media/external_primary/images/media/42"
        assertEquals(uri, itemWith(uri, MediaType.IMAGE).mediaKey)
    }

    @Test
    fun sameIdOnDifferentVolumes_isNotTheSameIdentity() {
        val internal = itemWith("content://media/external_primary/images/media/9", MediaType.IMAGE, id = 9)
        val sdCard = itemWith("content://media/1234-5678/images/media/9", MediaType.IMAGE, id = 9)
        assertNotEquals(internal.mediaKey, sdCard.mediaKey)
    }
}
