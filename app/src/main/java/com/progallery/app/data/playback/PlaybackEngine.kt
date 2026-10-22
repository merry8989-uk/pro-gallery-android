package com.progallery.app.data.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place a player is built.
 *
 * There were four, and they had already drifted: the video player fell back to a software decoder
 * and the other three did not, so a device without a hardware decoder played a video and failed
 * the same audio. The drift was not carelessness — there was nowhere to put the setting. This is
 * that place.
 *
 * Two things every player now gets:
 *
 * - **A decoder fallback.** A codec the device lacks should not end playback outright; Media3 is
 *   allowed to try a software decoder instead. The switch lives on the renderers factory, not on
 *   the builder, which is why it is easy to miss.
 * - **A MIME hint when the app is sure of one.** Sniffing is good and usually right; the hint
 *   removes the guess for a container whose header is ambiguous and for an address with no
 *   extension at all. It is never guessed — see [PlaybackMime].
 *
 * The data source is a parameter rather than an afterthought: the WebDAV player's sign-in rides
 * on it, and that is the only difference between it and the others.
 */
@Singleton
class PlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun build(dataSourceFactory: DataSource.Factory? = null): ExoPlayer {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val source = if (dataSourceFactory != null) {
            DefaultMediaSourceFactory(dataSourceFactory)
        } else {
            DefaultMediaSourceFactory(context)
        }
        return ExoPlayer.Builder(context, source, renderers).build().apply {
            playWhenReady = true
        }
    }

    /**
     * The media item, told what it is when that is known.
     *
     * `mimeType` and `displayName` come from the system's index when the file is in it; a stream
     * or a WebDAV address has neither, and then the extension of the address is the only clue —
     * which is what [PlaybackMime] reads.
     */
    fun mediaItem(uri: Uri, mimeType: String? = null, displayName: String? = null): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        PlaybackMime.hintFor(mimeType, displayName, uri.toString())?.let(builder::setMimeType)
        return builder.build()
    }

    /** For a player that only ever has an address. */
    fun mediaItem(address: String): MediaItem = mediaItem(Uri.parse(address))
}
