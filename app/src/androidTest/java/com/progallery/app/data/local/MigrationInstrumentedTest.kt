package com.progallery.app.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real migration against real SQLite on a device or emulator.
 *
 * This is the test that proves an upgrade does not cost the user their favourites,
 * albums, hidden items or trash. [MigrationCoverageTest] checks the migration chain
 * and its SQL for consistency without a device; this one executes the SQL and shows
 * the rows survive.
 *
 * The pre-upgrade database is created from explicit DDL rather than from Room's
 * exported schema JSON: Room only exports the version currently being compiled, so
 * `3.json` does not exist. The DDL below mirrors the v3 entities. Once the exported
 * schemas are committed to the repository this can additionally validate against
 * Room's expected schema, which is noted in ROADMAP.md.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** The v3 schema, as the entities described it. */
        val V3_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `favourites` (`mediaKey` TEXT NOT NULL, `uri` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaKey`))",
            "CREATE TABLE IF NOT EXISTS `custom_albums` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `custom_album_items` (`albumId` INTEGER NOT NULL, " +
                "`mediaKey` TEXT NOT NULL, `uri` TEXT NOT NULL, PRIMARY KEY(`albumId`, `mediaKey`))",
            "CREATE INDEX IF NOT EXISTS `index_custom_album_items_albumId` " +
                "ON `custom_album_items` (`albumId`)",
            "CREATE TABLE IF NOT EXISTS `album_covers` (`albumKey` TEXT NOT NULL, `coverUri` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`albumKey`))",
            "CREATE TABLE IF NOT EXISTS `trash` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`originalUri` TEXT NOT NULL, `trashUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                "`mimeType` TEXT NOT NULL, `isVideo` INTEGER NOT NULL, `mediaType` TEXT, " +
                "`sizeBytes` INTEGER NOT NULL, `originalBucketId` INTEGER NOT NULL, " +
                "`originalRelativePath` TEXT, `deletedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `hidden_items` (`mediaKey` TEXT NOT NULL, `uri` TEXT NOT NULL, " +
                "`hiddenAt` INTEGER NOT NULL, PRIMARY KEY(`mediaKey`))",
            "CREATE TABLE IF NOT EXISTS `scan_directories` (`treeUri` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `lastScannedAt` INTEGER, " +
                "PRIMARY KEY(`treeUri`))",
        )
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    private fun openAt(version: Int): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    V3_SCHEMA.forEach { db.execSQL(it) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion < 4) MIGRATION_3_4.migrate(db)
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun migratingFrom3To4_keepsUserDataAndAddsTheHistoryTable() {
        // A v3 database with a row in every table the user can see.
        openAt(3).use { db ->
            db.execSQL("INSERT INTO favourites (mediaKey, uri, addedAt) VALUES ('content://media/1', 'content://media/1', 1)")
            db.execSQL("INSERT INTO hidden_items (mediaKey, uri, hiddenAt) VALUES ('content://media/2', 'content://media/2', 2)")
            db.execSQL("INSERT INTO custom_albums (id, name, createdAt) VALUES (7, 'Trips', 3)")
            db.execSQL("INSERT INTO custom_album_items (albumId, mediaKey, uri) VALUES (7, 'content://media/1', 'content://media/1')")
            db.execSQL("INSERT INTO album_covers (albumKey, coverUri, updatedAt) VALUES ('k', 'content://media/1', 4)")
            db.execSQL("INSERT INTO scan_directories (treeUri, displayName, addedAt) VALUES ('content://tree/x', 'Pictures', 5)")
            db.execSQL(
                "INSERT INTO trash (originalUri, trashUri, displayName, mimeType, isVideo, mediaType, " +
                    "sizeBytes, originalBucketId, originalRelativePath, deletedAt) " +
                    "VALUES ('content://media/3', 'file:///trash/3', 'gone.jpg', 'image/jpeg', 0, 'IMAGE', 42, 9, 'DCIM/', 6)",
            )
        }

        // Reopen at v4: the migration runs.
        openAt(4).use { db ->
            db.query("SELECT mediaKey FROM favourites").use { c ->
                assertTrue("a favourite was lost", c.moveToFirst())
                assertEquals("content://media/1", c.getString(0))
                assertEquals(1, c.count)
            }
            db.query("SELECT mediaKey FROM hidden_items").use { c ->
                assertTrue("a hidden item was lost", c.moveToFirst())
                assertEquals(1, c.count)
            }
            db.query("SELECT name FROM custom_albums").use { c ->
                assertTrue("an album was lost", c.moveToFirst())
                assertEquals("Trips", c.getString(0))
            }
            db.query("SELECT mediaKey FROM custom_album_items").use { c ->
                assertEquals("album membership was lost", 1, c.count)
            }
            db.query("SELECT coverUri FROM album_covers").use { c ->
                assertEquals("an album cover was lost", 1, c.count)
            }
            db.query("SELECT displayName, sizeBytes FROM trash").use { c ->
                assertTrue("a trashed item was lost", c.moveToFirst())
                assertEquals("gone.jpg", c.getString(0))
                assertEquals(42L, c.getLong(1))
            }
            db.query("SELECT displayName FROM scan_directories").use { c ->
                assertEquals("a granted folder was lost", 1, c.count)
            }

            // The new table exists and starts empty.
            db.query("SELECT COUNT(*) FROM recovery_history").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("the new table should start empty", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun runningTheMigrationTwiceIsHarmless() {
        // CREATE TABLE IF NOT EXISTS means a re-run must not throw, which matters if a
        // device retries an upgrade.
        openAt(3).close()
        openAt(4).use { db ->
            MIGRATION_3_4.migrate(db)
            db.query("SELECT COUNT(*) FROM recovery_history").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }
}
