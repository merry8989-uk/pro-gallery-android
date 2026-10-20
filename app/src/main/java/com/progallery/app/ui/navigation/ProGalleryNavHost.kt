package com.progallery.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.progallery.app.ui.screens.albums.AlbumDetailScreen
import com.progallery.app.ui.screens.albums.AlbumsScreen
import com.progallery.app.ui.screens.albums.CustomAlbumDetailScreen
import com.progallery.app.data.model.MediaItem
import com.progallery.app.ui.screens.audio.AudioPlayerScreen
import com.progallery.app.ui.screens.audio.AudioScreen
import com.progallery.app.ui.screens.documents.DocumentsScreen
import com.progallery.app.ui.screens.editor.audio.AudioEditorScreen
import com.progallery.app.ui.screens.pdf.PdfViewerScreen
import com.progallery.app.ui.screens.androidfolders.AndroidFoldersScreen
import com.progallery.app.ui.screens.settings.SettingsScreen
import com.progallery.app.ui.screens.files.AllFilesScreen
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
fun ProGalleryNavHost(
    externalUri: String? = null,
    onExternalUriHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    // One place decides where a tapped item goes, by its kind.
    val openItem: (MediaItem) -> Unit = { item ->
        when {
            item.isImage -> navController.navigate(Routes.viewer(item.uri.toString(), item.mediaType.name))
            item.isAudio -> navController.navigate(Routes.audioPlayer(item.uri.toString()))
            item.isVideo -> navController.navigate(Routes.videoPlayer(item.uri.toString()))
            item.isPdf -> navController.navigate(Routes.pdfViewer(item.uri.toString()))
        }
    }

    // Opened from another app (we are the gallery): jump straight to that item.
    androidx.compose.runtime.LaunchedEffect(externalUri) {
        if (externalUri != null) {
            navController.navigate(Routes.viewer(externalUri, "IMAGE"))
            onExternalUriHandled()
        }
    }

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
        composable(Routes.AUDIO) {
            AudioScreen(
                onBack = { navController.popBackStack() },
                onOpenPlayer = { uri -> navController.navigate(Routes.audioPlayer(uri)) },
            )
        }
        composable(Routes.DOCUMENTS) {
            DocumentsScreen(
                onBack = { navController.popBackStack() },
                onOpen = openItem,
                onOpenPdf = { uri -> navController.navigate(Routes.pdfViewer(uri)) },
            )
        }
        composable(Routes.ALL_FILES) {
            AllFilesScreen(
                onBack = { navController.popBackStack() },
                onOpen = openItem,
            )
        }
        composable(Routes.ALBUMS) {
            AlbumsScreen(
                onBack = { navController.popBackStack() },
                onOpenAlbum = { id, name -> navController.navigate(Routes.albumDetail(id, name)) },
                onOpenCustomAlbum = { id, name -> navController.navigate(Routes.customAlbumDetail(id, name)) },
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
        composable(
            route = "${Routes.CUSTOM_ALBUM_DETAIL}/{albumId}/{name}",
            arguments = listOf(
                navArgument("albumId") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) { entry ->
            CustomAlbumDetailScreen(
                albumId = entry.arguments?.getLong("albumId") ?: 0L,
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
                onOpen = openItem,
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEEP_SCAN) {
            DeepScanScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ANDROID_FOLDERS) {
            AndroidFoldersScreen(
                onBack = { navController.popBackStack() },
                // Results here are file:// items, so route by kind like everywhere else.
                onOpen = openItem,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpen = openItem,
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
                onEdit = { targetUri, isVideo ->
                    navController.navigate(
                        if (isVideo) Routes.videoEditor(targetUri) else Routes.photoEditor(targetUri),
                    )
                },
                onPlayVideo = { targetUri -> navController.navigate(Routes.videoPlayer(targetUri)) },
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
            route = "${Routes.PDF_VIEWER}?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            PdfViewerScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.AUDIO_PLAYER}?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            AudioPlayerScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() },
                onEdit = { targetUri, cut ->
                    navController.navigate(Routes.audioEditor(targetUri, cut))
                },
            )
        }
        composable(
            route = "${Routes.AUDIO_EDITOR}?uri={uri}&cut={cut}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("cut") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            AudioEditorScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                startInCutMode = entry.arguments?.getBoolean("cut") ?: false,
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
