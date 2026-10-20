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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progallery.app.ui.components.DashboardTile
import com.progallery.app.ui.components.MediaCollection
import com.progallery.app.ui.components.MediaPermissionGate
import com.progallery.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenViewer: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ProGallery") },
                actions = {
                    IconButton(onClick = { onOpen(Routes.SEARCH) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
            )
        },
    ) { padding ->
        MediaPermissionGate {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DashboardTile(
                        icon = Icons.Filled.Folder,
                        title = "All files",
                        subtitle = "${state.allFiles} files · ${state.other} other",
                        onClick = { onOpen(Routes.ALL_FILES) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardTile(
                            icon = Icons.Filled.PhotoLibrary,
                            title = "Photos",
                            subtitle = "${state.photos} items",
                            onClick = { onOpen(Routes.PHOTOS) },
                            modifier = Modifier.weight(1f),
                        )
                        DashboardTile(
                            icon = Icons.Filled.Videocam,
                            title = "Videos",
                            subtitle = "${state.videos} items",
                            onClick = { onOpen(Routes.VIDEOS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardTile(
                            icon = Icons.Filled.MusicNote,
                            title = "Audio",
                            subtitle = "${state.audio} items",
                            onClick = { onOpen(Routes.AUDIO) },
                            modifier = Modifier.weight(1f),
                        )
                        DashboardTile(
                            icon = Icons.Filled.Description,
                            title = "Documents",
                            subtitle = "${state.documents} items",
                            onClick = { onOpen(Routes.DOCUMENTS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
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
