package com.progallery.app.ui.screens.viewer

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.FileActionBar
import com.progallery.app.ui.components.FileActionSpec
import com.progallery.app.ui.components.FileInfoDialog
import com.progallery.app.ui.components.FolderPickerDialog
import com.progallery.app.ui.components.ZoomState
import com.progallery.app.ui.components.sameUri
import com.progallery.app.ui.components.zoomGestures
import com.progallery.app.util.ImmersiveSystemBars
import com.progallery.app.util.MediaFormatUtils
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.ui.text.style.TextAlign
import com.progallery.app.data.model.ImageDecodability

/**
 * Full-screen photo/video viewer.
 *
 * Gestures: single tap toggles the controls, double tap zooms in/out, two-finger
 * pinch zooms, and one-finger drag pans — but only while zoomed, so a horizontal
 * swipe still changes page when the image is at its natural size.
 */
@Composable
fun ViewerScreen(
    uri: String,
    type: MediaType = MediaType.IMAGE,
    onBack: () -> Unit,
    onEdit: (uri: String, isVideo: Boolean) -> Unit,
    onPlayVideo: (uri: String) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val decodedUri = java.net.URLDecoder.decode(uri, "UTF-8")
    val zoom = remember { ZoomState() }

    // Start at page 0 and jump to the tapped item once the library has loaded.
    // (An initialPage computed here would always be 0, because the media list is
    // still empty on the first composition.)
    // Load only the tapped item's own kind, so photos never slide into videos.
    LaunchedEffect(decodedUri, type) { viewModel.configure(decodedUri, type) }

    val pagerState = rememberPagerState(pageCount = { items.size.coerceAtLeast(1) })
    var didJump by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        if (!didJump && items.isNotEmpty()) {
            val index = items.indexOfFirst { sameUri(it.uri.toString(), decodedUri) }
            if (index >= 0) {
                pagerState.scrollToPage(index)
                didJump = true
            }
        }
    }

    // A page always opens at its natural size.
    LaunchedEffect(pagerState.currentPage) { zoom.reset() }

    var controlsVisible by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var moveTarget by remember { mutableStateOf<MediaItem?>(null) }
    var infoTarget by remember { mutableStateOf<MediaItem?>(null) }

    ImmersiveSystemBars(hide = !controlsVisible)

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeleteConsent(result.resultCode == Activity.RESULT_OK) }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onWriteConsent(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let {
            deleteLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }
    LaunchedEffect(state.pendingWrite) {
        state.pendingWrite?.let {
            writeLauncher.launch(IntentSenderRequest.Builder(it.request.intentSender).build())
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    val current = items.getOrNull(pagerState.currentPage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (items.isEmpty()) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            // Tap detection sits on an ancestor of the pager. Ancestors receive
            // pointer events after their descendants, so this can never swallow
            // the swipe before the pager sees it — which is what broke paging.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                if (zoom.isZoomed) zoom.reset() else zoom.scale = 2.5f
                            },
                        )
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> items.getOrNull(page)?.uri?.toString() ?: "page-$page" },
                    // Panning needs the drag; paging gets it back at natural size.
                    userScrollEnabled = !zoom.isZoomed,
                ) { page ->
                    val item = items.getOrNull(page)
                    if (item != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoom.scale,
                                        scaleY = zoom.scale,
                                        translationX = zoom.offset.x,
                                        translationY = zoom.offset.y,
                                    )
                                    .zoomGestures(zoom),
                                // A blank screen tells the user nothing. If the picture
                                // cannot be drawn, say why and offer the way out.
                                error = {
                                    NoPreviewCard(
                                        item = item,
                                        onOpenWith = { viewModel.openWith(item) },
                                    )
                                },
                            )
                            // Videos open in the player rather than sitting as a still frame.
                            if (item.isVideo) {
                                IconButton(
                                    onClick = { onPlayVideo(item.uri.toString()) },
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(72.dp)
                                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play video",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (controlsVisible && current != null) {
            ViewerTopBar(
                position = pagerState.currentPage + 1,
                total = items.size,
                isFavourite = current.isFavourite,
                onBack = onBack,
                onToggleFavourite = { viewModel.toggleFavourite(current) },
                modifier = Modifier.align(Alignment.TopStart),
            )
            ViewerActionBar(
                onShare = { viewModel.share(current) },
                onEdit = { onEdit(current.uri.toString(), current.isVideo) },
                onAdvancedEdit = { viewModel.advancedEdit(current) },
                onRename = { renameTarget = current },
                onMove = { moveTarget = current },
                onDelete = { viewModel.requestDelete(current) },
                onInfo = { infoTarget = current },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    renameTarget?.let { item ->
        RenameDialog(
            item = item,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.requestRename(item, name)
                renameTarget = null
            },
        )
    }

    moveTarget?.let { item ->
        FolderPickerDialog(
            title = "Move to folder",
            folders = folders,
            currentPath = item.relativePath ?: item.bucketName,
            confirmLabel = "Move",
            onDismiss = { moveTarget = null },
            onConfirm = { target ->
                viewModel.requestMove(item, target)
                moveTarget = null
            },
        )
    }

    infoTarget?.let { item ->
        FileInfoDialog(item = item, onDismiss = { infoTarget = null })
    }
}

@Composable
private fun ViewerTopBar(
    position: Int,
    total: Int,
    isFavourite: Boolean,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .statusBarsPadding()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            text = "$position / $total",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favourite",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ViewerActionBar(
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onAdvancedEdit: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .navigationBarsPadding()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerAction(Icons.Filled.Share, "Share", onShare)
        ViewerAction(Icons.Filled.Edit, "Edit", onEdit)
        ViewerAction(Icons.Filled.AutoFixHigh, "Advanced", onAdvancedEdit)
        ViewerAction(Icons.Filled.DriveFileRenameOutline, "Rename", onRename)
        ViewerAction(Icons.Filled.DriveFileMove, "Move", onMove)
        ViewerAction(Icons.Filled.Delete, "Delete", onDelete)
        ViewerAction(Icons.Filled.Info, "Info", onInfo)
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    // widthIn keeps seven labelled actions fitting on a narrow phone.
    Column(
        modifier = Modifier.widthIn(min = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RenameDialog(item: MediaItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(item.uri) { mutableStateOf(item.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shown instead of a blank screen when a picture cannot be drawn.
 *
 * The reason comes from [ImageDecodability], so the app says which of the two it is: this
 * device's Android is too old for the format, or Android has no decoder for it at all. The
 * way out is offered rather than left to be guessed at.
 */
@Composable
private fun NoPreviewCard(
    item: MediaItem,
    onOpenWith: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = item.displayName,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = ImageDecodability.reason(item.extension)
                ?: "This picture could not be read — the file may be damaged.",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        TextButton(onClick = onOpenWith, modifier = Modifier.padding(top = 8.dp)) {
            Text("Open with another app")
        }
    }
}
