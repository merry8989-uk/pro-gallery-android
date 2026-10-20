package com.progallery.app.ui.screens.viewer

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import com.progallery.app.util.ImmersiveSystemBars
import com.progallery.app.util.MediaFormatUtils

/**
 * Full-screen photo/video viewer.
 *
 * Gestures: single tap toggles the controls, double tap zooms in/out, pinch
 * zooms, and drag pans while zoomed. While zoomed the pager stops swiping so
 * panning is not stolen by page changes; leaving a page resets it to fit.
 */
@Composable
fun ViewerScreen(
    uri: String,
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
    val initialIndex = items.indexOfFirst { it.uri.toString() == decodedUri }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size.coerceAtLeast(1) },
    )

    var controlsVisible by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Stable key per item: without this the pager reuses a page's
                // composition for a different photo and zoom/pan leaks across.
                key = { page -> items.getOrNull(page)?.uri?.toString() ?: "page-$page" },
                userScrollEnabled = !zoomed,
            ) { page ->
                val item = items.getOrNull(page)
                if (item != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ZoomableImage(
                            model = item.uri,
                            description = item.displayName,
                            resetKey = item.uri.toString(),
                            isActive = page == pagerState.currentPage,
                            onTap = { controlsVisible = !controlsVisible },
                            onZoomChanged = { isZoomed ->
                                if (page == pagerState.currentPage) zoomed = isZoomed
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
                onRename = { renameTarget = current },
                onMove = { moveTarget = current },
                onDelete = { viewModel.requestDelete(current) },
                onInfo = { infoTarget = current },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
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
        MoveDialog(
            item = item,
            folders = folders,
            onDismiss = { moveTarget = null },
            onConfirm = { target ->
                viewModel.requestMove(item, target)
                moveTarget = null
            },
        )
    }

    infoTarget?.let { item ->
        InfoDialog(item = item, onDismiss = { infoTarget = null })
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
        ViewerAction(Icons.Filled.DriveFileRenameOutline, "Rename", onRename)
        ViewerAction(Icons.Filled.DriveFileMove, "Move", onMove)
        ViewerAction(Icons.Filled.Delete, "Delete", onDelete)
        ViewerAction(Icons.Filled.Info, "Info", onInfo)
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * A zoomable, pannable image. Tap detection lives on the container while the
 * transform gestures live on the image itself — keeping them on separate nodes
 * is what lets pinch work without the tap detector swallowing it.
 */
@Composable
private fun ZoomableImage(
    model: Any?,
    description: String,
    resetKey: Any?,
    isActive: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = newScale
        offset = if (newScale <= 1f) Offset.Zero else offset + panChange
    }

    // Report zoom so the pager can stop stealing horizontal drags.
    LaunchedEffect(scale) { onZoomChanged(scale > 1f) }

    // Returning to a page always shows the whole image again.
    LaunchedEffect(isActive) {
        if (!isActive) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(resetKey) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
                .transformable(transformState),
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

@Composable
private fun MoveDialog(
    item: MediaItem,
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!supported) {
                    Text(
                        "Moving files needs Android 11 or newer. On this version the platform does not allow an app to change a file's folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        "Current: ${item.relativePath ?: item.bucketName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    folders.forEach { folder ->
                        Text(
                            text = folder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(folder) }
                                .padding(vertical = 12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text("Or type a folder, e.g. Pictures/Keep") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(custom) },
                enabled = supported && custom.isNotBlank(),
            ) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Name", item.displayName)
                InfoRow("Type", item.mimeType.ifBlank { "—" })
                InfoRow("Size", MediaFormatUtils.formatBytes(item.sizeBytes))
                InfoRow("Dimensions", item.dimensionLabel)
                if (item.isVideo) {
                    InfoRow("Duration", MediaFormatUtils.formatDuration(item.durationMs))
                }
                InfoRow("Album", item.bucketName)
                InfoRow("Path", item.relativePath ?: "—")
                InfoRow("Modified", MediaFormatUtils.formatDate(item.dateModifiedSeconds))
                InfoRow("Source", if (item.source.name == "DEEP_SCAN") "Deep scan" else "MediaStore")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}
