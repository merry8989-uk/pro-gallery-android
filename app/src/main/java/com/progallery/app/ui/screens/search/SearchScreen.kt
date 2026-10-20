package com.progallery.app.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.ALL, favouritesOnly = false) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Search",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = onOpen,
            showSearch = true,
            emptyTitle = "No matches",
            emptySubtitle = "Try a different file name, album or file type.",
        )
    }
}
