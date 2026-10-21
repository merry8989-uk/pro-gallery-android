package com.progallery.app.ui.screens.stickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.progallery.app.data.stickers.StickerKind
import com.progallery.app.data.stickers.Stickers
import com.progallery.app.util.MediaActions

/**
 * The stickers the app has found, shown so they can actually be seen.
 *
 * **Three things were wrong before this, and all three are fixed here.** The files were not in
 * the gallery at all (they are in an app folder, not the system's index); the tiles were drawn
 * on a flat colour, so transparent artwork — which is what a sticker is — could be invisible;
 * and a `.tgs` sticker is not a picture at all, so it was promised a preview it could never have.
 *
 * Hence the chequerboard: it is never the same colour as the artwork, so the shape shows whatever
 * colour the sticker is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerScreen(
    onBack: () -> Unit,
    onOpen: (entry: StickerEntry, inPlayer: Boolean) -> Unit,
    onScanAndroidMedia: () -> Unit,
    viewModel: StickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stickers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Centred { CircularProgressIndicator() }

                state.error != null -> Centred {
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                state.indexEmpty -> Centred {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No stickers found yet.\n\nStickers live in the folders " +
                                "messaging apps keep under Android/media, which the system does " +
                                "not hand over. Scanning that folder is what finds them.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(
                            onClick = onScanAndroidMedia,
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("Scan Android media") }
                    }
                }

                state.stickers.isEmpty() -> Centred {
                    Text(
                        text = "The scan found no stickers.\n\nOnly files in a folder named for " +
                            "stickers, or with a sticker-only extension, are listed here.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.stickers, key = { it.item.mediaKey }) { entry ->
                        StickerCell(
                            entry = entry,
                            onClick = { onOpen(entry, viewModel.opensInPlayer(entry)) },
                            onOpenWith = { MediaActions.launchOpen(entry.item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerCell(
    entry: StickerEntry,
    onClick: () -> Unit,
    onOpenWith: () -> Unit,
) {
    val showable = Stickers.canShow(entry.kind)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .chequerboard()
            .clickable { if (showable) onClick() else onOpenWith() },
        contentAlignment = Alignment.Center,
    ) {
        if (showable) {
            AsyncImage(
                model = entry.item.uri,
                contentDescription = entry.item.displayName,
                // Fit, not Crop: cropping a sticker cuts off exactly the outline that makes it
                // recognisable.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(10.dp),
            )
            if (entry.kind == StickerKind.VIDEO) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video sticker",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
        } else {
            // Not a picture, so no picture is promised — the format and the way out are.
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = entry.kind.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Stickers.reason(entry.kind)?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/**
 * A chequerboard, so a transparent sticker is visible.
 *
 * A sticker has no background of its own. On a single flat colour it can vanish — white line art
 * on a light tile, black on a dark one — which is exactly what "the stickers show nothing"
 * looked like. Two alternating greys are never the same colour as the artwork.
 */
private fun Modifier.chequerboard(cell: Dp = 10.dp): Modifier = drawBehind {
    val cellPx = cell.toPx()
    val columns = (size.width / cellPx).toInt() + 1
    val rows = (size.height / cellPx).toInt() + 1
    val light = Color(0xFFE8E8E8)
    val dark = Color(0xFFCFCFCF)

    for (row in 0 until rows) {
        for (column in 0 until columns) {
            drawRect(
                color = if ((row + column) % 2 == 0) light else dark,
                topLeft = Offset(column * cellPx, row * cellPx),
                size = Size(cellPx, cellPx),
            )
        }
    }
}
