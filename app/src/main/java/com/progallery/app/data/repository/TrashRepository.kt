package com.progallery.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.progallery.app.data.local.dao.TrashDao
import com.progallery.app.data.local.entity.TrashEntity
import com.progallery.app.data.model.MediaItem
import com.progallery.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A real, app-managed "Recently Deleted" system. Deletion moves a copy of the
 * bytes into app-private storage and records where they came from; the original
 * MediaStore entry is then removed through the platform's consent flow by the
 * UI layer. Restore puts the bytes back as a new MediaStore entry.
 *
 * Only valid Android APIs are used — no direct filesystem access to other apps.
 */
/** A media item that has been copied into the trash, and its trash row id. */
data class TrashedMedia(val trashId: Long, val item: MediaItem)

@Singleton
class TrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao,
) {

    private val trashDir: File get() = File(context.filesDir, "trash").apply { mkdirs() }

    fun observeTrash(): Flow<List<TrashEntity>> = trashDao.observeAll()

    suspend fun list(): List<TrashEntity> = withContext(Dispatchers.IO) { trashDao.all() }

    /**
     * Copies items into the trash and records their origin. Returns the entries
     * that were successfully stored, so the caller only ever deletes originals
     * whose bytes are safely copied.
     */
    suspend fun moveToTrash(items: List<MediaItem>): List<TrashedMedia> = withContext(Dispatchers.IO) {
        val recorded = ArrayList<TrashedMedia>()
        for (item in items) {
            val destination = File(trashDir, "${System.currentTimeMillis()}_${item.displayName}")
            val copied = FileUtils.copyToFile(context, item.uri, destination) ?: continue
            val id = trashDao.insert(
                TrashEntity(
                    originalUri = item.uri.toString(),
                    trashUri = Uri.fromFile(copied).toString(),
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    isVideo = item.isVideo,
                    sizeBytes = copied.length(),
                    originalBucketId = item.bucketId,
                    originalRelativePath = item.relativePath,
                ),
            )
            recorded += TrashedMedia(trashId = id, item = item)
        }
        recorded
    }

    /**
     * Drops trash entries and their copies without touching the originals.
     * Used when a delete is cancelled after the copy was made, so the trash
     * never holds duplicates of files that are still in the gallery.
     */
    suspend fun discard(trashIds: List<Long>) = withContext(Dispatchers.IO) {
        trashIds.forEach { id ->
            trashDao.byId(id)?.let { entity ->
                runCatching { File(Uri.parse(entity.trashUri).path ?: "").delete() }
                trashDao.delete(id)
            }
        }
    }

    /** Restores a trashed item by re-publishing its bytes to MediaStore. */
    suspend fun restore(entity: TrashEntity): Uri? = withContext(Dispatchers.IO) {
        val file = File(Uri.parse(entity.trashUri).path ?: return@withContext null)
        if (!file.exists()) return@withContext null
        val restored = publishBack(file, entity)
        if (restored != null) {
            file.delete()
            trashDao.delete(entity.id)
        }
        restored
    }

    suspend fun deleteForever(entity: TrashEntity) = withContext(Dispatchers.IO) {
        runCatching { File(Uri.parse(entity.trashUri).path ?: "").delete() }
        trashDao.delete(entity.id)
    }

    /** Removes trash entries older than [retentionDays] (default 30). */
    suspend fun purgeExpired(retentionDays: Int = 30): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        val expired = trashDao.olderThan(cutoff)
        expired.forEach { entity ->
            runCatching { File(Uri.parse(entity.trashUri).path ?: "").delete() }
            trashDao.delete(entity.id)
        }
        expired.size
    }

    private fun publishBack(file: File, entity: TrashEntity): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (entity.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, entity.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, entity.mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${if (entity.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES}/ProGallery/Restored",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    if (entity.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                ),
                "ProGallery/Restored",
            )
            if (!dir.exists() && !dir.mkdirs()) return null
            val dest = File(dir, entity.displayName)
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(entity.mimeType), null)
            return Uri.fromFile(dest)
        }
    }
}
