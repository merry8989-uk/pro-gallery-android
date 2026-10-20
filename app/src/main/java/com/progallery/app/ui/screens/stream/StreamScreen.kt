package com.progallery.app.ui.screens.stream

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.stream.StreamLink

/**
 * Paste a link and play it.
 *
 * The limits are stated on the screen rather than discovered by failing: the app plays
 * a **direct** media address, and a link to a web page is not one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val pending by viewModel.play.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        pending?.let {
            onPlay(it)
            viewModel.consumePlay()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stream from a link") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (recent.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearRecent() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear the list")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::setInput,
                    label = { Text("Media link") },
                    placeholder = { Text("https://example.com/clip.mp4") },
                    singleLine = true,
                    isError = state.error != null,
                    trailingIcon = {
                        if (state.input.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setInput("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.error != null) {
                item {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Button(
                    onClick = viewModel::submit,
                    enabled = state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("  Play")
                }
            }
            item {
                Text(
                    // Said up front, because this is the one thing people expect to work
                    // and it does not.
                    text = "The link must point straight at the media — a video, an audio " +
                        "file, an image, or an HLS/DASH stream (.m3u8 / .mpd). A link to a web " +
                        "page is not one, and will not play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recent.isNotEmpty()) {
                item {
                    Text(
                        text = "RECENT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(recent, key = { it.url }) { link ->
                    RecentLinkRow(
                        link = link,
                        onPlay = { viewModel.playAgain(link) },
                        onForget = { viewModel.forget(link) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentLinkRow(link: StreamLink, onPlay: () -> Unit, onForget: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.host,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${link.kind.label} · ${link.url}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onPlay) { Text("Play") }
        IconButton(onClick = onForget) {
            Icon(Icons.Filled.Close, contentDescription = "Remove from the list")
        }
    }
}
