package com.rentmate.app.ui.screens

import com.rentmate.app.data.activity.ActivityEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The heading a day of activity is listed under. */
sealed interface DayBucket {
    data object Today : DayBucket
    data object Yesterday : DayBucket
    data class On(val date: LocalDate) : DayBucket
}

/** A day heading and its events, newest first. */
data class ActivityDayGroup(val bucket: DayBucket, val events: List<ActivityEvent>)

/** How long ago something happened, kept as data so the UI can pick the localised string. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime
    data class Days(val count: Int) : RelativeTime
}

/** Classifies [date] against [today]. Dates in the future count as today (clock skew). */
fun dayBucketFor(date: LocalDate, today: LocalDate): DayBucket = when {
    !date.isBefore(today) -> DayBucket.Today
    date == today.minusDays(1) -> DayBucket.Yesterday
    else -> DayBucket.On(date)
}

/** Groups [events] by local day, newest day first and newest event first within a day. */
fun groupByDay(events: List<ActivityEvent>, today: LocalDate, zone: ZoneId): List<ActivityDayGroup> =
    events.sortedByDescending { it.createdAt }
        .groupBy { dayBucketFor(it.createdAt.atZone(zone).toLocalDate(), today) }
        .map { (bucket, list) -> ActivityDayGroup(bucket, list) }

/** Describes how long before [now] [then] was. Future timestamps read as just now. */
fun relativeTime(then: Instant, now: Instant): RelativeTime {
    val d = Duration.between(then, now)
    return when {
        d.toMinutes() < 1 -> RelativeTime.JustNow
        d.toMinutes() < 60 -> RelativeTime.Minutes(d.toMinutes().toInt())
        d.toHours() < 24 -> RelativeTime.Hours(d.toHours().toInt())
        else -> RelativeTime.Days(d.toDays().toInt())
    }
}
