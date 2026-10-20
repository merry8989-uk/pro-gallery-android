package com.progallery.app.util

import android.content.Context
import coil.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports and clears the app's **own** thumbnail cache.
 *
 * This is Coil's cache of generated previews, which lives in the app's cache
 * directory. Clearing it can never touch the user's photos, videos or any
 * recovered file — those are not in here.
 */
@Singleton
class ThumbnailCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Bytes currently held in the thumbnail cache. */
    fun sizeBytes(): Long = runCatching { context.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)

    /** The configured upper bound, in bytes. */
    fun limitBytes(): Long =
        runCatching { context.imageLoader.diskCache?.maxSize ?: 0L }.getOrDefault(0L)

    /** Clears the disk and memory thumbnail caches. Returns the bytes freed. */
    suspend fun clear(): Long = withContext(Dispatchers.IO) {
        val before = sizeBytes()
        runCatching { context.imageLoader.diskCache?.clear() }
        runCatching { context.imageLoader.memoryCache?.clear() }
        before
    }
}
