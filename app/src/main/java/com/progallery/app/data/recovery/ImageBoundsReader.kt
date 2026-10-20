package com.progallery.app.data.recovery

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** An image's pixel size, read without decoding the pixels. */
data class ImageBounds(val width: Int, val height: Int) {
    val label: String get() = "$width × $height"
}

/**
 * Reads an image's dimensions from its header only.
 *
 * `inJustDecodeBounds` never allocates the bitmap, so this stays cheap even for a
 * 50 MP photo, and it is what lets a thumbnail result show an estimated size.
 */
@Singleton
class ImageBoundsReader @Inject constructor() {

    suspend fun read(openStream: () -> InputStream?): ImageBounds? = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream()?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0 && options.outHeight > 0) {
                ImageBounds(options.outWidth, options.outHeight)
            } else {
                null
            }
        }.getOrNull()
    }
}
