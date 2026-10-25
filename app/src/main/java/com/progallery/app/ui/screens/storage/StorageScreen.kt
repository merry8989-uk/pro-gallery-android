package com.progallery.app.ui.screens.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.model.MediaType
import com.progallery.app.data.storage.FolderShare
import com.progallery.app.data.storage.KindShare
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.util.MediaFormatUtils
import androidx.compose.foundation.clickable

/**
 * Where the space went.
 *
 * The numbers are what the app can see, and the screen says so **above** them rather than in a
 * footnote — a total that looked like the whole device while being a fraction of it would be worse
 * than no total, because it would look like an answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    onOpenTree: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPermissionGate {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Storage") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            // Read into a local first: `state` is a delegated property, so Kotlin will not
            // smart-cast `state.summary` and every use of it would need a `!!`.
            val summary = state.summary
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                summary == null || summary.isEmpty -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Nothing to add up yet. The app counts what it can read, and the Access " +
                            "screen says what it has been allowed to reach.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Surface(tonalElevation = 1.dp) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(
                                        text = "${summary.totalCount} files · " +
                                            MediaFormatUtils.formatBytes(summary.totalBytes),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "This is what the app can read, not the whole " +
                                            "device. Android keeps part of every phone's storage " +
                                            "private, and no permission unlocks it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        item {
                            SectionHeader("By kind")
                        }
                        items(summary.byKind) { share ->
                            KindRow(share)
                        }

                        item {
                            HorizontalDivider()
                            SectionHeader("Largest folders")
                        }
                        items(summary.largestFolders) { folder ->
                            FolderRow(folder)
                        }

                        if (summary.inTrashCount > 0) {
                            item {
                                HorizontalDivider()
                                SectionHeader("In the trash")
                                Text(
                                    text = "${summary.inTrashCount} files · " +
                                        MediaFormatUtils.formatBytes(summary.inTrashBytes) +
                                        " — recoverable, because the app is holding them.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }

                        item {
                            HorizontalDivider()
                            Text(
                                text = "The file tree shows the same files folder by folder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                                    .fillMaxWidth()
                                    .clickableText(onOpenTree),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun KindRow(share: KindShare) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label(share.kind), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${share.count} · ${MediaFormatUtils.formatBytes(share.bytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The bar is the share of everything the app can see, which is the comparison that answers
        // "what is using my space" — not a share of the largest row.
        LinearProgressIndicator(
            progress = { share.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun FolderRow(folder: FolderShare) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(folder.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${folder.count} · ${MediaFormatUtils.formatBytes(folder.bytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { folder.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

private fun label(kind: MediaType): String = when (kind) {
    MediaType.IMAGE -> "Images"
    MediaType.VIDEO -> "Videos"
    MediaType.AUDIO -> "Audio"
    MediaType.DOCUMENT -> "Documents"
    MediaType.ARCHIVE -> "Archives"
    MediaType.OTHER -> "Other"
    MediaType.ALL -> "Everything"
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
