package com.rentmate.app.ui.screens

import com.rentmate.app.data.BillShareWithProfileRow
import com.rentmate.app.data.BillWithSharesRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.LocalDate
import org.junit.Test

/** RM-0056 part 3: Home says what you owe, to whom and by when. */
class HomeHeadlineTest {

    private fun bill(id: String, issuer: String, due: String, vararg shares: Pair<String, Double>, paid: Boolean = false) =
        BillWithSharesRow(
            id = id, household_id = "h1", title = id, amount = shares.sumOf { it.second }, due_date = due,
            split_method = "EQUAL", issued_by_user_id = issuer, created_at = "c", updated_at = "u",
            bill_shares = shares.mapIndexed { i, (user, amount) ->
                BillShareWithProfileRow(id = "$id-$i", user_id = user, amount_owed = amount, is_paid = paid)
            }
        )

    @Test
    fun youOwe_namesTheCreditorAndTheEarliestDueDate() {
        val bills = listOf(
            bill("power", "sipho", "2026-11-30T00:00:00Z", "me" to 1200.0, "sipho" to 1200.0),
            bill("water", "sipho", "2026-11-20", "me" to 45.0, "sipho" to 45.0)
        )
        assertEquals(HomeHeadline.YouOwe(124_500, "sipho", "2026-11-20", 0, billTitle = "water"), homeHeadline(bills, "me"))
    }

    @Test
    fun youOwe_picksTheLargestPaymentAndCountsTheRest() {
        val bills = listOf(
            bill("rent", "thandi", "2026-12-01", "me" to 3000.0),
            bill("wifi", "sipho", "2026-12-05", "me" to 300.0)
        )
        val headline = homeHeadline(bills, "me") as HomeHeadline.YouOwe
        assertEquals("thandi", headline.toUserId)
        assertEquals(300_000, headline.cents)
        assertEquals(1, headline.otherCreditors)
    }

    @Test
    fun owedToYou_whenOthersOweTheUser() {
        val bills = listOf(bill("wifi", "me", "2026-12-05", "thandi" to 300.0, "me" to 300.0))
        assertEquals(HomeHeadline.OwedToYou(30_000, "thandi", 0), homeHeadline(bills, "me"))
    }

    @Test
    fun settled_whenEverythingIsPaid() {
        val bills = listOf(bill("rent", "thandi", "2026-12-01", "me" to 3000.0, paid = true))
        assertEquals(HomeHeadline.Settled, homeHeadline(bills, "me"))
        assertEquals(HomeHeadline.Settled, homeHeadline(emptyList(), "me"))
        assertEquals(HomeHeadline.Settled, homeHeadline(bills, null))
    }

    @Test
    fun simplifiedPaymentWithNoDirectBillHasNoDueDate() {
        // me owes u2, u2 owes u3: simplification has me pay u3 directly.
        val bills = listOf(
            bill("a", "u2", "2026-12-01", "me" to 100.0),
            bill("b", "u3", "2026-12-02", "u2" to 100.0)
        )
        assertEquals(HomeHeadline.YouOwe(10_000, "u3", null, 0), homeHeadline(bills, "me"))
    }

    @Test
    fun daysUntil_countsWholeDays() {
        val today = LocalDate.of(2026, 11, 27)
        assertEquals(3L, daysUntil("2026-11-30", today))
        assertEquals(3L, daysUntil("2026-11-30T00:00:00Z", today))
        assertEquals(0L, daysUntil("2026-11-27", today))
        assertEquals(-2L, daysUntil("2026-11-25", today))
        assertNull(daysUntil("soon", today))
    }
}
