package com.rentmate.app.data

import com.rentmate.app.network.toRpcParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RM-0016: the network calls behind the cached repositories. Each call may throw
 * (IOException offline, RestException from Supabase). Kept as interfaces so tests
 * can pass a fake that fails on purpose. RM-0017 added the writes.
 */
interface BillsRemoteSource {
    /** Every bill of [householdId] with its shares and debtor names. */
    suspend fun fetchBills(householdId: String): List<BillWithSharesRow>

    /** The members of [householdId] with their display names. */
    suspend fun fetchMembers(householdId: String): List<HouseholdMemberRow>

    /** Calls create_bill and returns the new bill's server id. */
    suspend fun createBill(params: CreateBillParams): String

    /**
     * RM-0018: id of a bill in [CreateBillParams.household_id_in] with the same
     * title, amount and due date (and issuer, when [issuerId] is given) that the
     * server created at or after [createdAtOrAfterIso], or null. Used by the sync
     * worker's duplicate guard.
     */
    suspend fun findMatchingBill(params: CreateBillParams, issuerId: String?, createdAtOrAfterIso: String): String?
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

    /** Inserts one item and returns its id. */
    suspend fun insertItem(insert: ShoppingItemInsert): String

    /** Marks the item [id] purchased. */
    suspend fun markPurchased(id: String, update: ShoppingItemPurchase)

    /**
     * RM-0018: marks item [id] purchased and returns how many rows changed. 0
     * means the row no longer exists (or RLS hides it), so the sync worker can
     * tell a lost target apart from a success.
     */
    suspend fun replayPurchase(id: String, update: ShoppingItemPurchase): Int

    /**
     * RM-0056: deletes the item [id]. RLS lets any household member delete.
     * The default throws so test fakes that never delete need no change.
     */
    suspend fun deleteItem(id: String): Unit = throw UnsupportedOperationException("deleteItem")
}

private val MEMBER_COLUMNS = Columns.raw("user_id, profiles(display_name)")

@Serializable
private data class MemberWithProfileRow(val user_id: String, val profiles: ProfileNameRow? = null)

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

    override suspend fun fetchMembers(householdId: String): List<HouseholdMemberRow> =
        supabase.from("household_members")
            .select(columns = MEMBER_COLUMNS) { filter { eq("household_id", householdId) } }
            .decodeList<MemberWithProfileRow>()
            .map { HouseholdMemberRow(it.user_id, it.profiles?.display_name) }

    override suspend fun createBill(params: CreateBillParams): String =
        supabase.postgrest.rpc("create_bill", params.toRpcParams()).decodeAs<IdRow>().id

    override suspend fun findMatchingBill(
        params: CreateBillParams,
        issuerId: String?,
        createdAtOrAfterIso: String
    ): String? =
        supabase.from("bills")
            .select(columns = Columns.raw("id")) {
                filter {
                    eq("household_id", params.household_id_in)
                    eq("title", params.title_in)
                    eq("amount", params.amount_in)
                    eq("due_date", params.due_date_in)
                    if (issuerId != null) eq("issued_by_user_id", issuerId)
                    gte("created_at", createdAtOrAfterIso)
                }
                limit(1)
            }
            .decodeList<IdRow>()
            .firstOrNull()?.id
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

    override suspend fun insertItem(insert: ShoppingItemInsert): String =
        supabase.from("shopping_items").insert(insert) { select() }.decodeSingle<IdRow>().id

    override suspend fun markPurchased(id: String, update: ShoppingItemPurchase) {
        supabase.from("shopping_items").update(update) { filter { eq("id", id) } }
    }

    override suspend fun replayPurchase(id: String, update: ShoppingItemPurchase): Int =
        supabase.from("shopping_items")
            .update(update) {
                select(Columns.raw("id"))
                filter { eq("id", id) }
            }
            .decodeList<IdRow>()
            .size

    override suspend fun deleteItem(id: String) {
        supabase.from("shopping_items").delete { filter { eq("id", id) } }
    }
}
