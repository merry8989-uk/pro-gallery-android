package com.progallery.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.model.ViewMode
import com.progallery.app.util.MediaFormatUtils
import androidx.paging.compose.LazyPagingItems
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import com.progallery.app.data.settings.ThumbnailPolicy
import com.progallery.app.data.model.ImageDecodability
import coil.request.CachePolicy

/**
 * A single grid thumbnail.
 *
 * Respects the settings: with thumbnails off it shows the file-type icon, and
 * the name/size strip appears only when those are enabled — a tile is never
 * blank.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    policy: ThumbnailPolicy = ThumbnailPolicy(),
    showName: Boolean = false,
    showSize: Boolean = false,
    showDetails: Boolean = false,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        /** What the file *is*, when no real preview is available. Never a blank tile. */
        @Composable
        fun typeIcon() {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.mediaType.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (!showName && item.extension.isNotEmpty()) {
                Text(
                    text = item.extension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp),
                )
            }
            // An image with no preview on this device says so, rather than looking like a
            // file that simply failed to load.
            if (item.isImage && !ImageDecodability.canRender(item.extension)) {
                Text(
                    text = "no preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
                )
            }
        }

        when {
            // Audio artwork and a PDF first page do not come from the file's pixels, so
            // they are produced separately and may legitimately be unavailable.
            policy.needsEmbeddedPreview(item) -> {
                val preview by produceState<ImageBitmap?>(null, item.uri, policy) {
                    value = MediaPreviews.load(context, item)
                }
                val image = preview
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    typeIcon()
                }
            }

            policy.allows(item) -> {
                val request = ImageRequest.Builder(context)
                    .data(item.uri)
                    .apply { if (item.isVideo) videoFrameMillis(1000) }
                    // The same quality and caching the recovery grid uses: one setting, honoured
                    // wherever a preview is drawn rather than only where it was first wired.
                    .size(policy.maxDimension)
                    .memoryCachePolicy(if (policy.cache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .diskCachePolicy(if (policy.cache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .crossfade(true)
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = "Video",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                    )
                }
            }

            else -> typeIcon()
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            if (item.isVideo && showName) {
                Text(
                    text = MediaFormatUtils.formatDuration(item.durationMs),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 4.dp)
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (showName) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            if (showName && showSize) {
                Text(
                    text = MediaFormatUtils.formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            // Only where the values are actually known, so the line is never invented.
            if (showName && showDetails) {
                val detail = detailLine(item)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }

        if (item.isFavourite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Favourite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp),
            )
        }
        if (selectionMode) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
            )
        }
    }
}

/**
 * Pixel size and duration, when the app actually knows them.
 *
 * Returns null rather than a placeholder, so the line is never a made-up value.
 */
private fun detailLine(item: MediaItem): String? {
    val parts = ArrayList<String>(2)
    if (item.width > 0 && item.height > 0) parts += "${item.width}×${item.height}"
    if (item.durationMs > 0) parts += MediaFormatUtils.formatDuration(item.durationMs)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Grid or list rendering of a media collection. */
/**
 * The paged sibling of [MediaCollection].
 *
 * Only the container differs: tiles and rows are the same composables, so a paged
 * grid looks and behaves exactly like a cached one. Items are flagged as favourites
 * as they are drawn, which is why a favourite toggle does not disturb the pager.
 */
@Composable
fun PagedMediaCollection(
    items: LazyPagingItems<MediaItem>,
    columns: Int,
    viewMode: ViewMode,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    favouriteKeys: Set<String>,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    policy: ThumbnailPolicy = ThumbnailPolicy(),
    showDetails: Boolean = false,
    showNames: Boolean = false,
    showSize: Boolean = false,
) {
    if (viewMode == ViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                count = items.itemCount,
                // Keyed by the item's identity, so a reload does not shuffle the
                // tiles that are already on screen.
                key = { index -> items.peek(index)?.mediaKey ?: "pending-$index" },
            ) { index ->
                val item = items[index] ?: return@items
                MediaThumbnail(
                    item = item.copy(isFavourite = item.mediaKey in favouriteKeys),
                    selected = item.mediaKey in selectedIds,
                    selectionMode = selectionMode,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    policy = policy,
                    showDetails = showDetails,
                    showName = showNames,
                    showSize = showSize,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                count = items.itemCount,
                key = { index -> items.peek(index)?.mediaKey ?: "pending-$index" },
            ) { index ->
                val item = items[index] ?: return@items
                MediaListRow(
                    item = item.copy(isFavourite = item.mediaKey in favouriteKeys),
                    selected = item.mediaKey in selectedIds,
                    selectionMode = selectionMode,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    policy = policy,
                    showDetails = showDetails,
                    showName = showNames,
                    showSize = showSize,
                )
            }
        }
    }
}

@Composable
fun MediaCollection(
    items: List<MediaItem>,
    columns: Int,
    viewMode: ViewMode,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    policy: ThumbnailPolicy = ThumbnailPolicy(),
    showDetails: Boolean = false,
    showNames: Boolean = false,
    showSize: Boolean = false,
) {
    if (viewMode == ViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items, key = { it.uri.toString() }) { item ->
                MediaThumbnail(
                    item = item,
                    selected = item.mediaKey in selectedIds,
                    selectionMode = selectionMode,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    policy = policy,
                    showDetails = showDetails,
                    showName = showNames,
                    showSize = showSize,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items, key = { it.uri.toString() }) { item ->
                MediaListRow(
                    item = item,
                    selected = item.mediaKey in selectedIds,
                    selectionMode = selectionMode,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    policy = policy,
                    showDetails = showDetails,
                    showName = showNames,
                    showSize = showSize,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaListRow(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    policy: ThumbnailPolicy = ThumbnailPolicy(),
    showDetails: Boolean = false,
    showName: Boolean,
    showSize: Boolean,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaThumbnail(
                item = item,
                selected = selected,
                selectionMode = false,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier.size(56.dp),
                policy = policy,
                showDetails = showDetails,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                if (showName) {
                    Text(
                        text = item.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (showSize || item.isPreviewable) {
                    Text(
                        text = "${item.dimensionLabel} • ${MediaFormatUtils.formatBytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selectionMode && selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Contextual action bar shown when one or more items are selected. */
@Composable
fun SelectionToolbar(
    count: Int,
    totalCount: Int,
    allFavourite: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    showOpenWith: Boolean,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onFavourite: () -> Unit,
    onEdit: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Without this the actions sit underneath the system navigation
                // bar and cannot be tapped on gesture- and button-nav devices.
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                }
                Text(
                    text = "$count selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (count < totalCount) {
                    TextButton(onClick = onSelectAll) { Text("Select all") }
                }
            }
            // Same icon-and-label treatment as the full-screen viewer's action bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showOpenWith) {
                    SelectionAction(
                        icon = Icons.Filled.OpenInNew,
                        label = "Open in",
                        onClick = onOpenWith,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (count == 1) {
                    SelectionAction(
                        icon = Icons.Filled.Edit,
                        label = "Edit",
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    )
                }
                SelectionAction(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )
                SelectionAction(
                    icon = if (allFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = "Fav",
                    onClick = onFavourite,
                    modifier = Modifier.weight(1f),
                )
                SelectionAction(
                    icon = Icons.Filled.PlaylistAdd,
                    label = "Album",
                    onClick = onAddToAlbum,
                    modifier = Modifier.weight(1f),
                )
                SelectionAction(
                    icon = Icons.Filled.VisibilityOff,
                    label = "Hide",
                    onClick = onHide,
                    modifier = Modifier.weight(1f),
                )
                SelectionAction(
                    icon = Icons.Filled.Delete,
                    label = "Delete",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One labelled action, sized so the tap target is comfortable on a phone. */
@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A representative icon for a file kind. */
fun MediaType.icon(): ImageVector = when (this) {
    MediaType.IMAGE -> Icons.Filled.Image
    MediaType.VIDEO -> Icons.Filled.Movie
    MediaType.AUDIO -> Icons.Filled.MusicNote
    MediaType.DOCUMENT -> Icons.Filled.Description
    MediaType.ARCHIVE -> Icons.Filled.FolderZip
    MediaType.OTHER -> Icons.Filled.InsertDriveFile
    MediaType.ALL -> Icons.Filled.Folder
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Small reusable tile used on the home dashboard. */
@Composable
fun DashboardTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.BrokenImage, contentDescription = null)
            Text(
                text = message,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
