package com.progallery.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The schema version the app is on. Used by the `@Database` annotation and read by
 * the migration tests, so the two can never disagree.
 */
object DatabaseVersion {
    const val CURRENT = 5
}

/**
 * Every migration step, oldest first. Read by the migration tests so a forgotten
 * or out-of-order migration fails the build rather than a user's upgrade.
 */
object MigrationSteps {
    val ALL: List<Pair<Int, Int>> = listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5)
}

/**
 * The SQL the migrations run, kept as data rather than inline strings so a test
 * can check it against the entities it has to satisfy.
 */
object MigrationSql {
    val MIGRATION_3_4_CREATE_RECOVERY_HISTORY = """
        CREATE TABLE IF NOT EXISTS `recovery_history` (
            `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            `displayName` TEXT NOT NULL,
            `sourceLabel` TEXT NOT NULL,
            `destinationLabel` TEXT NOT NULL,
            `outcome` TEXT NOT NULL,
            `detail` TEXT,
            `sizeBytes` INTEGER NOT NULL,
            `exportedAt` INTEGER NOT NULL
        )
    """.trimIndent()
}

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

/**
 * Adds the real media kind to trash rows. Before this, only `isVideo` was
 * stored, so a deleted audio file was restored into Pictures as if it were an
 * image — and on some devices that insert simply failed.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trash` ADD COLUMN `mediaType` TEXT")
        db.execSQL(
            "UPDATE `trash` SET `mediaType` = CASE WHEN `isVideo` = 1 THEN 'VIDEO' ELSE 'IMAGE' END",
        )
    }
}

/**
 * Adds the recovery/export history table.
 *
 * Purely additive: no existing table is touched, so favourites, albums, hidden
 * items and the trash survive the upgrade untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(MigrationSql.MIGRATION_3_4_CREATE_RECOVERY_HISTORY)
    }
}

/**
 * Adds the table the app's own index writes to, so media the system's index does not hand over
 * can be shown in the gallery.
 *
 * Purely additive: no existing table is touched, so favourites, albums, hidden items, the trash
 * and the recovery history all survive the upgrade untouched.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(MigrationSql.MIGRATION_4_5_CREATE_INDEXED_MEDIA)
    }
}
