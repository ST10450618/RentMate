package com.rentmate.app.ui.screens

import com.rentmate.app.data.BillShareWithProfileRow
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.SettleUpRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** RM-0056: the filter chips and settle-up rows on Money. */
class MoneyRulesTest {

    private fun bill(id: String, paid: Boolean, repeat: String = "Never", stopped: Boolean = false) = BillWithSharesRow(
        id = id, household_id = "h1", title = id, amount = 100.0, due_date = "2026-12-01",
        split_method = "Equal", issued_by_user_id = "me", created_at = "c", updated_at = "u",
        repeat = repeat, repeat_stopped = stopped,
        bill_shares = listOf(BillShareWithProfileRow(id = "$id-s", user_id = "u2", amount_owed = 100.0, is_paid = paid))
    )

    private val bills = listOf(
        bill("open", paid = false),
        bill("paid", paid = true),
        bill("rent", paid = false, repeat = "Monthly"),
        bill("stopped", paid = true, repeat = "Weekly", stopped = true)
    )

    @Test
    fun openShowsBillsWithAnUnpaidShare() {
        assertEquals(listOf("open", "rent"), filterBills(bills, BillFilter.Open).map { it.id })
    }

    @Test
    fun paidShowsFullyPaidBills() {
        assertEquals(listOf("paid", "stopped"), filterBills(bills, BillFilter.Paid).map { it.id })
    }

    @Test
    fun repeatingSkipsStoppedBills() {
        assertEquals(listOf("rent"), filterBills(bills, BillFilter.Repeating).map { it.id })
    }

    @Test
    fun aShareInsideItsUndoWindowCountsAsPaid() {
        val shown = filterBills(bills, BillFilter.Paid) { id, paid -> paid || id == "open-s" }
        assertEquals(listOf("open", "paid", "stopped"), shown.map { it.id })
    }

    private fun pay(from: String, to: String) = SettleUpRow(from, from.uppercase(), to, to.uppercase(), 10.0)

    @Test
    fun settleRoleFollowsTheUser() {
        assertEquals(SettleRole.YouPay, settleRole(pay("me", "sipho"), "me"))
        assertEquals(SettleRole.PaysYou, settleRole(pay("thandi", "me"), "me"))
        assertEquals(SettleRole.Between, settleRole(pay("thandi", "sipho"), "me"))
        assertEquals(SettleRole.Between, settleRole(pay("thandi", "sipho"), null))
    }

    @Test
    fun visibleSettleUpPutsTheUserFirstAndHidesUndoneReceipts() {
        val rows = listOf(pay("thandi", "sipho"), pay("lebo", "me"), pay("me", "sipho"), pay("thandi", "me"))
        assertEquals(
            listOf("me", "lebo"),
            visibleSettleUp(rows, "me", hiddenPayers = setOf("thandi")).map { it.from_user_id }.take(2)
        )
        assertEquals(3, visibleSettleUp(rows, "me", hiddenPayers = setOf("thandi")).size)
    }
}
