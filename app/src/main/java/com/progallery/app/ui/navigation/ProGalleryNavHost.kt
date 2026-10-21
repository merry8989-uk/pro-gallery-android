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
import com.progallery.app.ui.screens.recovery.RecoveryScreen
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
import com.progallery.app.data.model.MediaType
import com.progallery.app.ui.screens.viewer.ViewerScreen
import com.progallery.app.ui.screens.videos.VideosScreen
import com.progallery.app.ui.screens.diagnostics.PermissionDiagnosticsScreen
import com.progallery.app.ui.screens.stream.StreamScreen
import com.progallery.app.ui.screens.stream.StreamPlayerScreen
import com.progallery.app.ui.screens.webdav.WebDavScreen
import com.progallery.app.ui.screens.webdav.WebDavPlayerScreen
import com.progallery.app.data.cloud.CloudKind
import com.progallery.app.ui.screens.cloud.CloudScreen
import com.progallery.app.ui.screens.cloud.CloudImageViewerScreen
import com.progallery.app.ui.screens.text.TextViewerScreen
import com.progallery.app.ui.screens.memories.MemoriesScreen

@Composable
fun ProGalleryNavHost(
    externalUri: String? = null,
    onExternalUriHandled: () -> Unit = {},
    /** Set when the app was launched from the memories reminder. */
    openMemories: Boolean = false,
    onMemoriesHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    // One place decides where a tapped item goes, by its kind.
    val openItem: (MediaItem) -> Unit = { item ->
        when {
            item.isImage -> navController.navigate(Routes.viewer(item.uri.toString(), item.mediaType.name))
            item.isAudio -> navController.navigate(Routes.audioPlayer(item.uri.toString()))
            item.isVideo -> navController.navigate(Routes.videoPlayer(item.uri.toString()))
            item.isPdf -> navController.navigate(Routes.pdfViewer(item.uri.toString()))
            // The app's own reader for text, JSON, CSV and code. Without it these went to
            // "open with another app", which often found nothing.
            item.isTextReadable -> navController.navigate(
                Routes.textViewer(item.uri.toString(), item.displayName),
            )
        }
    }

    // Opened from another app (we are the gallery): jump straight to that item.
    // Tapping the reminder opens Memories, which is the whole point of the notification.
    androidx.compose.runtime.LaunchedEffect(openMemories) {
        if (openMemories) {
            navController.navigate(Routes.MEMORIES)
            onMemoriesHandled()
        }
    }

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
        composable(Routes.MEMORIES) {
            MemoriesScreen(
                onBack = { navController.popBackStack() },
                onOpen = { uri, type ->
                    navController.navigate(Routes.viewer(uri, type.name))
                },
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
        composable(Routes.RECOVERY) {
            RecoveryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ANDROID_FOLDERS) {
            AndroidFoldersScreen(
                onBack = { navController.popBackStack() },
                // Results here are file:// items, so route by kind like everywhere else.
                onOpen = openItem,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissionDiagnostics = {
                    navController.navigate(Routes.PERMISSION_DIAGNOSTICS)
                },
            )
        }
        composable(Routes.PERMISSION_DIAGNOSTICS) {
            PermissionDiagnosticsScreen(
                onBack = { navController.popBackStack() },
                // Adding a folder is where the folder picker already lives, so the fix
                // takes the user to the real thing rather than a second copy of it.
                onPickFolder = { navController.navigate(Routes.DEEP_SCAN) },
            )
        }
        composable(
            route = "${Routes.TEXT_VIEWER}?uri={uri}&title={title}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            TextViewerScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                title = entry.arguments?.getString("title").orEmpty().ifBlank { "Text" },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CLOUD) {
            CloudScreen(
                onBack = { navController.popBackStack() },
                onOpen = { uri, kind ->
                    // Everything opens inside the app: pictures in a viewer built for a
                    // single cloud document, and video or audio in the URI player.
                    when (kind) {
                        CloudKind.IMAGE -> navController.navigate(Routes.cloudViewer(uri, uri))
                        else -> navController.navigate(Routes.streamPlayer(uri))
                    }
                },
            )
        }
        composable(
            route = "${Routes.CLOUD_VIEWER}?uri={uri}&title={title}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            CloudImageViewerScreen(
                uri = entry.arguments?.getString("uri") ?: "",
                title = entry.arguments?.getString("title").orEmpty()
                    .ifBlank { "Cloud picture" },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.WEBDAV) {
            WebDavScreen(
                onBack = { navController.popBackStack() },
                onPlay = { serverId, url ->
                    navController.navigate(Routes.webDavPlayer(serverId, url))
                },
            )
        }
        composable(
            route = "${Routes.WEBDAV_PLAYER}?server={server}&url={url}",
            arguments = listOf(
                navArgument("server") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType },
            ),
        ) { entry ->
            WebDavPlayerScreen(
                serverId = entry.arguments?.getString("server") ?: "",
                url = entry.arguments?.getString("url") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.STREAM) {
            StreamScreen(
                onBack = { navController.popBackStack() },
                onPlay = { url -> navController.navigate(Routes.streamPlayer(url)) },
            )
        }
        composable(
            route = "${Routes.STREAM_PLAYER}?url={url}&title={title}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            StreamPlayerScreen(
                uri = entry.arguments?.getString("url") ?: "",
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString("title").orEmpty().ifBlank { null },
            )
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
                type = entry.arguments?.getString("type")
                    ?.let { name -> MediaType.entries.firstOrNull { it.name == name } }
                    ?: MediaType.IMAGE,
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
