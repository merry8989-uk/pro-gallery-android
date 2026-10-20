package com.progallery.app.di

import android.content.Context
import androidx.room.Room
import com.progallery.app.data.local.ProGalleryDatabase
import com.progallery.app.data.local.dao.AlbumCoverDao
import com.progallery.app.data.local.dao.CustomAlbumDao
import com.progallery.app.data.local.dao.FavouriteDao
import com.progallery.app.data.local.dao.HiddenDao
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.data.local.dao.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ProGalleryDatabase =
        Room.databaseBuilder(context, ProGalleryDatabase::class.java, "progallery.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFavouriteDao(db: ProGalleryDatabase): FavouriteDao = db.favouriteDao()
    @Provides fun provideCustomAlbumDao(db: ProGalleryDatabase): CustomAlbumDao = db.customAlbumDao()
    @Provides fun provideAlbumCoverDao(db: ProGalleryDatabase): AlbumCoverDao = db.albumCoverDao()
    @Provides fun provideTrashDao(db: ProGalleryDatabase): TrashDao = db.trashDao()
    @Provides fun provideHiddenDao(db: ProGalleryDatabase): HiddenDao = db.hiddenDao()
    @Provides fun provideScanDirectoryDao(db: ProGalleryDatabase): ScanDirectoryDao = db.scanDirectoryDao()
}
