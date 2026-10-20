package com.progallery.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real migration against real SQLite on a device or emulator.
 *
 * This is the test that proves an upgrade does not cost the user their favourites,
 * albums, hidden items or trash. [MigrationCoverageTest] checks the migration chain
 * and SQL for consistency without a device; this one executes it and then validates
 * the result against the schema Room exported.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProGalleryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun clearDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun migratingFrom3To4_keepsUserDataAndAddsTheHistoryTable() {
        // A v3 database with a row in every table the user can see.
        helper.createDatabase(TEST_DB, 3).use { db ->
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
            db.close()
        }

        // Runs MIGRATION_3_4 and validates the result against the exported v4 schema.
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        migrated.query("SELECT mediaKey FROM favourites").use { c ->
            assertTrue("a favourite was lost", c.moveToFirst())
            assertEquals("content://media/1", c.getString(0))
            assertEquals(1, c.count)
        }
        migrated.query("SELECT mediaKey FROM hidden_items").use { c ->
            assertTrue("a hidden item was lost", c.moveToFirst())
            assertEquals(1, c.count)
        }
        migrated.query("SELECT name FROM custom_albums").use { c ->
            assertTrue("an album was lost", c.moveToFirst())
            assertEquals("Trips", c.getString(0))
        }
        migrated.query("SELECT mediaKey FROM custom_album_items").use { c ->
            assertEquals("album membership was lost", 1, c.count)
        }
        migrated.query("SELECT displayName, sizeBytes FROM trash").use { c ->
            assertTrue("a trashed item was lost", c.moveToFirst())
            assertEquals("gone.jpg", c.getString(0))
            assertEquals(42L, c.getLong(1))
        }
        migrated.query("SELECT displayName FROM scan_directories").use { c ->
            assertEquals("a granted folder was lost", 1, c.count)
        }

        // The new table exists and is empty.
        migrated.query("SELECT COUNT(*) FROM recovery_history").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("the new table should start empty", 0, c.getInt(0))
        }
        migrated.close()
    }
}
