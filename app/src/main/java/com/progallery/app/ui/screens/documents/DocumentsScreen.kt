package com.progallery.app.ui.screens.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onOpenPdf: (String) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.configure(type = MediaType.DOCUMENT, favouritesOnly = false) }

    // Documents from other apps are hidden from MediaStore on Android 11+, so
    // the picker is the guaranteed way to open a PDF regardless of permissions.
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onOpenPdf(it.toString()) }
    }

    MediaPermissionGate {
        MediaBrowserScaffold(
            title = "Documents",
            viewModel = viewModel,
            onBack = onBack,
            onOpenItem = onOpen,
            emptyTitle = "No documents found",
            emptySubtitle = "Use \"Open a PDF\" below, or Deep Scan a folder.",
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AllFilesAccessCard()
                    Surface(tonalElevation = 1.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Open a PDF", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Pick any PDF on the device and read it in the built-in viewer.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                                Text("Choose")
                            }
                        }
                    }
                }
            },
        )
    }
}
