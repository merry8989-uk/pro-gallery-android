package com.progallery.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.progallery.app.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Application entry point. Configures Hilt and a shared Coil [ImageLoader] tuned
 * for large media libraries (bounded memory + disk caches, video frames).
 *
 * The disk cache size comes from the user's setting. Coil builds its cache once,
 * so the value is read here at startup and a change takes effect on the next
 * launch — which the Settings row says.
 */
@HiltAndroidApp
class ProGalleryApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var settingsRepository: SettingsRepository

    /** Read once at startup; Coil cannot resize its cache later. */
    @Volatile
    private var thumbnailCacheLimitMb: Int = DEFAULT_CACHE_MB

    override fun onCreate() {
        super.onCreate()
        thumbnailCacheLimitMb = runCatching {
            runBlocking { settingsRepository.current().thumbnailCacheLimitMb }
        }.getOrDefault(DEFAULT_CACHE_MB)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(thumbnailCacheLimitMb.toLong() * 1024L * 1024L)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val DEFAULT_CACHE_MB = 256
    }
}
