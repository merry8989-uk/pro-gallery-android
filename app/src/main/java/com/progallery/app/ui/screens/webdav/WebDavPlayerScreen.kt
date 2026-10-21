package com.progallery.app.ui.screens.webdav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.progallery.app.data.media.PlaybackFailure

/**
 * Plays a file straight off a WebDAV server.
 *
 * The sign-in is attached to the player's own HTTP source rather than to the address, so
 * the password never becomes part of a URL, a navigation argument, or a log line. Nothing
 * is downloaded to the device first: the file is read as it plays.
 */
// PlayerView and the data-source factory are @UnstableApi, which uses the AndroidX opt-in
// mechanism, so it is written fully qualified (Kotlin's OptIn is a different annotation).
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavPlayerScreen(
    serverId: String,
    url: String,
    onBack: () -> Unit,
    viewModel: WebDavPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val address = remember(url) {
        runCatching { java.net.URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
    }

    val auth by produceState<WebDavAuth?>(null, serverId) { value = viewModel.auth(serverId) }
    var failure by remember { mutableStateOf<Int?>(null) }

    val resolved = auth
    val player = remember(resolved, address) {
        val auth = resolved ?: return@remember null
        // The sign-in rides on the data source, and the data source is handed to the
        // player as a media source. ExoPlayer.Builder has no DataSource.Factory overload
        // in this Media3 version, so this is the supported way to inject it.
        val source = DefaultMediaSourceFactory(
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .apply { auth.header?.let { setDefaultRequestProperties(mapOf("Authorization" to it)) } },
        )
        ExoPlayer.Builder(context, source).build().apply {
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(address))
            prepare()
        }
    }

    DisposableEffect(player) {
        val active = player
        if (active == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failure = error.errorCode
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) failure = null
                }
            }
            active.addListener(listener)
            onDispose {
                active.removeListener(listener)
                active.release()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = address.substringAfterLast('/').ifBlank { address },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when {
                    resolved == null -> CircularProgressIndicator()
                    player == null -> Text(
                        text = "That server is no longer saved, so its sign-in is gone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> AndroidView(
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
            }

            val code = failure
            if (code != null) {
                Text(
                    text = PlaybackFailure.message(code, address.substringAfterLast('/')),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}
