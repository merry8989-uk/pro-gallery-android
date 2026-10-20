package com.progallery.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.vector.ImageVector
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.components.DashboardTile
import com.progallery.app.ui.components.DefaultGalleryPrompt
import com.progallery.app.ui.components.MediaCollection
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.navigation.Routes
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenViewer: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Counts were previously computed before permission was granted and stayed
    // at zero; refresh on every resume so they catch up.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    if (state.showDefaultGalleryPrompt) {
        DefaultGalleryPrompt(onDismiss = { viewModel.dismissDefaultGalleryPrompt() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ProGallery") },
                actions = {
                    IconButton(onClick = { onOpen(Routes.SEARCH) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
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
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (HomeEntries.isEmpty(state.enabledKinds)) {
                        // Everything is switched off: say so instead of showing an
                        // empty Home with no explanation.
                        DashboardTile(
                            icon = Icons.Filled.Tune,
                            title = "Nothing to show",
                            subtitle = "Every file category is switched off. Open Settings to turn one back on.",
                            onClick = { onOpen(Routes.SETTINGS) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (HomeEntries.showCollections(state.enabledKinds)) {
                        DashboardTile(
                            icon = Icons.Filled.Folder,
                            title = "All files",
                            subtitle = "${state.allFiles} files · ${state.other} other",
                            onClick = { onOpen(Routes.ALL_FILES) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // One row per pair of enabled types; a lone tile keeps half width.
                    HomeEntries.typeTiles(state.enabledKinds)
                        .map { kind ->
                            when (kind) {
                                MediaType.IMAGE -> HomeTile(
                                    Icons.Filled.PhotoLibrary, "Photos",
                                    "${state.photos} items", Routes.PHOTOS,
                                )
                                MediaType.VIDEO -> HomeTile(
                                    Icons.Filled.Videocam, "Videos",
                                    "${state.videos} items", Routes.VIDEOS,
                                )
                                MediaType.AUDIO -> HomeTile(
                                    Icons.Filled.MusicNote, "Audio",
                                    "${state.audio} items", Routes.AUDIO,
                                )
                                else -> HomeTile(
                                    Icons.Filled.Description, "Documents",
                                    "${state.documents} items", Routes.DOCUMENTS,
                                )
                            }
                        }
                        .chunked(2)
                        .forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    DashboardTile(
                        icon = Icons.Filled.Restore,
                        title = "Recovery",
                        subtitle = "Quick Scan: what the app can actually reach, and from where",
                        onClick = { onOpen(Routes.RECOVERY) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DashboardTile(
                        icon = Icons.Filled.Android,
                        title = "Android folders",
                        subtitle = "Android/media, this app's folder — and what is blocked",
                        onClick = { onOpen(Routes.ANDROID_FOLDERS) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardTile(
                            icon = Icons.Filled.Delete,
                            title = "Recently Deleted",
                            subtitle = "${state.trash} items",
                            onClick = { onOpen(Routes.TRASH) },
                            modifier = Modifier.weight(1f),
                        )
                        DashboardTile(
                            icon = Icons.Filled.TravelExplore,
                            title = "Deep Scan",
                            subtitle = "Find any file type",
                            onClick = { onOpen(Routes.DEEP_SCAN) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(
                    text = "Recent",
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    items(state.recent, key = { it.uri.toString() }) { item ->
                        com.progallery.app.ui.components.MediaThumbnail(
                            item = item,
                            selected = false,
                            selectionMode = false,
                            onClick = { onOpenViewer(item.uri.toString(), item.mediaType.name) },
                            onLongClick = {},
                        )
                    }
                }
            }
            }
        }
    }
}

/** One Home entry point, built only when its category is enabled. */
private data class HomeTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val route: String,
)
