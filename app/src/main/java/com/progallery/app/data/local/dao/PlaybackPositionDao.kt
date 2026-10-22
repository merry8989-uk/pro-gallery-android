package com.progallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.progallery.app.data.local.entity.PlaybackPositionEntity

/**
 * Where playback got to.
 *
 * Bounded on purpose. A position is a convenience, and a table that grows with every file ever
 * opened is a table that outgrows its usefulness — so the oldest entries are dropped once there
 * are more than a sensible number, and the count is asked for by the tests.
 */
@Dao
interface PlaybackPositionDao {

    @Query("SELECT * FROM playback_position WHERE mediaKey = :mediaKey")
    suspend fun find(mediaKey: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_position WHERE mediaKey = :mediaKey")
    suspend fun forget(mediaKey: String)

    @Query("SELECT COUNT(*) FROM playback_position")
    suspend fun count(): Int

    /** Keeps the most recently watched entries, drops the rest. */
    @Query(
        "DELETE FROM playback_position WHERE mediaKey NOT IN (" +
            "SELECT mediaKey FROM playback_position ORDER BY updatedAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM playback_position")
    suspend fun clear()
}
