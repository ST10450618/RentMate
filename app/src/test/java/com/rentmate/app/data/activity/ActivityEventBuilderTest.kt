package com.rentmate.app.data.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RM-0032: the document the app writes must match the field list in firestore.rules. */
class ActivityEventBuilderTest {

    private val marker = Any()

    @Test
    fun mapHasExactlyTheFieldsTheRulesExpect() {
        val map = ActivityEventBuilder.toMap(ActivityType.BILL_ADDED, "Thandi", "added a bill: Power R1 200,00", "bill-1", marker)
        assertEquals(setOf("type", "actorName", "summary", "refId", "createdAt"), map.keys)
        assertEquals("bill_added", map["type"])
        assertEquals("Thandi", map["actorName"])
        assertEquals("bill-1", map["refId"])
        assertTrue(map["createdAt"] === marker)
    }

    @Test
    fun missingRefIdIsStoredAsNull() {
        val map = ActivityEventBuilder.toMap(ActivityType.BILL_PAID, "Sipho", "x", null, marker)
        assertTrue(map.containsKey("refId"))
        assertNull(map["refId"])
    }

    @Test
    fun everyTypeUsesItsWireName() {
        ActivityType.entries.forEach {
            assertEquals(it.wireName, ActivityEventBuilder.toMap(it, "a", "b", null, marker)["type"])
        }
    }

    @Test
    fun blankTextFallsBackSoTheRulesNeverSeeAnEmptyString() {
        val map = ActivityEventBuilder.toMap(ActivityType.CHORE_COMPLETED, "  ", "", "id", marker)
        assertEquals("Someone", map["actorName"])
        assertEquals("chore_completed", map["summary"])
    }

    @Test
    fun longTextIsCutToTheRulesLimit() {
        val map = ActivityEventBuilder.toMap(ActivityType.SHOPPING_ADDED, "a".repeat(500), "b".repeat(500), "c".repeat(500), marker)
        assertEquals(EVENT_TEXT_MAX, (map["actorName"] as String).length)
        assertEquals(EVENT_TEXT_MAX, (map["summary"] as String).length)
        assertEquals(EVENT_TEXT_MAX, (map["refId"] as String).length)
    }

    @Test
    fun summariesFollowTheSampleTexts() {
        assertEquals("added a bill: Electricity R1 200", ActivityEventBuilder.billAdded("Electricity", "R1 200"))
        assertEquals("completed: Take out bins", ActivityEventBuilder.choreCompleted("Take out bins"))
        assertEquals("added to the shopping list: Soap", ActivityEventBuilder.shoppingAdded("Soap"))
        assertEquals("bought: Toilet paper", ActivityEventBuilder.shoppingBought("Toilet paper"))
        assertEquals("logged a fault: Leaking tap", ActivityEventBuilder.maintenanceLogged("Leaking tap"))
        assertEquals("marked Leaking tap as Sent", ActivityEventBuilder.maintenanceStatus("Leaking tap", "Sent"))
        assertEquals("marked Sipho's share of Wi-Fi R899 as paid", ActivityEventBuilder.sharePaid("Wi-Fi", "R899", "Sipho"))
        assertEquals("marked a share of Wi-Fi R899 as paid", ActivityEventBuilder.sharePaid("Wi-Fi", "R899", null))
    }
}
