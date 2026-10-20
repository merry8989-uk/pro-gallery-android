package com.progallery.app.ui.screens.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

/** Every kind in one list: images, videos, audio, documents, archives and other. */
@Composable
fun AllFilesScreen(
    onBack: () -> Unit,
    onOpenViewer: (String, String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.ALL, favouritesOnly = false) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "All files",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = { item ->
                if (item.isImage) {
                    onOpenViewer(item.uri.toString(), item.mediaType.name)
                } else {
                    onOpenPlayer(item.uri.toString())
                }
            },
            showSearch = true,
            emptyTitle = "Nothing found",
            emptySubtitle = "Files this app can see will appear here.",
        )
    }
}
