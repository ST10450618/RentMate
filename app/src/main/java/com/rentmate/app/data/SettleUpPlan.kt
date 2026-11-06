package com.rentmate.app.data

import android.util.Log
import com.rentmate.app.data.local.centsToRand
import com.rentmate.app.data.local.toCents
import com.rentmate.app.network.toRpcParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SettleUpPlan"

/** RM-0031: the server's settle-up plan (the settle_up RPC, US-11). */
interface SettleUpSource {
    /** The payments that would settle [householdId] now, as the server computes them. */
    suspend fun settleUp(householdId: String): List<SettleUpRow>
}

@Serializable
private data class SettleUpParams(val household_id_in: String)

/** [SettleUpSource] that calls settle_up on Supabase. */
@Singleton
class SupabaseSettleUpSource @Inject constructor(
    private val supabase: SupabaseClient
) : SettleUpSource {
    override suspend fun settleUp(householdId: String): List<SettleUpRow> =
        supabase.postgrest.rpc("settle_up", SettleUpParams(householdId).toRpcParams()).decodeList()
}

/**
 * RM-0031: builds a settle-up plan from the bills on this phone. It uses the
 * same rule as the server (unpaid shares, [DebtSimplifier]), so with the same
 * ledger it gives the same payments. Bills still waiting in the outbox are
 * included, because the server will have them after the next sync.
 *
 * Names come from [members] first, then from the share rows. A name that is
 * still unknown is left blank, and the screen shows a fallback word.
 */
fun localSettleUp(bills: List<BillWithSharesRow>, members: List<HouseholdMemberRow>): List<SettleUpRow> {
    val names = HashMap<String, String>()
    for (bill in bills) {
        for (share in bill.bill_shares) {
            share.profiles?.display_name?.takeIf { it.isNotBlank() }?.let { names[share.user_id] = it }
        }
    }
    for (member in members) {
        member.display_name?.takeIf { it.isNotBlank() }?.let { names[member.user_id] = it }
    }
    val obligations = bills.flatMap { bill ->
        bill.bill_shares.filter { !it.is_paid }.map {
            Obligation(creditor = bill.issued_by_user_id, debtor = it.user_id, cents = it.amount_owed.toCents())
        }
    }
    val transfers = DebtSimplifier.simplify(obligations)
    Log.d(TAG, "local plan: ${bills.size} bills, ${obligations.size} unpaid shares, ${transfers.size} payments")
    return transfers.map {
        SettleUpRow(
            from_user_id = it.from,
            from_display_name = names[it.from].orEmpty(),
            to_user_id = it.to,
            to_display_name = names[it.to].orEmpty(),
            amount = it.cents.centsToRand()
        )
    }
}
