package com.rentmate.app.ui.screens

import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.data.isRepeating

/*
 * RM-0056: the rules behind the Money screen, as pure functions so unit tests
 * cover them. Money shows bills and settle-up on one screen, as in the
 * redesign mockup.
 */

/** The filter chips above the bill rows. */
enum class BillFilter { Open, Paid, Repeating }

/**
 * The bills one filter shows.
 *
 * - Open: at least one share is still unpaid.
 * - Paid: every share is paid.
 * - Repeating: the bill still makes new copies of itself.
 *
 * @param isPaid whether a share counts as paid. The screen passes
 *   [BillsUiState.isShownPaid], so a share inside its Undo window moves at once.
 */
fun filterBills(
    bills: List<BillWithSharesRow>,
    filter: BillFilter,
    isPaid: (shareId: String, paid: Boolean) -> Boolean = { _, paid -> paid }
): List<BillWithSharesRow> = bills.filter { bill ->
    val allPaid = bill.bill_shares.isNotEmpty() && bill.bill_shares.all { isPaid(it.id, it.is_paid) }
    when (filter) {
        BillFilter.Open -> !allPaid
        BillFilter.Paid -> allPaid
        BillFilter.Repeating -> isRepeating(bill.repeat, bill.repeat_stopped)
    }
}

/** How a settle-up payment relates to the signed-in user. */
enum class SettleRole {
    /** The user pays someone. Only the person paid can confirm it. */
    YouPay,

    /** Someone pays the user. The user can mark it received. */
    PaysYou,

    /** A payment between two other housemates. */
    Between
}

/** Which [SettleRole] [row] has for [myId]. */
fun settleRole(row: SettleUpRow, myId: String?): SettleRole = when (myId) {
    null -> SettleRole.Between
    row.from_user_id -> SettleRole.YouPay
    row.to_user_id -> SettleRole.PaysYou
    else -> SettleRole.Between
}

/**
 * The settle-up rows to show: the user's own payments first, then the rest,
 * without payments that are inside their Undo window ([hiddenPayers] holds
 * the payer's user id).
 */
fun visibleSettleUp(rows: List<SettleUpRow>, myId: String?, hiddenPayers: Set<String>): List<SettleUpRow> =
    rows.filterNot { it.from_user_id in hiddenPayers && it.to_user_id == myId }
        .sortedBy { settleRole(it, myId).ordinal }
