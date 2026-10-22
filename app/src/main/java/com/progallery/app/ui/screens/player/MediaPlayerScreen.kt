package com.progallery.app.ui.screens.player

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.progallery.app.data.media.PlaybackFailure
import com.progallery.app.data.playback.PlaybackEngine
import com.progallery.app.data.playback.rememberPlaybackEngine
import com.progallery.app.ui.components.FileActionBar
import com.progallery.app.ui.components.FileActionSpec
import com.progallery.app.ui.components.FileInfoDialog
import com.progallery.app.ui.components.FolderPickerDialog
import com.progallery.app.ui.components.sameUri
import com.progallery.app.util.ImmersiveSystemBars
import com.progallery.app.util.MediaFormatUtils
import kotlinx.coroutines.delay

/**
 * The one player.
 *
 * Video and audio were two screens with two players, two failure paths and two views of what
 * "playing" means — and they had already drifted, which is how the same codec came to play as
 * video and fail as audio. There is one player now, built through
 * [com.progallery.app.data.playback.PlaybackEngine], and this screen is the only place one is
 * created.
 *
 * What differs is the **surface** and the **actions**, so those are the only two things that
 * branch:
 *
 * - **Video** fills the screen in a pager, so swiping moves to the next one in the order of the
 *   grid it was opened from, and the actions are trim, advanced edit and share.
 * - **Audio** shows the file and its controls, and the actions are edit, cut, share, move, copy,
 *   info and delete — because an audio file is a file you manage, not just one you watch.
 *
 * Everything else — the item, the queue, the position memory, the failures and the snackbar that
 * offers what can actually be done about them — is shared, because it is the same problem.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    uri: String,
    onBack: () -> Unit,
    onEdit: (uri: String, isAudio: Boolean, cut: Boolean) -> Unit,
    viewModel: MediaPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val engine = rememberPlaybackEngine()
    val player = remember { engine.build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // `.value` is right here: this is a callback, not composition, and it must read
                // whichever file is current at the moment it fails — not the one captured when the
                // listener was created.
                viewModel.reportPlaybackFailure(error.errorCode, viewModel.state.value.item?.displayName)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onPlayingChanged(isPlaying)
                // Saved on every pause, not on a timer: pausing is the moment a person is done for
                // now, and the last moment the position is certainly still valid.
                if (!isPlaying) viewModel.savePositionOf(player)
            }
        }
        player.addListener(listener)
        onDispose {
            // Before the release, because after it there is nothing left to read.
            viewModel.savePositionOf(player)
            player.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(uri) { viewModel.configure(uri) }

    // ExoPlayer has no position callback, so the progress is read on a timer while this is on
    // screen. One loop for both surfaces: the video surface does not show a progress bar, but it
    // costs nothing to keep the state true, and the audio surface needs it.
    LaunchedEffect(player) {
        while (true) {
            delay(500)
            viewModel.onProgress(
                positionMs = runCatching { player.currentPosition }.getOrDefault(0L),
                durationMs = runCatching { player.duration }.getOrDefault(0L),
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            // The snackbar offers what can actually be done about this failure, which differs by
            // reason: a stale entry wants a rescan, an unsupported codec wants another app.
            val label = when (state.failureAction) {
                PlaybackFailure.Action.RESCAN -> "Check again"
                PlaybackFailure.Action.ANOTHER_APP -> "Open with"
                PlaybackFailure.Action.RETRY -> "Try again"
                PlaybackFailure.Action.NONE -> null
            }
            val result = snackbarHostState.showSnackbar(it, actionLabel = label)
            if (result == SnackbarResult.ActionPerformed) {
                when (state.failureAction) {
                    PlaybackFailure.Action.RESCAN -> viewModel.rescanAndRetry()
                    PlaybackFailure.Action.ANOTHER_APP -> viewModel.openCurrentWithAnotherApp()
                    PlaybackFailure.Action.RETRY -> viewModel.rescanAndRetry()
                    PlaybackFailure.Action.NONE -> Unit
                }
            }
            viewModel.consumeMessage()
            viewModel.consumeFailureAction()
        }
    }

    // The platform's own consent, for the audio actions that change other apps' files.
    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onWriteConsent(result.resultCode == android.app.Activity.RESULT_OK) }
    LaunchedEffect(state.pendingWrite) {
        state.pendingWrite?.let {
            writeLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeleteConsent(result.resultCode == android.app.Activity.RESULT_OK) }
    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let {
            deleteLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }

    if (state.isAudio) {
        AudioSurface(
            state = state,
            player = player,
            folders = folders,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onEdit = onEdit,
            viewModel = viewModel,
        )
    } else {
        VideoSurface(
            state = state,
            player = player,
            engine = engine,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onEdit = onEdit,
            viewModel = viewModel,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Video
// ---------------------------------------------------------------------------------------------

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    state: MediaPlayerUiState,
    player: Player,
    engine: PlaybackEngine,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (uri: String, isAudio: Boolean, cut: Boolean) -> Unit,
    viewModel: MediaPlayerViewModel,
) {
    ImmersiveSystemBars(hide = true)

    val pagerState = rememberPagerState(pageCount = { state.items.size.coerceAtLeast(1) })
    val current = state.item

    // Start on the file that was tapped.
    LaunchedEffect(state.items) {
        val index = state.items.indexOfFirst { sameUri(it.uri.toString(), current?.uri?.toString() ?: "") }
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }

    // Whatever page is showing is what plays — this is the swipe-to-next behaviour.
    LaunchedEffect(pagerState.currentPage, state.items) {
        val page = state.items.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        // Swiping is leaving the last one, so its position is saved before the player is pointed
        // somewhere else.
        if (current != null && current.mediaKey != page.mediaKey) viewModel.savePositionOf(player)

        viewModel.setCurrent(page)
        player.setMediaItem(engine.mediaItem(page.uri, page.mimeType, page.displayName))
        // Seek before preparing: Media3 takes this as the position to start from, so the file
        // opens where it was left rather than at the beginning and then jumping.
        val resume = viewModel.resumeAt(page)
        if (resume > 0) player.seekTo(resume)
        player.prepare()
        player.playWhenReady = true
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                            setShutterBackgroundColor(AndroidColor.BLACK)
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
            if (state.items.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${state.items.size}",
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
                // Which file this is, not just which number. With thirty-two videos in the list, a
                // position alone does not say what is on screen.
                current?.displayName?.let { name ->
                    Text(
                        text = name,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
                    )
                }
            }
        }

        FileActionBar(
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.35f)),
            actions = listOf(
                FileActionSpec(Icons.Filled.ContentCut, "Trim") {
                    current?.uri?.toString()?.let { onEdit(it, false, true) }
                },
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

// ---------------------------------------------------------------------------------------------
// Audio
// ---------------------------------------------------------------------------------------------

@Composable
private fun AudioSurface(
    state: MediaPlayerUiState,
    player: Player,
    folders: List<String>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (uri: String, isAudio: Boolean, cut: Boolean) -> Unit,
    viewModel: MediaPlayerViewModel,
) {
    var copyOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    val item = state.item

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = item?.displayName ?: "Audio",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text(state.error!!, textAlign = TextAlign.Center)
                    else -> {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(96.dp),
                        )
                        Text(
                            text = item?.displayName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                        )
                        Text(
                            text = item?.let { MediaFormatUtils.formatBytes(it.sizeBytes) }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        IconButton(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            modifier = Modifier.padding(top = 16.dp).size(72.dp),
                        ) {
                            Icon(
                                imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.playing) "Pause" else "Play",
                                modifier = Modifier.size(52.dp),
                            )
                        }
                    }
                }
            }

            if (state.error == null && item != null) {
                val duration = state.durationMs.coerceAtLeast(1)
                val position = if (seeking) seekValue.toLong() else state.positionMs
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = {
                            seeking = true
                            seekValue = it
                        },
                        onValueChangeFinished = {
                            player.seekTo(seekValue.toLong().coerceAtLeast(0))
                            seeking = false
                        },
                        valueRange = 0f..duration.toFloat(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = MediaFormatUtils.formatDuration(position),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = MediaFormatUtils.formatDuration(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                FileActionBar(
                    actions = listOf(
                        FileActionSpec(Icons.Filled.Edit, "Edit") {
                            onEdit(item.uri.toString(), true, false)
                        },
                        FileActionSpec(Icons.Filled.ContentCut, "Cut") {
                            onEdit(item.uri.toString(), true, true)
                        },
                        FileActionSpec(Icons.Filled.Share, "Share") { viewModel.share() },
                        FileActionSpec(Icons.Filled.DriveFileMove, "Move") { moveOpen = true },
                        FileActionSpec(Icons.Filled.ContentCopy, "Copy") { copyOpen = true },
                        FileActionSpec(Icons.Filled.Info, "Info") { infoOpen = true },
                        FileActionSpec(Icons.Filled.Delete, "Delete") { viewModel.requestDelete() },
                    ),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (copyOpen && item != null) {
        FolderPickerDialog(
            title = "Copy to folder",
            folders = folders,
            currentPath = item.relativePath ?: item.bucketName,
            confirmLabel = "Copy",
            onDismiss = { copyOpen = false },
            onConfirm = { target -> viewModel.requestCopy(target); copyOpen = false },
        )
    }
    if (moveOpen && item != null) {
        FolderPickerDialog(
            title = "Move to folder",
            folders = folders,
            currentPath = item.relativePath ?: item.bucketName,
            confirmLabel = "Move",
            onDismiss = { moveOpen = false },
            onConfirm = { target -> viewModel.requestMove(target); moveOpen = false },
        )
    }
    if (infoOpen && item != null) {
        FileInfoDialog(item = item, onDismiss = { infoOpen = false })
    }
}
