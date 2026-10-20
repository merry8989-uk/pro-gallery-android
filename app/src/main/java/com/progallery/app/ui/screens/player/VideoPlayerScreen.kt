package com.progallery.app.ui.screens.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.progallery.app.ui.components.FileActionBar
import com.progallery.app.ui.components.FileActionSpec
import com.progallery.app.ui.components.sameUri
import com.progallery.app.util.ImmersiveSystemBars
import androidx.media3.common.util.UnstableApi

/**
 * Full-screen video playback with a swipe between videos.
 *
 * Sliding moves to the next video **in the order of the grid the user came from**,
 * and that video starts playing. One ExoPlayer is reused: only the media item is
 * swapped, so there is no second player and no audio overlap.
 */
// PlayerView and its setters are marked @UnstableApi by Media3, which uses the
// AndroidX opt-in mechanism (not Kotlin's), so it is written fully qualified.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    uri: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val decoded = Uri.parse(java.net.URLDecoder.decode(uri, "UTF-8"))
    val items by viewModel.items.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    ImmersiveSystemBars(hide = true)

    LaunchedEffect(decoded) { viewModel.configure(uri) }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val pagerState = rememberPagerState(pageCount = { items.size.coerceAtLeast(1) })

    // Start on the video that was tapped.
    LaunchedEffect(items) {
        val index = items.indexOfFirst { sameUri(it.uri.toString(), decoded.toString()) }
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }

    // Whatever page is showing is what plays — this is the swipe-to-next behaviour.
    LaunchedEffect(pagerState.currentPage, items) {
        val current = items.getOrNull(pagerState.currentPage)
        if (current != null) {
            viewModel.setCurrent(current)
            player.setMediaItem(MediaItem.fromUri(current.uri))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Only the page on screen needs a surface; the player is shared.
            beyondViewportPageCount = 0,
        ) { page ->
            if (page == pagerState.currentPage) {
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
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .statusBarsPadding()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            if (items.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        FileActionBar(
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.35f)),
            actions = listOf(
                FileActionSpec(Icons.Filled.ContentCut, "Trim") { onEdit(decoded.toString()) },
                FileActionSpec(Icons.Filled.AutoFixHigh, "Advanced") { viewModel.advancedEdit() },
                FileActionSpec(Icons.Filled.Share, "Share") { viewModel.share() },
            ),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
