package com.progallery.app.data.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaExtractor

/**
 * Plays the containers Media3's own extractors cannot read, by asking the **device** to read them.
 *
 * Media3 bundles its extractors, so what the app can play is the same everywhere — which is usually
 * the point, and is exactly the problem for MPEG program streams. Android's own `MediaParser`
 * **does** read `video/mp2p`, so a `.mpg` that Media3 refuses is often a file the device can play
 * perfectly well. This factory hands those extensions to the platform and leaves everything else
 * alone.
 *
 * Four things worth being plain about:
 *
 * - **It is Android 11 and up.** `MediaParserExtractorAdapter` is `@RequiresApi(30)`. Below that
 *   there is no platform parser to call, so the engine does not install this at all and those files
 *   are handed to another app, as they were.
 * - **It is only for the extensions Media3 cannot read.** Everything else — including DASH, HLS and
 *   every format Media3 handles well — goes to the delegate untouched. A file that plays today must
 *   not start playing differently because of a change meant for `.mpg`.
 * - **Whether it works depends on the device.** The platform parser is the vendor's, so a `.mpg`
 *   may open on one phone and not another. That is why the capability screen calls these
 *   "opens, with a caveat" rather than "opens here" — the caveat is real and it is the device's.
 * - **Failure is still honest.** If the parser cannot read it either, the player reports it the way
 *   it reports every other failure, and the hand-off is still offered.
 */
@UnstableApi
internal class PlatformParserMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val dataSourceFactory: DataSource.Factory,
    private val platformParserFactory: ProgressiveMediaExtractor.Factory,
    private val extensions: Set<String>,
) : MediaSource.Factory {

    private var drmProvider: DrmSessionManagerProvider? = null
    private var errorPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply {
        drmProvider = drmSessionManagerProvider
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply {
        errorPolicy = loadErrorHandlingPolicy
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (extensionOf(mediaItem) !in extensions) return delegate.createMediaSource(mediaItem)

        val platform = ProgressiveMediaSource.Factory(dataSourceFactory, platformParserFactory)
        drmProvider?.let(platform::setDrmSessionManagerProvider)
        errorPolicy?.let(platform::setLoadErrorHandlingPolicy)
        return platform.createMediaSource(mediaItem)
    }

    /** The extension of the item's address, lowercased and without the dot. */
    private fun extensionOf(mediaItem: MediaItem): String {
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val last = uri.substringAfterLast('/', "")
        if (!last.contains('.')) return ""
        return last.substringAfterLast('.').lowercase()
    }

    companion object {
        /**
         * The MPEG program stream family, and nothing else.
         *
         * `.ts` is deliberately absent: that is a transport stream, and Media3 reads it already.
         * `.flv` is absent for the same reason. ASF and SWF are absent because the platform parser
         * does not read them either — no amount of wiring here would change that, and pretending
         * otherwise would be the kind of claim this project keeps having to remove.
         */
        val MPEG_PROGRAM_STREAM_EXTENSIONS = setOf("mpg", "mpeg", "mpe", "mpv", "m2v", "mp2", "vob")
    }
}
