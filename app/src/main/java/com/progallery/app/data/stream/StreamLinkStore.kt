package com.progallery.app.data.stream

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

private val Context.streamStore: DataStore<Preferences> by preferencesDataStore(name = "progallery_streams")

/**
 * The links the user has streamed, most recent first.
 *
 * Only the address is stored, never anything about what was played — the kind is
 * re-derived on read, because [StreamLinks.parse] is pure and cheap. One entry per
 * address: pasting the same link again moves it to the top rather than adding a
 * duplicate.
 */
@Singleton
class StreamLinkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        val KEY = stringPreferencesKey("recent_links")

        /** Enough to be useful, few enough that the list stays a list. */
        const val LIMIT = 20

        const val SEPARATOR = "\n"
    }

    fun observe(): Flow<List<StreamLink>> = context.streamStore.data.map { preferences ->
        preferences[KEY]
            .orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { StreamLinks.parse(it).getOrNull() }
    }

    suspend fun remember(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        context.streamStore.edit { preferences ->
            val existing = preferences[KEY].orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
            val next = (listOf(clean) + existing.filterNot { it == clean }).take(LIMIT)
            preferences[KEY] = next.joinToString(SEPARATOR)
        }
    }

    suspend fun forget(url: String) {
        context.streamStore.edit { preferences ->
            val next = preferences[KEY].orEmpty().split(SEPARATOR)
                .filter { it.isNotBlank() && it != url }
            preferences[KEY] = next.joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        context.streamStore.edit { it.remove(KEY) }
    }
}
