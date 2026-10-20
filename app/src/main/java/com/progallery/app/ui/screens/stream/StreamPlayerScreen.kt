package com.progallery.app.ui.screens.stream

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Plays one address.
 *
 * Kept apart from the local video player on purpose: that one carries trim, delete,
 * rename and a playlist built from the library, none of which mean anything for a
 * remote address. This player has the controls that do apply, and nothing that
 * pretends to.
 *
 * When a link cannot be played, Media3's own reason is shown together with the most
 * likely cause — an address that is not a direct media file — rather than a spinner
 * that never stops.
 */
// PlayerView and its setters are @UnstableApi, which uses the AndroidX opt-in
// mechanism, so it is written fully qualified (Kotlin's OptIn is a different annotation).
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamPlayerScreen(
    uri: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val address = remember(uri) {
        runCatching { java.net.URLDecoder.decode(uri, "UTF-8") }.getOrDefault(uri)
    }
    val host = remember(address) { runCatching { Uri.parse(address).host }.getOrNull() ?: address }

    var failure by remember { mutableStateOf<String?>(null) }

    val player = remember(address) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(address))
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failure = error.errorCodeName
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) failure = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = host, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, address)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Share the link"))
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share the link")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val message = failure
            if (message != null) {
                Text(
                    text = "This link could not be played ($message).\n\n" +
                        "The usual reason is that the address is not a direct media file — a " +
                        "link to a web page, or to a service that needs a sign-in, will not " +
                        "play. HLS (.m3u8) and DASH (.mpd) streams do.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            } else {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}
