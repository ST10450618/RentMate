package com.rentmate.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RM-0016: the network reads behind the cached repositories. Each call may throw
 * (IOException offline, RestException from Supabase). Kept as interfaces so tests
 * can pass a fake that fails on purpose.
 */
interface BillsRemoteSource {
    /** Every bill of [householdId] with its shares and debtor names. */
    suspend fun fetchBills(householdId: String): List<BillWithSharesRow>
}

/** Network reads for chores. */
interface ChoresRemoteSource {
    /** Every chore of [householdId]. */
    suspend fun fetchChores(householdId: String): List<ChoreRow>
}

/** Network reads for shopping items. */
interface ShoppingRemoteSource {
    /** Every shopping item of [householdId], with who added and bought it. */
    suspend fun fetchItems(householdId: String): List<ShoppingItemRow>
}

private val BILL_WITH_SHARES_COLUMNS = Columns.raw("*, bill_shares(*, profiles(display_name))")

// Explicit column list (no "*"): the raw FK columns would otherwise collide
// with the aliased embeds below, since both want the same JSON key.
private val SHOPPING_ITEM_COLUMNS = Columns.raw(
    "id, household_id, name, is_purchased, purchased_at, converted_to_bill_id, created_at, " +
        "added_by_user_id:profiles!added_by_user_id(display_name), " +
        "purchased_by_user_id:profiles!purchased_by_user_id(display_name)"
)

/** Supabase [BillsRemoteSource]. */
@Singleton
class SupabaseBillsRemoteSource @Inject constructor(
    private val supabase: SupabaseClient
) : BillsRemoteSource {
    override suspend fun fetchBills(householdId: String): List<BillWithSharesRow> =
        supabase.from("bills")
            .select(columns = BILL_WITH_SHARES_COLUMNS) { filter { eq("household_id", householdId) } }
            .decodeList<BillWithSharesRow>()
}

/** Supabase [ChoresRemoteSource]. */
@Singleton
class SupabaseChoresRemoteSource @Inject constructor(
    private val supabase: SupabaseClient
) : ChoresRemoteSource {
    override suspend fun fetchChores(householdId: String): List<ChoreRow> =
        supabase.from("chores").select { filter { eq("household_id", householdId) } }.decodeList<ChoreRow>()
}

/** Supabase [ShoppingRemoteSource]. */
@Singleton
class SupabaseShoppingRemoteSource @Inject constructor(
    private val supabase: SupabaseClient
) : ShoppingRemoteSource {
    override suspend fun fetchItems(householdId: String): List<ShoppingItemRow> =
        supabase.from("shopping_items")
            .select(columns = SHOPPING_ITEM_COLUMNS) { filter { eq("household_id", householdId) } }
            .decodeList<ShoppingItemRow>()
}
