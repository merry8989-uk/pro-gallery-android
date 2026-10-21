package com.progallery.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.progallery.app.data.local.dao.AlbumCoverDao
import com.progallery.app.data.local.dao.CustomAlbumDao
import com.progallery.app.data.local.dao.FavouriteDao
import com.progallery.app.data.local.dao.HiddenDao
import com.progallery.app.data.local.dao.RecoveryHistoryDao
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.data.local.dao.TrashDao
import com.progallery.app.data.local.entity.AlbumCoverEntity
import com.progallery.app.data.local.entity.CustomAlbumEntity
import com.progallery.app.data.local.entity.CustomAlbumItemEntity
import com.progallery.app.data.local.entity.FavouriteEntity
import com.progallery.app.data.local.entity.HiddenEntity
import com.progallery.app.data.local.entity.RecoveryHistoryEntity
import com.progallery.app.data.local.entity.ScanDirectoryEntity
import com.progallery.app.data.local.entity.TrashEntity
import com.progallery.app.data.local.dao.IndexedMediaDao
import com.progallery.app.data.local.entity.IndexedMediaEntity

@Database(
    entities = [
        FavouriteEntity::class,
        CustomAlbumEntity::class,
        CustomAlbumItemEntity::class,
        AlbumCoverEntity::class,
        TrashEntity::class,
        HiddenEntity::class,
        ScanDirectoryEntity::class,
        RecoveryHistoryEntity::class,
        IndexedMediaEntity::class,
    ],
    version = DatabaseVersion.CURRENT,
    exportSchema = true,
)
abstract class ProGalleryDatabase : RoomDatabase() {
    abstract fun favouriteDao(): FavouriteDao
    abstract fun customAlbumDao(): CustomAlbumDao
    abstract fun albumCoverDao(): AlbumCoverDao
    abstract fun trashDao(): TrashDao
    abstract fun hiddenDao(): HiddenDao
    abstract fun scanDirectoryDao(): ScanDirectoryDao
    abstract fun recoveryHistoryDao(): RecoveryHistoryDao

    /** The app's own index of files the system's does not hand over. */
    abstract fun indexedMediaDao(): IndexedMediaDao
}
