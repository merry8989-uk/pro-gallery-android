package com.progallery.app.ui.screens.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.data.memories.MemoryGroup
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.MediaThumbnail
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.progallery.app.data.settings.ThumbnailPolicy
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

/**
 * The pictures worth seeing again today.
 *
 * **Why it exists.** A photo taken on 5 April 2018 is worth showing again on 5 April 2026 —
 * and the app can know that without being told, because the date is already in the file. There
 * is nothing to set up: the pictures are found, grouped by how long ago they were, and shown.
 *
 * The whole card is built from the same tile the rest of the app uses, so a memory of a WebP, a
 * video or a format with no decoder behaves exactly as it does everywhere else — an honest
 * fallback rather than a blank rectangle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    onBack: () -> Unit,
    onOpen: (uri: String, type: MediaType) -> Unit,
    viewModel: MemoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.open.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /**
     * Asked once, here, rather than as a setup step.
     *
     * Android 13 and later require permission to post any notification, so there is no way to
     * send the morning reminder without it — but it is asked for **in context**, on the screen
     * that explains what it is for, and the rest of the app works whether or not it is granted.
     * Asking again is harmless: the system answers immediately once a choice has been made.
     */
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pending) {
        pending?.let {
            onOpen(it.uri, it.type)
            viewModel.consumeOpen()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memories") },
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

                state.error != null -> CentredMessage(state.error!!)

                state.memories.isEmpty -> CentredMessage(
                    "Nothing to look back on today.\n\n" +
                        "Memories appear on the day a picture was taken, in a later year — so a " +
                        "photo from 5 April 2018 comes back on 5 April. There is nothing to set up.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item { Header(state) }

                    if (state.memories.onThisDay.isNotEmpty()) {
                        item { SectionHeading("ON THIS DAY") }
                        items(state.memories.onThisDay, key = { "day-${it.yearsAgo}" }) { group ->
                            MemoryCard(
                                group = group,
                                policy = state.thumbnailPolicy,
                                onOpen = { viewModel.open(group, it) },
                            )
                        }
                    }

                    if (state.memories.thisMonth.isNotEmpty()) {
                        item { SectionHeading("EARLIER THIS MONTH") }
                        items(state.memories.thisMonth, key = { "month-${it.yearsAgo}" }) { group ->
                            MemoryCard(
                                group = group,
                                policy = state.thumbnailPolicy,
                                onOpen = { viewModel.open(group, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: MemoriesUiState) {
    val count = state.memories.itemCount
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "  ${state.today.format(prettyDate())}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = when {
                state.memories.onThisDay.isEmpty() -> "$count to look back on this month."
                count == 1 -> "1 picture to look back on."
                else -> "$count pictures to look back on."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        // Said plainly, because this is what the notification permission is for — and because
        // it is the one thing about memories that cannot be worked out from the screen.
        Text(
            text = "A reminder arrives each morning at 7 when there is something to look back on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * One memory: a cover, how long ago it was, and the rest of the day's pictures beside it.
 *
 * The cover is the newest picture of that day, and the strip is everything else, so a day with
 * one photo and a day with forty read the same way.
 */
@Composable
private fun MemoryCard(
    group: MemoryGroup,
    policy: ThumbnailPolicy,
    onOpen: (MediaItem) -> Unit,
) {
    val cover = group.items.firstOrNull() ?: return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            MediaThumbnail(
                item = cover,
                selected = false,
                selectionMode = false,
                onClick = { onOpen(cover) },
                onLongClick = { onOpen(cover) },
                policy = policy,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "${group.yearsAgo} ${if (group.yearsAgo == 1) "year" else "years"} ago",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = buildString {
                        append(group.date.format(prettyDate()))
                        append(" · ")
                        append(if (group.items.size == 1) "1 picture" else "${group.items.size} pictures")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (group.items.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(group.items.drop(1), key = { it.mediaKey }) { item ->
                        MediaThumbnail(
                            item = item,
                            selected = false,
                            selectionMode = false,
                            onClick = { onOpen(item) },
                            onLongClick = { onOpen(item) },
                            policy = policy,
                            modifier = Modifier.size(84.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CentredMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "5 April 2018" — a date read the way it is spoken. */
private fun prettyDate(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
