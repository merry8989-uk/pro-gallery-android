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
import com.progallery.app.data.upload.LogUploadScheduler
import com.progallery.app.data.usage.UsageEvent
import com.progallery.app.data.usage.UsageStore
import com.progallery.app.data.eventlog.EventLogStore
import com.progallery.app.data.eventlog.EventName
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.decode.SvgDecoder

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
    @Inject lateinit var logUploadScheduler: LogUploadScheduler
    @Inject lateinit var usageStore: UsageStore
    @Inject lateinit var eventLog: EventLogStore

    /** For recording that must never hold up startup. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Records a crash so a fault can be found, then lets the process die as it would have.
     *
     * The record keeps the exception's class and the first few frames and **nothing else** —
     * no message, because a message is where a path or a search term would appear. See
     * [CrashSanitizer].
     *
     * `runBlocking` on a crash path is deliberate: the process is ending, and a record that
     * arrives after it does is no record at all. It is bounded, so a stuck write cannot hold
     * the crash open.
     */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                runBlocking {
                    withTimeoutOrNull(2_000) { usageStore.recordCrash(throwable) }
                    // The same fault, as a structured event: the usage store counts crashes, the
                    // event log carries the line that says when and in which session.
                    withTimeoutOrNull(2_000) {
                        eventLog.log(
                            EventName.CRASH,
                            details = mapOf(
                                "exception_type" to (throwable.javaClass.name.substringAfterLast('.')),
                                "fatal" to "true",
                            ),
                        )
                    }
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Read once at startup; Coil cannot resize its cache later. */
    @Volatile
    private var thumbnailCacheLimitMb: Int = DEFAULT_CACHE_MB

    override fun onCreate() {
        super.onCreate()
        // Scheduled here, so the reminder needs no setting and no first visit to Memories.
        // WorkManager keeps it across reboots.
        runCatching { memoriesScheduler.ensureScheduled() }

        // Sending is scheduled only if the user asked for it. Checked at startup as well as on the
        // switch, because the work may have been cancelled by the system or the app reinstalled.
        applicationScope.launch {
            runCatching {
                if (settingsRepository.current().uploadDiagnosticsEnabled) {
                    logUploadScheduler.ensureScheduled()
                }
            }
        }

        // Counted, not described: the event has no field that could hold a filename. Launched
        // rather than awaited, because recording must never delay the app starting.
        applicationScope.launch {
            runCatching { usageStore.recordEvent(UsageEvent.APP_OPENED) }
        }

        // The start of the session, and what the process found on the device it started on. The
        // phone is the infrastructure this app runs on, so this is the honest equivalent of a
        // server's startup line: memory available, and nothing about the person.
        applicationScope.launch {
            runCatching {
                val runtime = Runtime.getRuntime()
                eventLog.log(
                    EventName.SESSION_START,
                    details = mapOf(
                        "start_type" to "cold",
                        "memory_max_mb" to (runtime.maxMemory() / (1024 * 1024)).toString(),
                        "memory_free_mb" to (runtime.freeMemory() / (1024 * 1024)).toString(),
                    ),
                )
                eventLog.log(
                    EventName.MEMORY_REPORT,
                    details = mapOf(
                        "memory_total_mb" to (runtime.totalMemory() / (1024 * 1024)).toString(),
                        "memory_free_mb" to (runtime.freeMemory() / (1024 * 1024)).toString(),
                        "processors" to runtime.availableProcessors().toString(),
                    ),
                )
            }
        }

        installCrashRecorder()
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
                // Android's platform decoder cannot render SVG at all — it is a vector format,
                // not a bitmap. The app ships a decoder so those files show a picture instead
                // of a blank tile, which is what "listed but never shown" looked like.
                add(SvgDecoder.Factory())
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
