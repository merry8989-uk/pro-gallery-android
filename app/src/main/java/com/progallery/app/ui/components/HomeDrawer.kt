package com.progallery.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.progallery.app.ui.navigation.Routes
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum

/**
 * The sidebar.
 *
 * Three groups, so the list stays readable as it grows: the library on the device, the
 * cloud services, and the tools. Every cloud entry states what it does — and, where
 * there is a limit, what it does not — instead of leaving the user to find out.
 */
@Composable
fun HomeDrawer(
    drawerState: DrawerState,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            DrawerContents(
                onNavigate = { route ->
                    onNavigate(route)
                },
            )
        },
        content = content,
    )
}

@Composable
private fun DrawerContents(onNavigate: (String) -> Unit) {
    val context = LocalContext.current

    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.86f)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "ProGallery",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
            )

            DrawerSection("LIBRARY")
            // First in the list, because it is the one entry that shows something the user did
            // not go looking for.
            DrawerItem(Icons.Filled.AutoAwesome, "Memories", Routes.MEMORIES, onNavigate)
            // The apps whose media is otherwise invisible: WhatsApp, Telegram, Arattai, Hyped
            // and anything else that keeps files in a folder of its own.
            DrawerItem(Icons.Filled.Forum, "App media", Routes.APP_MEDIA, onNavigate)
            DrawerItem(Icons.Filled.Image, "Photos", Routes.PHOTOS, onNavigate)
            DrawerItem(Icons.Filled.VideoLibrary, "Videos", Routes.VIDEOS, onNavigate)
            DrawerItem(Icons.Filled.MusicNote, "Audio", Routes.AUDIO, onNavigate)
            DrawerItem(Icons.Filled.Description, "Documents", Routes.DOCUMENTS, onNavigate)
            DrawerItem(Icons.Filled.Folder, "All files", Routes.ALL_FILES, onNavigate)
            DrawerItem(Icons.Filled.Folder, "Albums", Routes.ALBUMS, onNavigate)
            DrawerItem(Icons.Filled.Favorite, "Favourites", Routes.FAVOURITES, onNavigate)
            DrawerItem(Icons.Filled.Restore, "Trash", Routes.TRASH, onNavigate)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DrawerSection("CLOUD")
            // Browsed inside the app. The services keep their own sign-in, so the app asks
            // for no password and there is nothing to log into again.
            DrawerItem(Icons.Filled.Cloud, "Browse cloud services", Routes.CLOUD, onNavigate)
            DrawerItem(Icons.Filled.Cloud, "WebDAV server", Routes.WEBDAV, onNavigate)
            Text(
                text = "Cloud folders open inside the app. Each service keeps its own " +
                    "sign-in, so nothing is signed into here, and a folder you choose once " +
                    "stays chosen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DrawerSection("TOOLS")
            DrawerItem(Icons.Filled.PlayArrow, "Stream from a link", Routes.STREAM, onNavigate)
            DrawerItem(Icons.Filled.Search, "Deep scan", Routes.DEEP_SCAN, onNavigate)
            DrawerItem(Icons.Filled.Restore, "Recovery", Routes.RECOVERY, onNavigate)
            DrawerItem(Icons.Filled.Folder, "Android folders", Routes.ANDROID_FOLDERS, onNavigate)
            DrawerItem(Icons.Filled.Search, "Search", Routes.SEARCH, onNavigate)
            DrawerItem(Icons.Filled.Settings, "Settings", Routes.SETTINGS, onNavigate)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DrawerSection("CONTACT")
            NavigationDrawerItem(
                icon = { androidx.compose.material3.Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                label = { Text("@osintgram_io") },
                selected = false,
                onClick = { openInstagram(context) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text(
                text = "For suggestions, feedback or a feature request.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )

            Column(modifier = Modifier.padding(bottom = 20.dp)) {}
        }
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    route: String,
    onNavigate: (String) -> Unit,
) {
    NavigationDrawerItem(
        icon = { androidx.compose.material3.Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = { onNavigate(route) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/**
 * Opens the contact handle.
 *
 * A plain web link, so it works whether or not the app is installed, and nothing happens
 * rather than a crash if there is no browser at all.
 */
private fun openInstagram(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/osintgram_io"))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
