package com.progallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.recovery.RecoveryResult
import com.progallery.app.data.recovery.RecoveryStatus
import com.progallery.app.util.MediaFormatUtils
import com.progallery.app.data.settings.ThumbnailPolicy
import coil.request.CachePolicy
import com.progallery.app.data.recovery.RecoverySource

/**
 * A preview for one recovery result.
 *
 * Shows a real thumbnail wherever one can be decoded, and never leaves a card
 * blank: a file whose preview fails gets a broken-image placeholder, and a file
 * with no preview gets its own type icon and format label. A thumbnail or cached
 * preview is marked as such, because it is **not** the original image.
 */
@Composable
fun RecoveryThumbnail(
    result: RecoveryResult,
    modifier: Modifier = Modifier,
    policy: ThumbnailPolicy = ThumbnailPolicy(),
    showName: Boolean = false,
    showSize: Boolean = false,
) {
    // Whether this file's kind came from its bytes rather than its name. The recovery engine
    // records that as the source, so the thumbnail can honour the setting that governs it.
    val identifiedBySignature = result.source == RecoverySource.SIGNATURE
    val item = result.item
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when {
            // Only images and videos have pixels of their own here; audio artwork and
            // document pages are the browsing grid's job, not a recovery result's.
            item.isPreviewable && policy.allows(item, identifiedBySignature) ->
                ThumbnailOrFallback(item, policy)

            else -> FormatIcon(item)
        }

        if (item.isVideo && item.isPreviewable && policy.allows(item, identifiedBySignature)) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
        }

        // Honesty markers: these tiles are not the original file.
        when {
            result.isThumbnail -> Marker("THUMBNAIL")
            result.status == RecoveryStatus.PREVIEW_ONLY -> Marker("PREVIEW")
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            if (showName) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 4.dp),
                )
            }
            if (showSize && item.sizeBytes > 0) {
                Text(
                    text = MediaFormatUtils.formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** The real preview, with a placeholder while it loads and on failure. */
@Composable
private fun ThumbnailOrFallback(item: MediaItem, policy: ThumbnailPolicy) {
    val context = LocalContext.current
    var failed by remember(item.uri) { mutableStateOf(false) }

    if (failed) {
        // Decoding failed — say so instead of showing a blank tile.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = "The preview could not be decoded",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        return
    }

    val request = ImageRequest.Builder(context)
        .data(item.uri)
        .apply { if (item.isVideo) videoFrameMillis(1000) }
        // The quality the person chose, and whether to keep what comes back. Both are per request
        // because Coil's loader is a singleton built once at startup.
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
        onError = { failed = true },
    )
}

/**
 * Type icon plus the file's format, for anything without a visual preview.
 *
 * A `BoxScope` extension so the format label can be aligned inside the tile.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.FormatIcon(item: MediaItem) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = item.mediaType.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
    val label = item.extension.ifBlank { item.mimeType.substringAfterLast('/') }
    if (label.isNotBlank()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Marker(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
