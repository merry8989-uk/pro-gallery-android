package com.progallery.app.data.local

import com.progallery.app.data.local.entity.RecoveryHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val columns = RecoveryHistoryEntity::class.java.declaredFields.map { it.name }

        assertTrue("the table name is missing from the migration", sql.contains("`recovery_history`"))
        columns.forEach { column ->
            assertTrue(
                "column `$column` is declared on RecoveryHistoryEntity but the migration does not create it",
                sql.contains("`$column`"),
            )
        }
    }

    @Test
    fun theNewestMigrationDoesNotCreateColumnsTheEntityLacks() {
        val sql = MigrationSql.MIGRATION_3_4_CREATE_RECOVERY_HISTORY
        val declared = RecoveryHistoryEntity::class.java.declaredFields.map { it.name }.toSet()
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
