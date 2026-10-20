package com.progallery.app.ui.screens.videos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

@Composable
fun VideosScreen(
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onEdit: (String) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.VIDEO, favouritesOnly = false) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Videos",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = { onOpenPlayer(it.uri.toString()) },
            onEditItem = { onEdit(it.uri.toString()) },
            emptyTitle = "No videos found",
            emptySubtitle = "Videos on this device will appear here.",
        )
    }
}
