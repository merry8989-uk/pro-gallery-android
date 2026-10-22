package com.progallery.app.data.playback

import com.progallery.app.data.local.dao.PlaybackPositionDao
import com.progallery.app.data.local.entity.PlaybackPositionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers where playback got to, per file.
 *
 * Three outcomes, and the middle one is the reason this is not simply an upsert:
 *
 * - **Watched to the end** → the position is **forgotten**, so the next open starts at the
 *   beginning rather than in the credits.
 * - **Barely started** → the position is **left alone**. Writing it would be pointless, and
 *   *clearing* it would throw away a real position the user had twenty minutes into the file.
 * - **Anywhere in between** → it is saved.
 *
 * Every database call is wrapped: a position is a convenience, and no convenience is worth a
 * crash in the player.
 */
@Singleton
class PlaybackPositionRepository @Inject constructor(
    private val dao: PlaybackPositionDao,
) {
    private companion object {
        /** Enough to cover the files anyone is actually part-way through. */
        const val KEEP = 500
    }

    /** Where to start, which is the remembered position or the beginning. */
    suspend fun resumeAt(mediaKey: String): Long {
        val saved = runCatching { dao.find(mediaKey) }.getOrNull() ?: return 0L
        return PlaybackPosition.resumeAt(saved.positionMs, saved.durationMs)
    }

    suspend fun save(mediaKey: String, positionMs: Long, durationMs: Long) {
        if (PlaybackPosition.isFinished(positionMs, durationMs)) {
            runCatching { dao.forget(mediaKey) }
            return
        }
        if (!PlaybackPosition.shouldSave(positionMs, durationMs)) return
        runCatching {
            dao.upsert(PlaybackPositionEntity(mediaKey, positionMs, durationMs))
            dao.prune(KEEP)
        }
    }

    suspend fun forget(mediaKey: String) {
        runCatching { dao.forget(mediaKey) }
    }
}
