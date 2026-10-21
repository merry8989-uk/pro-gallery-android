package com.progallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.progallery.app.data.local.entity.IndexedMediaEntity

/**
 * The app's own record of the files it found outside the system's index.
 *
 * Read whole rather than paged: these are the files a scan turned up in a handful of app
 * folders, not a library of tens of thousands, and holding them in memory is what lets the grid
 * merge them into **any** sort order with the same comparator it is already using.
 */
@Dao
interface IndexedMediaDao {

    @Query("SELECT * FROM indexed_media")
    suspend fun all(): List<IndexedMediaEntity>

    @Query("SELECT COUNT(*) FROM indexed_media")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<IndexedMediaEntity>)

    @Query("DELETE FROM indexed_media")
    suspend fun clear()

    @Query("DELETE FROM indexed_media WHERE mediaKey IN (:keys)")
    suspend fun delete(keys: List<String>)

    /** Files under one app's folder, so removing an app's media removes exactly those rows. */
    @Query("DELETE FROM indexed_media WHERE relativePath LIKE :prefix")
    suspend fun deleteUnder(prefix: String)
}
