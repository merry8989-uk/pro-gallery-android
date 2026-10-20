package com.progallery.app.util

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that the MediaStore query the app depends on does not throw on the
 * running platform (it may legitimately return an empty cursor in CI).
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun mediaStoreQuery_doesNotThrow() {
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.MIME_TYPE),
            null,
            null,
            null,
        )
        assertNotNull(cursor)
        cursor?.close()
    }
}
