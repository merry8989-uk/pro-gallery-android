package com.progallery.app.data.repository

import android.net.Uri
import com.progallery.app.data.local.dao.AlbumCoverDao
import com.progallery.app.data.local.dao.CustomAlbumDao
import com.progallery.app.data.local.entity.AlbumCoverEntity
import com.progallery.app.data.local.entity.CustomAlbumEntity
import com.progallery.app.data.local.entity.CustomAlbumItemEntity
import com.progallery.app.data.model.Album
import com.progallery.app.data.model.MediaItem
import com.progallery.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns user-created albums: their membership and their cover images. Auto
 * albums (folders) stay in [MediaRepository]; this repository layers the
 * user's own collections on top of the same media index.
 */
@Singleton
class AlbumRepository @Inject constructor(
    private val customAlbumDao: CustomAlbumDao,
    private val albumCoverDao: AlbumCoverDao,
    private val mediaRepository: MediaRepository,
) {

    /**
     * Custom albums with live cover, item count and total size. Cover resolution
     * order: an explicitly chosen cover, else the first item in the album.
     */
    fun observeCustomAlbums(): Flow<List<Album>> = combine(
        customAlbumDao.observeAlbums(),
        customAlbumDao.observeAllItems(),
        albumCoverDao.observeAll(),
        mediaRepository.observeMedia(MediaType.ALL),
    ) { albums, items, covers, media ->
        val sizeByKey = media.associate { it.mediaKey to it.sizeBytes }
        val coverByKey = covers.associate { it.albumKey to it.coverUri }
        albums.map { album ->
            val members = items.filter { it.albumId == album.id }
            val cover = coverByKey[albumKey(album.id)] ?: members.firstOrNull()?.uri
            Album(
                id = album.id,
                name = album.name,
                coverUri = cover?.let(Uri::parse),
                itemCount = members.size,
                totalBytes = members.sumOf { sizeByKey[it.mediaKey] ?: 0L },
                isCustom = true,
            )
        }
    }.flowOn(Dispatchers.Default)

    /** The media in a custom album, in the order the gallery lists it. */
    fun observeAlbumItems(albumId: Long): Flow<List<MediaItem>> = combine(
        customAlbumDao.observeItems(albumId),
        mediaRepository.observeMedia(MediaType.ALL),
    ) { items, media ->
        val keys = items.mapTo(HashSet()) { it.mediaKey }
        media.filter { it.mediaKey in keys }
    }.flowOn(Dispatchers.Default)

    suspend fun createAlbum(name: String): Long = withContext(Dispatchers.IO) {
        customAlbumDao.create(CustomAlbumEntity(name = name.trim().ifBlank { "Untitled album" }))
    }

    suspend fun deleteAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        customAlbumDao.itemsIn(albumId).forEach { customAlbumDao.removeItem(albumId, it.mediaKey) }
        customAlbumDao.delete(albumId)
        albumCoverDao.clear(albumKey(albumId))
    }

    /** Adds [items] to the album, ignoring any already present. Returns how many were new. */
    suspend fun addToAlbum(albumId: Long, items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        val existing = customAlbumDao.itemsIn(albumId).mapTo(HashSet()) { it.mediaKey }
        var added = 0
        items.forEach { item ->
            if (existing.add(item.mediaKey)) {
                customAlbumDao.addItem(
                    CustomAlbumItemEntity(
                        albumId = albumId,
                        mediaKey = item.mediaKey,
                        uri = item.uri.toString(),
                    ),
                )
                added++
            }
        }
        added
    }

    suspend fun removeFromAlbum(albumId: Long, mediaKey: String) = withContext(Dispatchers.IO) {
        customAlbumDao.removeItem(albumId, mediaKey)
    }

    /** Sets an explicit cover for the album. */
    suspend fun setCover(albumId: Long, coverUri: Uri) = withContext(Dispatchers.IO) {
        albumCoverDao.set(AlbumCoverEntity(albumKey = albumKey(albumId), coverUri = coverUri.toString()))
    }

    companion object {
        /** Stable key used in the album_covers table for a custom album. */
        fun albumKey(albumId: Long): String = "custom:$albumId"
    }
}
