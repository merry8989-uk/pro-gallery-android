package com.progallery.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.progallery.app.ui.screens.albums.AlbumDetailScreen
import com.progallery.app.ui.screens.albums.AlbumsScreen
import com.progallery.app.ui.screens.deepscan.DeepScanScreen
import com.progallery.app.ui.screens.editor.photo.PhotoEditorScreen
import com.progallery.app.ui.screens.editor.video.VideoEditorScreen
import com.progallery.app.ui.screens.favourites.FavouritesScreen
import com.progallery.app.ui.screens.home.HomeScreen
import com.progallery.app.ui.screens.photos.PhotosScreen
import com.progallery.app.ui.screens.player.VideoPlayerScreen
import com.progallery.app.ui.screens.search.SearchScreen
import com.progallery.app.ui.screens.trash.TrashScreen
import com.progallery.app.ui.screens.viewer.ViewerScreen
import com.progallery.app.ui.screens.videos.VideosScreen

@Composable
fun ProGalleryNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = { route -> navController.navigate(route) },
                onOpenViewer = { uri, type -> navController.navigate(Routes.viewer(uri, type)) },
            )
        }
        composable(Routes.PHOTOS) {
            PhotosScreen(
                onBack = { navController.popBackStack() },
                onOpenViewer = { uri, type -> navController.navigate(Routes.viewer(uri, type)) },
                onEdit = { uri -> navController.navigate(Routes.photoEditor(uri)) },
            )
        }
        composable(Routes.ALBUMS) {
            AlbumsScreen(
                onBack = { navController.popBackStack() },
                onOpenAlbum = { id, name -> navController.navigate(Routes.albumDetail(id, name)) },
            )
        }
        composable(
            route = "${Routes.ALBUM_DETAIL}/{bucketId}/{name}",
            arguments = listOf(
                navArgument("bucketId") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) { entry ->
            AlbumDetailScreen(
                bucketId = entry.arguments?.getLong("bucketId") ?: 0L,
                albumName = entry.arguments?.getString("name") ?: "",
                onBack = { navController.popBackStack() },
                onOpenViewer = { uri, type -> navController.navigate(Routes.viewer(uri, type)) },
            )
        }
        composable(Routes.VIDEOS) {
            VideosScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = { uri -> navController.navigate(Routes.videoPlayer(uri)) },
                onEdit = { uri -> navController.navigate(Routes.videoEditor(uri)) },
            )
        }
        composable(Routes.FAVOURITES) {
            FavouritesScreen(
                onBack = { navController.popBackStack() },
                onOpenViewer = { uri, type -> navController.navigate(Routes.viewer(uri, type)) },
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEEP_SCAN) {
            DeepScanScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenViewer = { uri, type -> navController.navigate(Routes.viewer(uri, type)) },
            )
        }
        composable(
            route = "${Routes.VIEWER}?uri={uri}&type={type}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType; defaultValue = "IMAGE" },
            ),
        ) { entry ->
            ViewerScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.photoEditor(it)) },
            )
        }
        composable(
            route = "${Routes.VIDEO_PLAYER}?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            VideoPlayerScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.videoEditor(it)) },
            )
        }
        composable(
            route = "${Routes.PHOTO_EDITOR}?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            PhotoEditorScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.VIDEO_EDITOR}?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            VideoEditorScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
