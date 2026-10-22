package com.progallery.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.progallery.app.ui.navigation.ProGalleryNavHost
import com.progallery.app.ui.theme.ProGalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import com.progallery.app.data.memories.MemoriesNotifier
import com.progallery.app.data.settings.SettingsRepository
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    /**
     * A media URI handed to us by another app (ACTION_VIEW). Set when the user
     * has made ProGallery their gallery and opens a photo from elsewhere.
     */
    private var externalUri by mutableStateOf<String?>(null)

    /**
     * Set when the app was opened by tapping the memories reminder.
     *
     * Read from the intent rather than from a saved state: the reminder should open Memories
     * once, when it is tapped, and not again every time the app is brought back to the front.
     */
    private var openMemories by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalUri = intent?.data?.toString()
        openMemories = intent?.getBooleanExtra(MemoriesNotifier.EXTRA_OPEN_MEMORIES, false) == true

        setContent {
            // The theme the person chose, which until now reached nothing: this call had no
            // argument, so the app was always whatever the system was and the control was
            // decorative. `ThemeMode.isDark` is the one place that answers it.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val systemDark = isSystemInDarkTheme()
            ProGalleryTheme(darkTheme = settings?.themeMode?.isDark(systemDark) ?: systemDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ProGalleryNavHost(
                        externalUri = externalUri,
                        onExternalUriHandled = { externalUri = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalUri = intent.data?.toString()
        openMemories = intent.getBooleanExtra(MemoriesNotifier.EXTRA_OPEN_MEMORIES, false)
    }
}
