package com.progallery.app.data.local

import com.progallery.app.data.local.entity.RecoveryHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.progallery.app.data.local.entity.IndexedMediaEntity

/**
 * Guards the upgrade path without needing a device.
 *
 * These are the mistakes that lose a user's favourites, albums, hidden items or
 * trash on upgrade: bumping the version without writing the migration, leaving a
 * gap in the chain, or adding a column to an entity without adding it to the SQL.
 *
 * This does not execute SQLite — it checks that the migration chain and the SQL
 * are consistent with the schema the app declares. The SQL itself is exercised by
 * the instrumented suite on a device.
 */
class MigrationCoverageTest {


    /**
     * The entity's real columns.
     *
     * `declaredFields` also reports fields the compiler adds — the Compose compiler
     * puts a `$stable` marker on classes in a Compose module — so those are filtered
     * out rather than being mistaken for a missing column.
     */
    private fun columnsOf(clazz: Class<*>): Set<String> =
        clazz.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .filterNot { it.startsWith("$") || it == "Companion" }
            .toSet()

    @Test
    fun theDatabaseVersionIsTheLastMigrationTarget() {
        val lastTarget = MigrationSteps.ALL.last().second
        assertEquals(
            "the @Database version must equal the newest migration's target",
            DatabaseVersion.CURRENT,
            lastTarget,
        )
    }

    @Test
    fun everyVersionGapHasAMigration() {
        val sources = MigrationSteps.ALL.map { it.first }.toSet()
        for (from in 1 until DatabaseVersion.CURRENT) {
            assertTrue(
                "there is no migration starting at version $from — an upgrade would fail",
                from in sources,
            )
        }
    }

    @Test
    fun migrationsAreContiguousAndAscending() {
        MigrationSteps.ALL.forEach { (from, to) ->
            assertEquals("a migration must move exactly one version", from + 1, to)
        }
    }

    @Test
    fun theNewestMigrationCreatesEveryColumnItsEntityDeclares() {
        val sql = MigrationSql.MIGRATION_3_4_CREATE_RECOVERY_HISTORY
        val columns = columnsOf(RecoveryHistoryEntity::class.java)

        assertTrue("the table name is missing from the migration", sql.contains("`recovery_history`"))
        columns.forEach { column ->
            assertTrue(
                "column `$column` is declared on RecoveryHistoryEntity but the migration does not create it",
                sql.contains("`$column`"),
            )
        }
    }

    /**
     * The same two guards for the table the app's own index writes to.
     *
     * A CREATE TABLE that does not match the entity exactly does not fail at build time — it
     * fails on a real upgrade, on the device of whoever already had the app. That is the worst
     * place to find it, so it is checked here instead.
     */
    @Test
    fun theIndexedMediaMigrationCreatesEveryColumnItsEntityDeclares() {
        val sql = MigrationSql.MIGRATION_4_5_CREATE_INDEXED_MEDIA
        val columns = columnsOf(IndexedMediaEntity::class.java)

        assertTrue("the table name is missing from the migration", sql.contains("`indexed_media`"))
        columns.forEach { column ->
            assertTrue(
                "column `$column` is declared on IndexedMediaEntity but the migration does not create it",
                sql.contains("`$column`"),
            )
        }
    }

    @Test
    fun theIndexedMediaMigrationDoesNotCreateColumnsTheEntityLacks() {
        val sql = MigrationSql.MIGRATION_4_5_CREATE_INDEXED_MEDIA
        val declared = columnsOf(IndexedMediaEntity::class.java)
        val created = Regex("`([A-Za-z_][A-Za-z0-9_]*)`").findAll(sql)
            .map { it.groupValues[1] }
            .filter { it != "indexed_media" }
            .toSet()
        created.forEach { column ->
            assertTrue(
                "the migration creates `$column` but IndexedMediaEntity has no such field",
                column in declared,
            )
        }
    }

    @Test
    fun theIndexedMediaTableIsKeyedByTheMediaKey() {
        // The key the whole app uses for identity. A different primary key here would let the
        // same file be indexed twice and appear twice in the grid.
        val sql = MigrationSql.MIGRATION_4_5_CREATE_INDEXED_MEDIA
        assertTrue("the table must be keyed by mediaKey", sql.contains("PRIMARY KEY(`mediaKey`)"))
    }

    @Test
    fun theNewestMigrationDoesNotCreateColumnsTheEntityLacks() {
        val sql = MigrationSql.MIGRATION_3_4_CREATE_RECOVERY_HISTORY
        val declared = columnsOf(RecoveryHistoryEntity::class.java)
        // Every backticked identifier in the CREATE TABLE must be a real column.
        val created = Regex("`([A-Za-z_][A-Za-z0-9_]*)`").findAll(sql)
            .map { it.groupValues[1] }
            .filter { it != "recovery_history" }
            .toSet()
        created.forEach { column ->
            assertTrue(
                "the migration creates `$column` but RecoveryHistoryEntity has no such field",
                column in declared,
            )
        }
    }
}
