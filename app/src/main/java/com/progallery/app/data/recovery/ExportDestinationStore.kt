package com.progallery.app.data.recovery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Where the last export went, so the next one can offer to go there again. */
data class ExportDestination(
    val treeUri: String,
    val displayName: String,
)

private val Context.exportDestinationStore: DataStore<Preferences> by preferencesDataStore(
    name = "export_destination",
)

/**
 * Remembers the folder the last export was written to.
 *
 * **It is deliberately not a setting.** A tree URI is not a preference; it is a **permission
 * grant**, and the settings export is a file a person can share. Putting this in `AppSettings`
 * would put a grant into that file, which is the one thing the master prompt says must never be
 * exported ("do not export private file paths, access tokens, or sensitive URI grants").
 *
 * Keeping it here also means the settings-export completeness test keeps working: it requires every
 * field of `AppSettings` to be listed in the export, and a field that must not be listed would
 * break it. The constraint and the design agree instead of fighting.
 *
 * The grant itself is taken with `takePersistableUriPermission` when the folder is chosen, so this
 * URI is still usable after a restart — otherwise remembering it would remember nothing.
 */
@Singleton
class ExportDestinationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        val TREE_URI = stringPreferencesKey("tree_uri")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }

    val remembered: Flow<ExportDestination?> = context.exportDestinationStore.data.map { prefs ->
        val uri = prefs[TREE_URI]
        val name = prefs[DISPLAY_NAME]
        if (uri.isNullOrBlank()) null else ExportDestination(uri, name.orEmpty())
    }

    suspend fun current(): ExportDestination? = remembered.first()

    suspend fun remember(destination: ExportDestination) {
        context.exportDestinationStore.edit { prefs ->
            prefs[TREE_URI] = destination.treeUri
            prefs[DISPLAY_NAME] = destination.displayName
        }
    }

    /** Called when a remembered folder turns out to be gone or unreadable. */
    suspend fun forget() {
        context.exportDestinationStore.edit { prefs ->
            prefs.remove(TREE_URI)
            prefs.remove(DISPLAY_NAME)
        }
    }
}
