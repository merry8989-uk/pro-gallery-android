package com.progallery.app

import android.net.Uri
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import io.mockk.every
import io.mockk.mockk

/**
 * Builds [MediaItem]s for unit tests without a real Android runtime.
 *
 * Each item gets a distinct content URI, so `mediaKey` (the identity used by
 * favourites, hidden items and album membership) is unique per item exactly as
 * it is on a device.
 */
object TestMedia {

    fun item(
        id: Long,
        name: String = "file_$id.jpg",
        size: Long = 1000,
        date: Long = id,
        type: MediaType = MediaType.IMAGE,
        bucketId: Long = 1,
        bucketName: String = "Camera",
        mime: String = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
        favourite: Boolean = false,
        durationMs: Long = 0,
    ): MediaItem {
        val collection = if (type == MediaType.VIDEO) "video" else "images"
        val uriString = "content://media/external/$collection/media/$id"
        return MediaItem(
            id = id,
            uri = mockk<Uri>().also { every { it.toString() } returns uriString },
            displayName = name,
            mimeType = mime,
            sizeBytes = size,
            dateModifiedSeconds = date,
            width = 100,
            height = 100,
            durationMs = durationMs,
            bucketId = bucketId,
            bucketName = bucketName,
            relativePath = null,
            mediaType = type,
            isFavourite = favourite,
        )
    }
}
