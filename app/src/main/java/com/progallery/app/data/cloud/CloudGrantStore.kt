package com.progallery.app.data.cloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cloudGrantStore: DataStore<Preferences> by preferencesDataStore(name = "progallery_cloud")

/** A provider folder the user has granted once. */
data class GrantedCloudTree(
    val treeUri: String,
    val title: String,
)

/**
 * Remembers which provider folders have been granted.
 *
 * The platform persists the *permission*; this remembers *which* trees were granted and what
 * to call them, so the Cloud screen can list them as ready to browse. Without it the user
 * would be asked to pick the same folder again every visit — which is exactly the "why am I
 * signing in again" problem.
 *
 * Only the tree address and its display name are stored. No credential of any kind is kept
 * here, because the app never receives one: the provider app owns the account.
 */
@Singleton
class CloudGrantStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        val KEY = stringPreferencesKey("granted_trees")
        const val FIELD = "\t"

        /** Nothing may carry a tab or a newline into a line-based store. */
        fun sanitize(value: String) = value.replace(FIELD, " ").replace("\n", " ").replace("\r", " ")
    }

    fun observe(): Flow<List<GrantedCloudTree>> = context.cloudGrantStore.data.map { preferences ->
        decode(preferences[KEY].orEmpty())
    }

    suspend fun remember(treeUri: String, title: String) {
        context.cloudGrantStore.edit { preferences ->
            val existing = decode(preferences[KEY].orEmpty()).filterNot { it.treeUri == treeUri }
            val next = existing + GrantedCloudTree(treeUri, title)
            preferences[KEY] = encode(next)
        }
    }

    suspend fun forget(treeUri: String) {
        context.cloudGrantStore.edit { preferences ->
            val next = decode(preferences[KEY].orEmpty()).filterNot { it.treeUri == treeUri }
            preferences[KEY] = encode(next)
        }
    }

    internal fun encode(trees: List<GrantedCloudTree>): String =
        trees.joinToString("\n") { "${sanitize(it.treeUri)}$FIELD${sanitize(it.title)}" }

    internal fun decode(raw: String): List<GrantedCloudTree> = raw
        .split("\n")
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 2) return@mapNotNull null
            val uri = parts[0].trim()
            if (uri.isEmpty()) return@mapNotNull null
            GrantedCloudTree(treeUri = uri, title = parts[1].ifBlank { uri })
        }
}
