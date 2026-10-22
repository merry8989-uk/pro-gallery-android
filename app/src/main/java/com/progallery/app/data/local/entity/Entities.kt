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
    /**
     * The item's actual kind. Nullable so the migration can add it without a
     * SQL default (which Room would have to match exactly); null means "infer
     * from [isVideo]".
     */
    val mediaType: String? = null,
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

/**
 * One export attempt, kept so the user can see what was recovered and where it
 * went.
 *
 * Only a human-readable destination label is stored — never the Storage Access
 * Framework tree URI, which carries a grant that must not be written to the
 * database.
 */
@Entity(tableName = "recovery_history")
data class RecoveryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val sourceLabel: String,
    val destinationLabel: String,
    /** "Exported" or "Failed". */
    val outcome: String,
    /** The failure reason, when there was one. */
    val detail: String? = null,
    val sizeBytes: Long = 0,
    val exportedAt: Long = System.currentTimeMillis(),
)

/**
 * A file the app found itself, outside the system's index.
 *
 * This is what makes the gallery able to show media that MediaStore does not hand over — the
 * photos and videos messaging apps keep under `Android/media`, which is where the user's
 * WhatsApp pictures live. The scan writes them here, and the grid merges them into its own
 * order, so they appear where their date puts them rather than in a separate list.
 *
 * Every column is a plain value: no paths are exposed beyond the file's own URI, and nothing
 * here carries an access grant.
 */
@Entity(tableName = "indexed_media")
data class IndexedMediaEntity(
    @PrimaryKey val mediaKey: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val dateTakenSeconds: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String?,
    val mediaType: String,
    val indexedAt: Long = System.currentTimeMillis(),
)

/**
 * Where playback got to, so a long video can be picked up again.
 *
 * Keyed by the media key — the same key the rest of the app identifies an item by — so a position
 * follows the file and not the screen it was opened from.
 *
 * `durationMs` is stored alongside rather than looked up, because the resume rules need it and
 * the file may not be readable when the question is asked.
 */
@Entity(tableName = "playback_position")
data class PlaybackPositionEntity(
    @PrimaryKey val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
