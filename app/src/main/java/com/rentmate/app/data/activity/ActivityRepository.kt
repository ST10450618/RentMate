package com.rentmate.app.data.activity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of the household activity feed (RM-0033).
 *
 * The app uses [FirestoreActivityRepository] (RM-0032).
 */
interface ActivityRepository {
    /** Emits the newest [limit] events of [householdId], newest first, and again on every change. */
    fun observe(householdId: String, limit: Int = 50): Flow<List<ActivityEvent>>

    /**
     * True when the events are made-up sample data rather than the household's real
     * activity. The screen labels sample data so nobody mistakes it for real housemates.
     */
    val isSampleData: Boolean get() = false
}

/**
 * Sample-data stand-in, kept for tests. Ignores the household
 * and emits one fixed sample list, with timestamps relative to when [observe] is called,
 * so the screen always shows today, yesterday and older groups. Never emits live updates.
 */
@Singleton
class InMemoryActivityRepository @Inject constructor() : ActivityRepository {

    override val isSampleData: Boolean = true

    override fun observe(householdId: String, limit: Int): Flow<List<ActivityEvent>> =
        flowOf(sampleEvents(Instant.now()).take(limit))

    internal fun sampleEvents(now: Instant): List<ActivityEvent> = listOf(
        ActivityEvent("e1", ActivityType.BILL_ADDED, "Thandi", "added a bill: Electricity R1 200", "bill-1", now.minus(Duration.ofMinutes(5))),
        ActivityEvent("e2", ActivityType.CHORE_COMPLETED, "Sipho", "completed: Take out bins", "chore-1", now.minus(Duration.ofMinutes(48))),
        ActivityEvent("e3", ActivityType.SHOPPING_ADDED, "Naledi", "added to the shopping list: Dishwashing liquid", "shop-1", now.minus(Duration.ofHours(3))),
        ActivityEvent("e4", ActivityType.BILL_PAID, "Sipho", "paid their share of Wi-Fi R899", "bill-2", now.minus(Duration.ofHours(30))),
        ActivityEvent("e5", ActivityType.MAINTENANCE_LOGGED, "Thandi", "logged a fault: Leaking kitchen tap", "maint-1", now.minus(Duration.ofHours(34))),
        ActivityEvent("e6", ActivityType.SHOPPING_BOUGHT, "Naledi", "bought: Toilet paper", "shop-2", now.minus(Duration.ofHours(40))),
        ActivityEvent("e7", ActivityType.MAINTENANCE_STATUS, "Landlord", "marked Leaking kitchen tap as In progress", "maint-1", now.minus(Duration.ofDays(4))),
        ActivityEvent("e8", ActivityType.CHORE_COMPLETED, "Naledi", "completed: Clean the bathroom", "chore-2", now.minus(Duration.ofDays(9)))
    )
}
