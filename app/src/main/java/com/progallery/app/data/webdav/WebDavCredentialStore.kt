package com.progallery.app.data.webdav

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.webDavStore: DataStore<Preferences> by preferencesDataStore(name = "progallery_webdav")

/**
 * The WebDAV servers the user added, with their passwords.
 *
 * Kept in its **own** store, separate from settings, for two reasons: the password must
 * never reach the settings export, and a server list is not a preference. It lives in the
 * app's private storage, which the platform sandbox protects, and it is never written to
 * the diagnostics log.
 *
 * [observe] deliberately omits passwords — the UI lists servers, and has no reason to
 * hold a password it is not about to use.
 */
@Singleton
class WebDavCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        val KEY = stringPreferencesKey("servers")
    }

    private fun stored(): Flow<List<StoredWebDavServer>> =
        context.webDavStore.data.map { WebDavServerCodec.decode(it[KEY].orEmpty()) }

    /** The servers, without their passwords. */
    fun observe(): Flow<List<WebDavServer>> = stored().map { list -> list.map { it.server } }

    /** The password for one server, fetched only when it is needed. */
    suspend fun credentials(serverId: String): StoredWebDavServer? =
        stored().first().firstOrNull { it.server.id == serverId }

    suspend fun add(name: String, url: String, username: String, password: String): WebDavServer {
        val server = WebDavServer(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { url.trim() },
            url = WebDavXml.asCollection(url.trim()),
            username = username,
        )
        context.webDavStore.edit { preferences ->
            val existing = WebDavServerCodec.decode(preferences[KEY].orEmpty())
            preferences[KEY] = WebDavServerCodec.encode(existing + StoredWebDavServer(server, password))
        }
        return server
    }

    suspend fun remove(serverId: String) {
        context.webDavStore.edit { preferences ->
            val kept = WebDavServerCodec.decode(preferences[KEY].orEmpty())
                .filterNot { it.server.id == serverId }
            preferences[KEY] = WebDavServerCodec.encode(kept)
        }
    }
}
