package com.progallery.app.ui.screens.appmedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.appmedia.AppMediaFolder

/**
 * Which apps keep media on this device, and how much.
 *
 * A person wants to know "is my Telegram media here?" — not to read a folder tree. Each app is
 * one card, and the card says plainly which of three situations it is in: has media, installed
 * with none found, or not installed at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMediaScreen(
    onBack: () -> Unit,
    onBrowseAndroidMedia: () -> Unit,
    viewModel: AppMediaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App media") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item { Introduction(state, onBrowseAndroidMedia) }

                    if (state.withMedia.isNotEmpty()) {
                        item { Heading("MEDIA FOUND") }
                        items(state.withMedia, key = { it.packageName }) { app ->
                            AppCard(app, onBrowseAndroidMedia)
                        }
                    }

                    if (state.withoutMedia.isNotEmpty()) {
                        item { Heading("NOTHING FOUND") }
                        items(state.withoutMedia, key = { it.packageName }) { app ->
                            AppCard(app, onBrowseAndroidMedia)
                        }
                    }

                    state.error?.let { message ->
                        item {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Introduction(state: AppMediaUiState, onBrowse: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Messaging apps keep the photos and videos you receive in their own " +
                "folders. This shows which of them have media on this device, and how much.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // The platform rule, said plainly, because it decides whether any of this can be seen.
        state.mediaReachability?.let { reachability ->
            Text(
                text = reachability.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (reachability.reachable) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        OutlinedButton(
            onClick = onBrowse,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Text("  Browse Android media")
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppCard(app: AppMediaFolder, onBrowse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        app.hasMedia -> if (app.countCapped) "20,000+" else "${app.total}"
                        app.installed -> "installed"
                        else -> "not installed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (app.hasMedia) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                text = when {
                    app.hasMedia -> breakdown(app)
                    app.installed ->
                        "Installed, but no media folder was found here. Media appears once " +
                            "something has been received."
                    else -> "Not installed on this device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (app.hasMedia) {
                Text(
                    text = app.folderPath.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedButton(onClick = onBrowse, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Open these files")
                }
            }
        }
    }
}

/** "1,204 photos · 88 videos · 3 audio" — only the kinds that are actually there. */
private fun breakdown(app: AppMediaFolder): String {
    val parts = buildList {
        if (app.images > 0) add("${app.images} ${if (app.images == 1) "photo" else "photos"}")
        if (app.videos > 0) add("${app.videos} ${if (app.videos == 1) "video" else "videos"}")
        if (app.audio > 0) add("${app.audio} audio")
        if (app.documents > 0) add("${app.documents} documents")
        if (app.other > 0) add("${app.other} other")
    }
    return if (parts.isEmpty()) "Nothing countable here." else parts.joinToString(" · ")
}
