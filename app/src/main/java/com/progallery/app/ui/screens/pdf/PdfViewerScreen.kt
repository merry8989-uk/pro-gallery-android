package com.progallery.app.ui.screens.pdf

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.ui.components.FileActionBar
import com.progallery.app.ui.components.FileActionSpec
import com.progallery.app.ui.components.FolderPickerDialog
import com.progallery.app.ui.components.ZoomState
import com.progallery.app.ui.components.zoomGestures
import com.progallery.app.util.ImmersiveSystemBars
import com.progallery.app.util.PdfPrinter

/**
 * In-app PDF viewer built on the platform [android.graphics.pdf.PdfRenderer].
 *
 * Tap toggles the controls, double tap zooms, pinch zooms, drag pans while
 * zoomed; swipe moves between pages. The bottom bar offers the requested
 * actions: share, copy, move, upload and print.
 */
@Composable
fun PdfViewerScreen(
    uri: String,
    onBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val zoom = remember { ZoomState() }

    var controlsVisible by remember { mutableStateOf(true) }
    var copyOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    ImmersiveSystemBars(hide = !controlsVisible)

    LaunchedEffect(uri) { viewModel.load(uri) }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onWriteConsent(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(state.pendingWrite) {
        state.pendingWrite?.let {
            writeLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    val pagerState = rememberPagerState(pageCount = { state.pageCount.coerceAtLeast(1) })
    LaunchedEffect(pagerState.currentPage) { zoom.reset() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.loading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )

            state.error != null -> Text(
                text = state.error!!,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                if (zoom.isZoomed) zoom.reset() else zoom.scale = 2f
                            },
                        )
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !zoom.isZoomed,
                ) { page ->
                    PdfPage(pageIndex = page, viewModel = viewModel, zoom = zoom)
                }
            }
        }

        if (controlsVisible && state.item != null) {
            val item = state.item!!
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
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
                    text = item.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.pageCount > 0) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${state.pageCount}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }

            FileActionBar(
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.45f)),
                actions = listOf(
                    FileActionSpec(Icons.Filled.Share, "Share") { viewModel.share(item) },
                    FileActionSpec(Icons.Filled.ContentCopy, "Copy") { copyOpen = true },
                    FileActionSpec(Icons.Filled.DriveFileMove, "Move") { moveOpen = true },
                    FileActionSpec(Icons.Filled.CloudUpload, "Upload") { viewModel.upload(item) },
                    FileActionSpec(Icons.Filled.Print, "Print") {
                        val started = PdfPrinter.print(context, item.uri, item.displayName)
                        if (!started) {
                            // No print service installed.
                            viewModel.upload(item)
                        }
                    },
                ),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val item = state.item
    if (copyOpen && item != null) {
        FolderPickerDialog(
            title = "Copy to folder",
            folders = folders,
            currentPath = item.relativePath ?: item.bucketName,
            confirmLabel = "Copy",
            onDismiss = { copyOpen = false },
            onConfirm = { target ->
                viewModel.requestCopy(item, target)
                copyOpen = false
            },
        )
    }
    if (moveOpen && item != null) {
        FolderPickerDialog(
            title = "Move to folder",
            folders = folders,
            currentPath = item.relativePath ?: item.bucketName,
            confirmLabel = "Move",
            onDismiss = { moveOpen = false },
            onConfirm = { target ->
                viewModel.requestMove(item, target)
                moveOpen = false
            },
        )
    }
}

@Composable
private fun PdfPage(pageIndex: Int, viewModel: PdfViewerViewModel, zoom: ZoomState) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) { bitmap = viewModel.renderPage(pageIndex) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val page = bitmap
        if (page == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
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
            )
        }
    }
}
