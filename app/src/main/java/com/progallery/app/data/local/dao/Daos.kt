package com.progallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.progallery.app.data.local.entity.AlbumCoverEntity
import com.progallery.app.data.local.entity.CustomAlbumEntity
import com.progallery.app.data.local.entity.CustomAlbumItemEntity
import com.progallery.app.data.local.entity.FavouriteEntity
import com.progallery.app.data.local.entity.HiddenEntity
import com.progallery.app.data.local.entity.RecoveryHistoryEntity
import com.progallery.app.data.local.entity.ScanDirectoryEntity
import com.progallery.app.data.local.entity.TrashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {
    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteEntity>>

    @Query("SELECT mediaKey FROM favourites")
    suspend fun allKeys(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE mediaKey = :mediaKey)")
    suspend fun isFavourite(mediaKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE mediaKey = :mediaKey")
    suspend fun remove(mediaKey: String)

    @Query("DELETE FROM favourites WHERE mediaKey IN (:mediaKeys)")
    suspend fun removeAll(mediaKeys: List<String>)
}

@Dao
interface CustomAlbumDao {
    @Query("SELECT * FROM custom_albums ORDER BY createdAt DESC")
    fun observeAlbums(): Flow<List<CustomAlbumEntity>>

    @Insert
    suspend fun create(album: CustomAlbumEntity): Long

    @Query("DELETE FROM custom_albums WHERE id = :albumId")
    suspend fun delete(albumId: Long)

    @Query("SELECT * FROM custom_album_items WHERE albumId = :albumId")
    suspend fun itemsIn(albumId: Long): List<CustomAlbumItemEntity>

    @Query("SELECT * FROM custom_album_items WHERE albumId = :albumId")
    fun observeItems(albumId: Long): Flow<List<CustomAlbumItemEntity>>

    @Query("SELECT * FROM custom_album_items")
    fun observeAllItems(): Flow<List<CustomAlbumItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: CustomAlbumItemEntity)

    @Query("DELETE FROM custom_album_items WHERE albumId = :albumId AND mediaKey = :mediaKey")
    suspend fun removeItem(albumId: Long, mediaKey: String)
}

@Dao
interface AlbumCoverDao {
    @Query("SELECT * FROM album_covers")
    fun observeAll(): Flow<List<AlbumCoverEntity>>

    @Query("SELECT coverUri FROM album_covers WHERE albumKey = :key")
    suspend fun coverFor(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: AlbumCoverEntity)

    @Query("DELETE FROM album_covers WHERE albumKey = :key")
    suspend fun clear(key: String)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    suspend fun all(): List<TrashEntity>

    @Query("SELECT * FROM trash WHERE id = :id")
    suspend fun byId(id: Long): TrashEntity?

    @Insert
    suspend fun insert(entity: TrashEntity): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trash WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("SELECT * FROM trash WHERE deletedAt < :cutoff")
    suspend fun olderThan(cutoff: Long): List<TrashEntity>
}

@Dao
interface HiddenDao {
    @Query("SELECT mediaKey FROM hidden_items")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT mediaKey FROM hidden_items")
    suspend fun allKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenEntity)

    @Query("DELETE FROM hidden_items WHERE mediaKey = :mediaKey")
    suspend fun unhide(mediaKey: String)
}

@Dao
interface ScanDirectoryDao {
    @Query("SELECT * FROM scan_directories ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ScanDirectoryEntity>>

    @Query("SELECT * FROM scan_directories")
    suspend fun all(): List<ScanDirectoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: ScanDirectoryEntity)

    @Query("DELETE FROM scan_directories WHERE treeUri = :treeUri")
    suspend fun remove(treeUri: String)

    @Query("UPDATE scan_directories SET lastScannedAt = :timestamp WHERE treeUri = :treeUri")
    suspend fun markScanned(treeUri: String, timestamp: Long)
}

/**
 * Export history. Rows can be cleared and old ones purged without ever touching
 * the recovered files themselves.
 */
@Dao
interface RecoveryHistoryDao {

    @Query("SELECT * FROM recovery_history ORDER BY exportedAt DESC")
    fun observeAll(): Flow<List<RecoveryHistoryEntity>>

    @Query("SELECT * FROM recovery_history ORDER BY exportedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RecoveryHistoryEntity>

    @Insert
    suspend fun add(entity: RecoveryHistoryEntity): Long

    @Query("DELETE FROM recovery_history WHERE exportedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @Query("DELETE FROM recovery_history")
    suspend fun clear()
}
