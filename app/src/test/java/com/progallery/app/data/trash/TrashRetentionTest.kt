package com.progallery.app.data.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic that decides whether a person's file is deleted.
 *
 * The audit's P1-3 asked for the trash's failure scenarios to be tested. The purge worker's behaviour
 * on a failed delete was already right — it retries and keeps the row — but the **retention** itself
 * had no test at all, and that is the part that actually removes files.
 */
class TrashRetentionTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun theDefaultIsAMonth() {
        assertEquals(30, TrashRetention.DEFAULT_DAYS)
        assertEquals(now - 30 * day, TrashRetention.cutoff(30, now))
    }

    @Test
    fun somethingYoungerThanTheRetentionIsNotExpired() {
        assertFalse(TrashRetention.isExpired(now - 29 * day, 30, now))
    }

    @Test
    fun somethingOlderIs() {
        assertTrue(TrashRetention.isExpired(now - 31 * day, 30, now))
    }

    @Test
    fun exactlyAtTheCutoffIsNotYetExpired() {
        // The boundary is deliberate: a file is purged once it is **past** its retention, not on the
        // instant it reaches it.
        assertFalse(TrashRetention.isExpired(now - 30 * day, 30, now))
    }

    @Test
    fun aRetentionOfZeroIsClampedToOneDayRatherThanEverything() {
        // Reading zero literally would delete a file the instant it was trashed — which is not a
        // trash, and is not recoverable.
        assertEquals(now - day, TrashRetention.cutoff(0, now))
        assertFalse("a file just trashed must survive", TrashRetention.isExpired(now - 60_000, 0, now))
    }

    @Test
    fun aNegativeRetentionIsAlsoClamped() {
        assertEquals(now - day, TrashRetention.cutoff(-5, now))
    }

    @Test
    fun aRowWithNoTimestampIsNeverPurged() {
        // An unknown age is not the same as an old one.
        assertFalse(TrashRetention.isPurgeable(null, 30, now))
        assertTrue(TrashRetention.isPurgeable(now - 40 * day, 30, now))
    }

    @Test
    fun aFutureTimestampIsNotExpired() {
        // A clock change, or a restored backup, can put a row in the future. It is not old.
        assertFalse(TrashRetention.isExpired(now + 10 * day, 30, now))
    }
}
