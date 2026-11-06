package com.rentmate.app.ui.screens

import com.rentmate.app.R
import com.rentmate.app.data.activity.ActivityType
import com.rentmate.app.ui.components.PillTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RM-0060: the muted context line and the status pill on each Activity row. */
class ActivityRowTest {

    @Test
    fun `bill events read as Bills and repairs as Repairs`() {
        assertEquals(R.string.activity_context_bills, activityContextRes(ActivityType.BILL_ADDED))
        assertEquals(R.string.activity_context_bills, activityContextRes(ActivityType.BILL_PAID))
        assertEquals(R.string.activity_context_repairs, activityContextRes(ActivityType.MAINTENANCE_STATUS))
    }

    @Test
    fun `events that finish something get a positive pill`() {
        assertEquals(R.string.activity_pill_paid to PillTone.Positive, activityStatusPill(ActivityType.BILL_PAID))
        assertEquals(R.string.activity_pill_done to PillTone.Positive, activityStatusPill(ActivityType.CHORE_COMPLETED))
        assertEquals(R.string.activity_pill_bought to PillTone.Positive, activityStatusPill(ActivityType.SHOPPING_BOUGHT))
    }

    @Test
    fun `events that only add something have no pill`() {
        assertNull(activityStatusPill(ActivityType.BILL_ADDED))
        assertNull(activityStatusPill(ActivityType.SHOPPING_ADDED))
        assertNull(activityStatusPill(ActivityType.MAINTENANCE_LOGGED))
    }
}
