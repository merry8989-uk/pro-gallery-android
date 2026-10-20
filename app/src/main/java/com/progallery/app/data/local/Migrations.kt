package com.progallery.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 keyed favourites, hidden items and custom-album membership by the numeric
 * MediaStore id. Photo and video ids come from different collections and
 * collide, so v2 re-keys them by the media content URI.
 *
 * Every affected table already stored the item's `uri`, so the existing rows can
 * be re-keyed in place — favourites, hidden items and album membership are all
 * preserved rather than dropped.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // favourites
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favourites_new` " +
                "(`mediaKey` TEXT NOT NULL, `uri` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaKey`))",
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `favourites_new` (`mediaKey`, `uri`, `addedAt`) " +
                "SELECT `uri`, `uri`, `addedAt` FROM `favourites`",
        )
        db.execSQL("DROP TABLE `favourites`")
        db.execSQL("ALTER TABLE `favourites_new` RENAME TO `favourites`")

        // hidden items
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `hidden_items_new` " +
                "(`mediaKey` TEXT NOT NULL, `uri` TEXT NOT NULL, `hiddenAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaKey`))",
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `hidden_items_new` (`mediaKey`, `uri`, `hiddenAt`) " +
                "SELECT `uri`, `uri`, `hiddenAt` FROM `hidden_items`",
        )
        db.execSQL("DROP TABLE `hidden_items`")
        db.execSQL("ALTER TABLE `hidden_items_new` RENAME TO `hidden_items`")

        // custom album membership
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_album_items_new` " +
                "(`albumId` INTEGER NOT NULL, `mediaKey` TEXT NOT NULL, `uri` TEXT NOT NULL, " +
                "PRIMARY KEY(`albumId`, `mediaKey`))",
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `custom_album_items_new` (`albumId`, `mediaKey`, `uri`) " +
                "SELECT `albumId`, `uri`, `uri` FROM `custom_album_items`",
        )
        db.execSQL("DROP TABLE `custom_album_items`")
        db.execSQL("ALTER TABLE `custom_album_items_new` RENAME TO `custom_album_items`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_custom_album_items_albumId` " +
                "ON `custom_album_items` (`albumId`)",
        )
    }
}
