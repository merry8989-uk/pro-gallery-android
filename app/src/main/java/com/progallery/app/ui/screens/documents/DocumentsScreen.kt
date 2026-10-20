package com.progallery.app.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.AllFilesAccessCard
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.DOCUMENT, favouritesOnly = false) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Documents",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = onOpen,
            // PDFs open in the in-app viewer; other documents go to the system.
            emptyTitle = "No documents found",
            emptySubtitle = "PDFs and other documents you can see will appear here.",
            header = { AllFilesAccessCard() },
        )
    }
}
