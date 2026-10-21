package com.progallery.app.data.usage

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven-day review. It decides what the next round of work should look at, so it is pinned
 * here rather than eyeballed in the app.
 */
class UsageReviewTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun day(
        daysAgo: Long,
        features: Map<FeatureId, Int> = emptyMap(),
        events: Map<UsageEvent, Int> = emptyMap(),
        performance: Map<PerfOperation, PerfTotals> = emptyMap(),
        crashes: List<CrashRecord> = emptyList(),
    ) = UsageDay(
        day = today.minusDays(daysAgo),
        features = features,
        events = events,
        performance = performance,
        crashes = crashes,
    )

    @Test
    fun onlyTheLastSevenDaysCount() {
        val digest = UsageReview.digest(
            listOf(
                day(0, features = mapOf(FeatureId.PHOTOS to 5)),
                day(6, features = mapOf(FeatureId.PHOTOS to 5)),
                day(7, features = mapOf(FeatureId.PHOTOS to 100)),  // outside the window
                day(30, features = mapOf(FeatureId.VIDEOS to 100)),
            ),
            today,
        )
        assertEquals(10, digest.features.first { it.first == FeatureId.PHOTOS }.second)
        assertTrue("a day from 30 days ago must not appear", digest.features.none { it.first == FeatureId.VIDEOS })
    }

    @Test
    fun theSeventhDayBackIsInsideTheWindow() {
        val digest = UsageReview.digest(listOf(day(6, features = mapOf(FeatureId.PHOTOS to 1))), today)
        assertEquals(1, digest.daysWithData)
    }

    @Test
    fun featuresAreOrderedByUse() {
        val digest = UsageReview.digest(
            listOf(day(1, features = mapOf(FeatureId.PHOTOS to 3, FeatureId.VIDEOS to 9))),
            today,
        )
        assertEquals(FeatureId.VIDEOS, digest.features.first().first)
        assertEquals(FeatureId.PHOTOS, digest.features.last().first)
    }

    @Test
    fun aFutureDayIsNotCounted() {
        val digest = UsageReview.digest(listOf(day(-3, features = mapOf(FeatureId.PHOTOS to 99))), today)
        assertTrue("a clock that jumped forward must not inflate the review", digest.features.isEmpty())
    }

    @Test
    fun withNoDataItSaysSoRatherThanShowingAnEmptyList() {
        val digest = UsageReview.digest(emptyList(), today)
        assertEquals(1, digest.observations.size)
        assertTrue(digest.observations.single().contains("No usage recorded"))
    }

    @Test
    fun aPartialWeekIsAdmitted() {
        val digest = UsageReview.digest(listOf(day(0, events = mapOf(UsageEvent.APP_OPENED to 1))), today)
        assertTrue(digest.observations.any { it.contains("1 of the last 7 days") })
    }

    @Test
    fun theBusiestAreaIsCalledOut() {
        val digest = UsageReview.digest(
            listOf(day(1, features = mapOf(FeatureId.PHOTOS to 80, FeatureId.VIDEOS to 20))),
            today,
        )
        assertTrue(digest.observations.any { it.contains("photos is the busiest area") })
    }

    @Test
    fun aSlowOperationIsCalledOut() {
        val digest = UsageReview.digest(
            listOf(day(1, performance = mapOf(PerfOperation.LIBRARY_LOAD to PerfTotals(2, 2000, 1200)))),
            today,
        )
        assertTrue(digest.observations.any { it.contains("library.load averages 1000ms") })
    }

    @Test
    fun aFastOperationIsNotCalledOut() {
        val digest = UsageReview.digest(
            listOf(day(1, performance = mapOf(PerfOperation.THUMBNAIL to PerfTotals(10, 500, 80)))),
            today,
        )
        assertTrue(digest.observations.none { it.contains("thumbnail averages") })
    }

    @Test
    fun crashesAreCountedAndMentioned() {
        val crash = CrashRecord("IllegalStateException", listOf("A.b"), "1.24.0")
        val digest = UsageReview.digest(
            listOf(day(1, crashes = listOf(crash, crash)), day(2, crashes = listOf(crash))),
            today,
        )
        assertEquals(3, digest.crashes)
        assertTrue(digest.observations.any { it.contains("3 crashes") })
    }

    @Test
    fun oneCrashIsNotCalledCrashes() {
        val crash = CrashRecord("IllegalStateException", emptyList(), "1.24.0")
        val digest = UsageReview.digest(listOf(day(1, crashes = listOf(crash))), today)
        assertTrue(digest.observations.any { it.contains("1 crash recorded") })
    }

    @Test
    fun searchingWithoutCreatingAlbumsIsNoticed() {
        val digest = UsageReview.digest(
            listOf(day(1, events = mapOf(UsageEvent.SEARCH_PERFORMED to 9))),
            today,
        )
        assertTrue(digest.observations.any { it.contains("no album was created") })
    }

    @Test
    fun aRefusedPermissionIsTreatedAsASignOfConfusingWording() {
        val digest = UsageReview.digest(
            listOf(day(1, events = mapOf(UsageEvent.PERMISSION_REFUSED to 2))),
            today,
        )
        assertTrue(digest.observations.any { it.contains("explaining itself poorly") })
    }

    @Test
    fun enoughDataIsOnlyClaimedWhenEveryDayHasSome() {
        val full = (0L..6L).map { day(it, events = mapOf(UsageEvent.APP_OPENED to 1)) }
        assertTrue(UsageReview.digest(full, today).hasEnoughToJudge)
        assertTrue(!UsageReview.digest(listOf(day(0, events = mapOf(UsageEvent.APP_OPENED to 1))), today).hasEnoughToJudge)
    }

    @Test
    fun aCustomWindowIsHonoured() {
        val digest = UsageReview.digest(
            listOf(day(10, features = mapOf(FeatureId.PHOTOS to 5))),
            today,
            windowDays = 14,
        )
        assertEquals(5, digest.features.single().second)
    }

    @Test
    fun theSlowestOperationComesFirst() {
        val digest = UsageReview.digest(
            listOf(
                day(
                    1,
                    performance = mapOf(
                        PerfOperation.THUMBNAIL to PerfTotals(1, 100, 100),
                        PerfOperation.SCAN to PerfTotals(1, 9000, 9000),
                    ),
                ),
            ),
            today,
        )
        assertEquals(PerfOperation.SCAN, digest.slowest.first().first)
    }
}
