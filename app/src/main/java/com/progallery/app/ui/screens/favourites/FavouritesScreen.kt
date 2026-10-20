package com.progallery.app.ui.screens.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

@Composable
fun FavouritesScreen(
    onBack: () -> Unit,
    onOpenViewer: (String, String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.ALL, favouritesOnly = true) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Favourites",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = { item ->
                if (item.isImage) onOpenViewer(item.uri.toString(), item.mediaType.name)
                else onOpenPlayer(item.uri.toString())
            },
            emptyTitle = "No favourites yet",
            emptySubtitle = "Tap the heart on any photo or video to keep it here.",
        )
    }
}
