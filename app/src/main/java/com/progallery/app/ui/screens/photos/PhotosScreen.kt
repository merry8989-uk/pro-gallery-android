package com.progallery.app.ui.screens.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

@Composable
fun PhotosScreen(
    onBack: () -> Unit,
    onOpenViewer: (String, String) -> Unit,
    onEdit: (String) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.IMAGE, favouritesOnly = false) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Photos",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = { onOpenViewer(it.uri.toString(), it.mediaType.name) },
            onEditItem = { item: MediaItem -> onEdit(item.uri.toString()) },
            emptyTitle = "No photos found",
            emptySubtitle = "Photos on this device will appear here.",
        )
    }
}
