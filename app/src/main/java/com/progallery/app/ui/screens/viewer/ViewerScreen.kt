package com.progallery.app.ui.screens.viewer

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@Composable
fun ViewerScreen(
    uri: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val decodedUri = java.net.URLDecoder.decode(uri, "UTF-8")
    val initialIndex = items.indexOfFirst { it.uri.toString() == decodedUri }.coerceAtLeast(0)

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size.coerceAtLeast(1) })

    Box(modifier = Modifier.fillMaxSize().padding(0.dp)) {
        if (items.isEmpty()) {
            Text("Loading…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val item = items.getOrNull(page)
                if (item != null) {
                    ZoomableImage(item.uri.toString(), item.displayName)
                }
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${items.size}",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
            IconButton(
                onClick = { items.getOrNull(pagerState.currentPage)?.let { onEdit(it.uri.toString()) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White) }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
    }
}

@Composable
private fun ZoomableImage(model: Any?, description: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        offsetX += panChange.x
        offsetY += panChange.y
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = model,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .transformable(transformState),
        )
    }
}
