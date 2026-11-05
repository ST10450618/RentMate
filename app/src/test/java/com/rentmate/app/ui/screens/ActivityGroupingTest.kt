package com.rentmate.app.ui.screens

import com.rentmate.app.data.activity.ActivityEvent
import com.rentmate.app.data.activity.ActivityType
import org.junit.Assert.assertEquals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class ActivityGroupingTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 5)
    private val now = Instant.parse("2026-11-05T12:00:00Z")

    private fun event(id: String, at: String) =
        ActivityEvent(id, ActivityType.CHORE_COMPLETED, "Sipho", "did it", null, Instant.parse(at))

    @Test
    fun bucketsTodayYesterdayAndOlder() {
        assertEquals(DayBucket.Today, dayBucketFor(today, today))
        assertEquals(DayBucket.Yesterday, dayBucketFor(today.minusDays(1), today))
        assertEquals(DayBucket.On(today.minusDays(2)), dayBucketFor(today.minusDays(2), today))
    }

    @Test
    fun futureDateCountsAsToday() {
        assertEquals(DayBucket.Today, dayBucketFor(today.plusDays(1), today))
    }

    @Test
    fun groupsNewestFirstAcrossUnsortedInput() {
        val groups = groupByDay(
            listOf(
                event("old", "2026-11-01T09:00:00Z"),
                event("t1", "2026-11-05T08:00:00Z"),
                event("y", "2026-11-04T23:59:00Z"),
                event("t2", "2026-11-05T11:00:00Z")
            ),
            today, zone
        )
        assertEquals(listOf(DayBucket.Today, DayBucket.Yesterday, DayBucket.On(LocalDate.of(2026, 11, 1))), groups.map { it.bucket })
        assertEquals(listOf("t2", "t1"), groups[0].events.map { it.id })
    }

    @Test
    fun groupingUsesTheGivenTimeZone() {
        val e = event("late", "2026-11-04T23:30:00Z")
        val johannesburg = ZoneId.of("Africa/Johannesburg") // UTC+2, so already 5 Nov
        assertEquals(DayBucket.Today, groupByDay(listOf(e), today, johannesburg).single().bucket)
        assertEquals(DayBucket.Yesterday, groupByDay(listOf(e), today, zone).single().bucket)
    }

    @Test
    fun emptyInputGivesNoGroups() {
        assertEquals(emptyList<ActivityDayGroup>(), groupByDay(emptyList(), today, zone))
    }

    @Test
    fun relativeTimeBoundaries() {
        fun rel(seconds: Long) = relativeTime(now.minusSeconds(seconds), now)
        assertEquals(RelativeTime.JustNow, rel(0))
        assertEquals(RelativeTime.JustNow, rel(59))
        assertEquals(RelativeTime.Minutes(1), rel(60))
        assertEquals(RelativeTime.Minutes(59), rel(59 * 60 + 59))
        assertEquals(RelativeTime.Hours(1), rel(3600))
        assertEquals(RelativeTime.Hours(23), rel(23 * 3600 + 3599))
        assertEquals(RelativeTime.Days(1), rel(24 * 3600))
        assertEquals(RelativeTime.Days(9), rel(9 * 24 * 3600L))
    }

    @Test
    fun futureTimestampReadsAsJustNow() {
        assertEquals(RelativeTime.JustNow, relativeTime(now.plusSeconds(120), now))
    }
}
