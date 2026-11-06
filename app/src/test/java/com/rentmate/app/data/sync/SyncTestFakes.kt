package com.rentmate.app.data.sync

import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.BillsRemoteSource
import com.rentmate.app.data.CreateBillParams
import com.rentmate.app.data.HouseholdMemberRow
import com.rentmate.app.data.ShoppingItemInsert
import com.rentmate.app.data.ShoppingItemPurchase
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.data.ShoppingRemoteSource
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.attachments.Attachment
import com.rentmate.app.data.attachments.AttachmentKind
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.UploadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.IOException
import kotlin.time.Duration

/** RM-0018: fake Supabase for the sync tests. Keeps the rows it accepted so refreshes return them. */
internal class FakeSyncRemote : BillsRemoteSource, ShoppingRemoteSource {
    var offline = false
    val calls = mutableListOf<String>()
    val items = mutableListOf<ShoppingItemRow>()
    val bills = mutableListOf<BillWithSharesRow>()
    var existingBillId: String? = null
    var billFetches = 0
    var itemFetches = 0
    var purchaseFailure: Exception? = null

    private fun failIfOffline() {
        if (offline) throw IOException("Unable to resolve host")
    }

    override suspend fun fetchBills(householdId: String): List<BillWithSharesRow> {
        failIfOffline(); billFetches++; return bills.toList()
    }

    override suspend fun fetchMembers(householdId: String) = emptyList<HouseholdMemberRow>().also { failIfOffline() }

    override suspend fun fetchItems(householdId: String): List<ShoppingItemRow> {
        failIfOffline(); itemFetches++; return items.toList()
    }

    override suspend fun createBill(params: CreateBillParams): String {
        failIfOffline()
        calls += "create_bill ${params.title_in}"
        val id = "server-bill-${bills.size + 1}"
        bills += BillWithSharesRow(
            id = id, household_id = params.household_id_in, title = params.title_in, amount = params.amount_in,
            due_date = params.due_date_in, split_method = params.split_method_in, issued_by_user_id = "u1",
            created_at = "now", updated_at = "now"
        )
        return id
    }

    override suspend fun findMatchingBill(params: CreateBillParams, issuerId: String?, createdAtOrAfterIso: String): String? {
        failIfOffline()
        calls += "find_bill ${params.title_in} issuer=$issuerId"
        return existingBillId
    }

    override suspend fun insertItem(insert: ShoppingItemInsert): String {
        failIfOffline()
        calls += "insert ${insert.name}"
        items += ShoppingItemRow(insert.id, insert.household_id, insert.name, is_purchased = false, created_at = "c")
        return insert.id
    }

    override suspend fun markPurchased(id: String, update: ShoppingItemPurchase) {
        failIfOffline()
        error("online purchases are not used here")
    }

    override suspend fun replayPurchase(id: String, update: ShoppingItemPurchase): Int {
        failIfOffline()
        purchaseFailure?.let { throw it }
        calls += "purchase $id"
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return 0
        items[index] = items[index].copy(is_purchased = true, purchased_at = update.purchased_at)
        return 1
    }
}

/** Records the activity events the replayer posts. */
internal class RecordingActivity : ActivityLogger {
    val events = mutableListOf<String>()
    override fun billAdded(title: String, amount: Double, billId: String?) { events += "billAdded $title $billId" }
    override fun sharePaid(title: String, amount: Double, payerName: String?, billId: String?) = Unit
    override fun settlementConfirmed(fromName: String, amount: Double) = Unit
    override fun choreCompleted(title: String, choreId: String?) = Unit
    override fun shoppingAdded(name: String, itemId: String?) { events += "shoppingAdded $name" }
    override fun shoppingBought(name: String, itemId: String?) { events += "shoppingBought $name" }
    override fun maintenanceLogged(title: String, requestId: String?) = Unit
    override fun maintenanceStatus(title: String, status: String, requestId: String?) = Unit
}

/** Photo storage where only [stored] paths exist. */
internal class FakeAttachments : AttachmentRepository {
    var stored = setOf<String>()
    val links = mutableListOf<Pair<AttachmentParent, List<String>>>()
    override fun upload(kind: AttachmentKind, jpeg: ByteArray): Flow<UploadProgress> = emptyFlow()
    override suspend fun delete(path: String) = Unit
    override suspend fun link(parent: AttachmentParent, paths: List<String>) { links += parent to paths }
    override suspend fun list(): List<Attachment> = emptyList()
    override suspend fun signedUrls(paths: List<String>, expiresIn: Duration) =
        paths.filter { it in stored }.associateWith { "https://signed/$it" }
}
