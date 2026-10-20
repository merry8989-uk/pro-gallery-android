package com.progallery.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sharing is started from an application-scoped context, which requires
 * FLAG_ACTIVITY_NEW_TASK or the platform throws. These tests pin that down for
 * both the single-item and multi-item paths, on a real Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class MediaActionsInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var actions: MediaActions

    @Before
    fun setUp() {
        actions = MediaActions(context)
    }

    private fun item(id: Long, video: Boolean = false) = MediaItem(
        id = id,
        uri = Uri.parse("content://media/external/${if (video) "video" else "images"}/media/$id"),
        displayName = "file_$id.${if (video) "mp4" else "jpg"}",
        mimeType = if (video) "video/mp4" else "image/jpeg",
        sizeBytes = 1024,
        dateModifiedSeconds = 0,
        width = 100,
        height = 100,
        durationMs = 0,
        bucketId = 1,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        mediaType = if (video) MediaType.VIDEO else MediaType.IMAGE,
    )

    @Test
    fun singleItemChooser_isLaunchableFromApplicationContext() {
        val chooser = actions.shareChooser(listOf(item(1)))
        assertTrue(
            "chooser must carry FLAG_ACTIVITY_NEW_TASK",
            chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
        )
        assertTrue(
            "chooser must grant read permission on the shared URI",
            chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun singleItem_usesActionSend() {
        assertEquals(Intent.ACTION_SEND, actions.shareIntent(listOf(item(1))).action)
    }

    @Test
    fun multipleItems_useActionSendMultiple() {
        assertEquals(
            Intent.ACTION_SEND_MULTIPLE,
            actions.shareIntent(listOf(item(1), item(2))).action,
        )
    }

    @Test
    fun multipleItemsChooser_isLaunchableFromApplicationContext() {
        val chooser = actions.shareChooser(listOf(item(1), item(2, video = true)))
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
