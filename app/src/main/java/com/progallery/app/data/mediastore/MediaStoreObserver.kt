package com.progallery.app.data.mediastore

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the system's media index and reports when it changes.
 *
 * Without this the in-memory index went stale: a photo taken in the camera app, a
 * file deleted elsewhere, or a rename only appeared after a manual refresh.
 *
 * One change often fires several callbacks, so signals are debounced and
 * conflated — a burst becomes a single refresh.
 */
@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        /** Wait for the index to go quiet before reporting a change. */
        const val DEBOUNCE_MS = 2_000L
    }

    /** Emits after the media index has changed and settled. */
    @OptIn(FlowPreview::class)
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }

        val resolver = context.contentResolver
        val watched = listOf(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        )
        watched.forEach { uri ->
            // A provider that refuses the registration must not crash the app.
            runCatching { resolver.registerContentObserver(uri, true, observer) }
        }

        awaitClose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
        .debounce(DEBOUNCE_MS)
        .conflate()
}
