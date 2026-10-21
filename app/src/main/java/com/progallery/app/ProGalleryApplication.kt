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
import android.os.Build
import coil.decode.ImageDecoderDecoder
import coil.decode.GifDecoder
import com.progallery.app.data.memories.MemoriesScheduler

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
    @Inject lateinit var memoriesScheduler: MemoriesScheduler

    /** Read once at startup; Coil cannot resize its cache later. */
    @Volatile
    private var thumbnailCacheLimitMb: Int = DEFAULT_CACHE_MB

    override fun onCreate() {
        super.onCreate()
        // Scheduled here, so the reminder needs no setting and no first visit to Memories.
        // WorkManager keeps it across reboots.
        runCatching { memoriesScheduler.ensureScheduled() }
        thumbnailCacheLimitMb = runCatching {
            runBlocking { settingsRepository.current().thumbnailCacheLimitMb }
        }.getOrDefault(DEFAULT_CACHE_MB)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                // Animated WebP and GIF need a multi-frame decoder. Without one Coil draws
                // the first frame and stops — which is why animated stickers showed nothing
                // useful. ImageDecoder is the platform's animated path from Android 9; below
                // that only GIF can be animated, and only through the legacy decoder.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    @Suppress("DEPRECATION")
                    add(GifDecoder.Factory())
                }
            }
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
