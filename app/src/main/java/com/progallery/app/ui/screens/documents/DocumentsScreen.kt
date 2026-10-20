package com.progallery.app.ui.screens.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.DOCUMENT, favouritesOnly = false) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Documents",
            viewModel = viewModel,
            onBack = onBack,
            // Documents have no in-app preview: tapping hands them to the system.
            onOpenItem = {},
            emptyTitle = "No documents found",
            emptySubtitle = "PDFs, office files, text and ebooks you can see will appear here.",
        )
    }
}
