package com.progallery.app.ui.screens.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.screens.common.MediaBrowserScaffold
import com.progallery.app.ui.screens.common.MediaBrowserViewModel

@Composable
fun AudioScreen(
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.AUDIO, favouritesOnly = false) }
    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Audio",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = { onOpenPlayer(it.uri.toString()) },
            emptyTitle = "No audio found",
            emptySubtitle = "Music, voice notes and recordings will appear here.",
        )
    }
}
