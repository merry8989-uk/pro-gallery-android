package com.progallery.app.di

import android.content.Context
import androidx.room.Room
import com.progallery.app.data.local.MIGRATION_1_2
import com.progallery.app.data.local.MIGRATION_2_3
import com.progallery.app.data.local.MIGRATION_3_4
import com.progallery.app.data.local.ProGalleryDatabase
import com.progallery.app.data.local.dao.AlbumCoverDao
import com.progallery.app.data.local.dao.CustomAlbumDao
import com.progallery.app.data.local.dao.FavouriteDao
import com.progallery.app.data.local.dao.HiddenDao
import com.progallery.app.data.local.dao.RecoveryHistoryDao
import com.progallery.app.data.local.dao.ScanDirectoryDao
import com.progallery.app.data.local.dao.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.progallery.app.data.local.MIGRATION_4_5
import com.progallery.app.data.local.MIGRATION_5_6
import com.progallery.app.data.local.dao.IndexedMediaDao
import com.progallery.app.data.local.dao.PlaybackPositionDao
import com.progallery.app.data.local.DatabaseVersion
import com.progallery.app.data.eventlog.EventName
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.progallery.app.data.eventlog.EventLogStore
import androidx.room.RoomDatabase

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        eventLog: EventLogStore,
    ): ProGalleryDatabase =
        Room.databaseBuilder(context, ProGalleryDatabase::class.java, "progallery.db")
            // Explicit migration: re-keys media references by URI and keeps
            // existing favourites, hidden items and album membership.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // That the database opened, and at which version. A migration that half-ran is
                    // otherwise a silent data problem: everything looks fine until a row is gone.
                    //
                    // The scope is deliberately not a screen's or a view model's: this fires once
                    // per process and must survive whichever component happened to touch the
                    // database first.
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            eventLog.log(
                                EventName.MIGRATION_EVENT,
                                details = mapOf(
                                    "opened_version" to db.version.toString(),
                                    "expected_version" to DatabaseVersion.CURRENT.toString(),
                                ),
                            )
                        }
                    }
                }
            })
            .build()

    @Provides fun provideFavouriteDao(db: ProGalleryDatabase): FavouriteDao = db.favouriteDao()
    @Provides fun provideCustomAlbumDao(db: ProGalleryDatabase): CustomAlbumDao = db.customAlbumDao()
    @Provides fun provideAlbumCoverDao(db: ProGalleryDatabase): AlbumCoverDao = db.albumCoverDao()
    @Provides fun provideTrashDao(db: ProGalleryDatabase): TrashDao = db.trashDao()
    @Provides fun provideHiddenDao(db: ProGalleryDatabase): HiddenDao = db.hiddenDao()
    @Provides fun provideScanDirectoryDao(db: ProGalleryDatabase): ScanDirectoryDao = db.scanDirectoryDao()

    /** The app's own index, which the gallery merges into the system's rows. */
    @Provides fun provideIndexedMediaDao(db: ProGalleryDatabase): IndexedMediaDao =
        db.indexedMediaDao()

    @Provides fun providePlaybackPositionDao(db: ProGalleryDatabase): PlaybackPositionDao =
        db.playbackPositionDao()

    @Provides fun provideRecoveryHistoryDao(db: ProGalleryDatabase): RecoveryHistoryDao =
        db.recoveryHistoryDao()
}
