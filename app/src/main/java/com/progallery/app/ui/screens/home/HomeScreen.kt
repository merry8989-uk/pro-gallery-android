package com.progallery.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.R
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.DashboardTile
import com.progallery.app.ui.components.DefaultGalleryPrompt
import com.progallery.app.ui.components.HomeDrawer
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.components.MediaThumbnail
import com.progallery.app.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenViewer: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    if (state.showDefaultGalleryPrompt) {
        DefaultGalleryPrompt(onDismiss = { viewModel.dismissDefaultGalleryPrompt() })
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    HomeDrawer(drawerState = drawerState, onNavigate = onOpen) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onOpen(Routes.SEARCH) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search files")
                        }
                        IconButton(onClick = { onOpen(Routes.SETTINGS) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
        ) { padding ->
            MediaPermissionGate {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    // **Hoisted out of the `LazyColumn`.** `remember` is a @Composable call and the
                    // `LazyColumn` body is a `LazyListScope`, which is not composable — declaring this
                    // inside it was a compile error, so the whole home screen did not build.
                    val typeTiles = remember(
                        state.enabledKinds,
                        state.photos,
                        state.videos,
                        state.audio,
                        state.documents,
                    ) {
                        HomeEntries.typeTiles(state.enabledKinds).map { kind ->
                            when (kind) {
                                MediaType.IMAGE -> HomeTile(Icons.Filled.PhotoLibrary, "Photos", "${state.photos} items", Routes.PHOTOS)
                                MediaType.VIDEO -> HomeTile(Icons.Filled.Videocam, "Videos", "${state.videos} items", Routes.VIDEOS)
                                MediaType.AUDIO -> HomeTile(Icons.Filled.MusicNote, "Audio", "${state.audio} items", Routes.AUDIO)
                                else -> HomeTile(Icons.Filled.Description, "Documents", "${state.documents} items", Routes.DOCUMENTS)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Your library",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "Photos, videos and files, all in one place.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (HomeEntries.isEmpty(state.enabledKinds)) {
                            item {
                                DashboardTile(
                                    icon = Icons.Filled.Tune,
                                    title = "Nothing to show",
                                    subtitle = "Every file category is switched off. Open Settings to turn one back on.",
                                    onClick = { onOpen(Routes.SETTINGS) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (HomeEntries.showCollections(state.enabledKinds)) {
                            item {
                                DashboardTile(
                                    icon = Icons.Filled.Folder,
                                    title = "All files",
                                    subtitle = "${state.allFiles} files · ${state.other} other",
                                    onClick = { onOpen(Routes.ALL_FILES) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        item { SectionHeading("Browse by type") }

                        items(typeTiles.chunked(2)) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                pair.forEach { tile ->
                                    DashboardTile(
                                        icon = tile.icon,
                                        title = tile.title,
                                        subtitle = tile.subtitle,
                                        onClick = { onOpen(tile.route) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        if (HomeEntries.showCollections(state.enabledKinds)) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    DashboardTile(
                                        icon = Icons.Filled.Folder,
                                        title = "Albums",
                                        subtitle = "${state.albums} folders",
                                        onClick = { onOpen(Routes.ALBUMS) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    DashboardTile(
                                        icon = Icons.Filled.Favorite,
                                        title = "Favourites",
                                        subtitle = "${state.favourites} items",
                                        onClick = { onOpen(Routes.FAVOURITES) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        item { SectionHeading("Tools & utilities") }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DashboardTile(
                                    icon = Icons.Filled.TravelExplore,
                                    title = "Deep Scan",
                                    subtitle = "Find files",
                                    onClick = { onOpen(Routes.deepScan()) },
                                    modifier = Modifier.weight(1f),
                                )
                                DashboardTile(
                                    icon = Icons.Filled.Restore,
                                    title = "Recovery",
                                    subtitle = "Check recoverable files",
                                    onClick = { onOpen(Routes.recovery()) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DashboardTile(
                                    icon = Icons.Filled.PlayArrow,
                                    title = "Stream",
                                    subtitle = "Play a media link",
                                    onClick = { onOpen(Routes.STREAM) },
                                    modifier = Modifier.weight(1f),
                                )
                                DashboardTile(
                                    icon = Icons.Filled.Android,
                                    title = "Android folders",
                                    subtitle = "Browse app folders",
                                    onClick = { onOpen(Routes.ANDROID_FOLDERS) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DashboardTile(
                                    icon = Icons.Filled.Delete,
                                    title = "Recently Deleted",
                                    subtitle = "${state.trash} items",
                                    onClick = { onOpen(Routes.TRASH) },
                                    modifier = Modifier.weight(1f),
                                )
                                DashboardTile(
                                    icon = Icons.Filled.AutoAwesome,
                                    title = "Memories",
                                    subtitle = "On this day",
                                    onClick = { onOpen(Routes.MEMORIES) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        item { SectionHeading("Recent") }
                        if (state.recent.isEmpty()) {
                            item {
                                Text(
                                    text = "Your recent items will appear here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        } else {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                ) {
                                    items(state.recent, key = { it.uri.toString() }) { item ->
                                        MediaThumbnail(
                                            item = item,
                                            selected = false,
                                            selectionMode = false,
                                            onClick = { onOpenViewer(item.uri.toString(), item.mediaType.name) },
                                            onLongClick = {},
                                            modifier = Modifier.width(116.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** One Home entry point, built only when its category is enabled. */
private data class HomeTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val route: String,
)
