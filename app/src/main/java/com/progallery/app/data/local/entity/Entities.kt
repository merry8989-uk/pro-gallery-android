package com.progallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Rows that reference media are keyed by [mediaKey] (the item's content URI),
 * never by the numeric MediaStore id: photo and video ids come from different
 * collections and collide, so a numeric key would let one item's state land on
 * another. See `MediaItem.mediaKey`.
 */

/** A media item the user marked as favourite. */
@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val mediaKey: String,
    val uri: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A user-created collection (distinct from an auto album derived from a folder). */
@Entity(tableName = "custom_albums")
data class CustomAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Membership of a media item in a custom album. */
@Entity(
    tableName = "custom_album_items",
    primaryKeys = ["albumId", "mediaKey"],
    indices = [Index("albumId")],
)
data class CustomAlbumItemEntity(
    val albumId: Long,
    val mediaKey: String,
    val uri: String,
)

/** A user-chosen cover for an album, keyed by album key (bucket id or "custom:<id>"). */
@Entity(tableName = "album_covers")
data class AlbumCoverEntity(
    @PrimaryKey val albumKey: String,
    val coverUri: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * An entry in the app-managed "Recently Deleted" trash. Files are copied into
 * app storage; [originalUri] and [originalRelativePath] let us restore them to
 * their previous location.
 */
@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUri: String,
    val trashUri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val originalBucketId: Long,
    val originalRelativePath: String?,
    val deletedAt: Long = System.currentTimeMillis(),
)

/** A media item hidden from the main gallery. */
@Entity(tableName = "hidden_items")
data class HiddenEntity(
    @PrimaryKey val mediaKey: String,
    val uri: String,
    val hiddenAt: Long = System.currentTimeMillis(),
)

/** A directory tree the user authorised for deep scanning via the SAF. */
@Entity(tableName = "scan_directories")
data class ScanDirectoryEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long? = null,
)
