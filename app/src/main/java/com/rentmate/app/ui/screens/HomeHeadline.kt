package com.rentmate.app.ui.screens

import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.DebtSimplifier
import com.rentmate.app.data.Obligation
import com.rentmate.app.data.Transfer
import com.rentmate.app.data.local.toCents
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * RM-0056 part 3: the answer at the top of Home. It says what the user owes,
 * to whom and by when, or who owes them, or that they are settled up.
 * ("Answer first, detail after" in docs/ui-redesign-plan.html.)
 */
sealed interface HomeHeadline {
    /**
     * The user owes [cents] to [toUserId].
     *
     * @property dueDate earliest due date (yyyy-MM-dd) of an unpaid share the user
     *   owes on a bill [toUserId] issued, or null when the payment comes only from
     *   debt simplification and no single bill links the two.
     * @property otherCreditors how many other housemates the user also owes.
     * @property billTitle what the payment is for: the title of the bill with
     *   [dueDate], or null when [dueDate] is null.
     */
    data class YouOwe(
        val cents: Long,
        val toUserId: String,
        val dueDate: String?,
        val otherCreditors: Int,
        val billTitle: String? = null
    ) : HomeHeadline

    /**
     * [fromUserId] owes the user [cents].
     *
     * @property otherDebtors how many other housemates also owe the user.
     */
    data class OwedToYou(val cents: Long, val fromUserId: String, val otherDebtors: Int) : HomeHeadline

    /** Nobody owes the user and the user owes nobody. */
    data object Settled : HomeHeadline
}

/**
 * Builds the [HomeHeadline] for [myId] from the cached bills.
 *
 * It uses the same simplified payments as Settle up ([DebtSimplifier.simplify]),
 * so "Pay Sipho" on Home names the same person as the Money screen. When the
 * user owes several people, the headline picks the largest payment.
 */
fun homeHeadline(bills: List<BillWithSharesRow>, myId: String?): HomeHeadline {
    if (myId == null) return HomeHeadline.Settled
    val obligations = bills.flatMap { bill ->
        bill.bill_shares.filter { !it.is_paid }.map {
            Obligation(creditor = bill.issued_by_user_id, debtor = it.user_id, cents = it.amount_owed.toCents())
        }
    }
    val transfers = DebtSimplifier.simplify(obligations)
    val biggest = compareByDescending<Transfer> { it.cents }.thenBy { it.from + it.to }

    val outgoing = transfers.filter { it.from == myId }.sortedWith(biggest)
    outgoing.firstOrNull()?.let { pay ->
        val firstDue = bills
            .filter { bill -> bill.issued_by_user_id == pay.to && bill.bill_shares.any { it.user_id == myId && !it.is_paid } }
            .minByOrNull { it.due_date.take(10) }
        return HomeHeadline.YouOwe(pay.cents, pay.to, firstDue?.due_date?.take(10), outgoing.size - 1, firstDue?.title)
    }

    val incoming = transfers.filter { it.to == myId }.sortedWith(biggest)
    incoming.firstOrNull()?.let { return HomeHeadline.OwedToYou(it.cents, it.from, incoming.size - 1) }

    return HomeHeadline.Settled
}

/**
 * Whole days from [today] to [dueDate] (yyyy-MM-dd): 0 on the due date,
 * negative once it has passed. Null when the date cannot be read. Home shows
 * it as the days-left pill next to the payment.
 */
fun daysUntil(dueDate: String, today: LocalDate): Long? =
    runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(dueDate.take(10))) }.getOrNull()
