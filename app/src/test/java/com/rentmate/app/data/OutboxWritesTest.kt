package com.rentmate.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rentmate.app.data.local.HouseholdMemberEntity
import com.rentmate.app.data.local.LocalCache
import com.rentmate.app.data.local.OutboxOp
import com.rentmate.app.data.local.OutboxQueue
import com.rentmate.app.data.local.RentMateDatabase
import com.rentmate.app.data.local.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal
import java.net.UnknownHostException
import java.time.LocalDate

/**
 * RM-0017: writes try Supabase first. With no connection they are stored in Room
 * as pending rows plus an outbox entry; any other failure is thrown and nothing
 * is queued.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboxWritesTest {

    /** Fake server. [failure] is thrown by every write; reads honour [offline]. */
    private class FakeRemote : BillsRemoteSource, ShoppingRemoteSource {
        var offline = false
        var failure: Exception? = null
        var bills: List<BillWithSharesRow> = emptyList()
        var items: List<ShoppingItemRow> = emptyList()
        var members: List<HouseholdMemberRow> = emptyList()
        val createdBills = mutableListOf<CreateBillParams>()
        val insertedItems = mutableListOf<ShoppingItemInsert>()
        val purchases = mutableListOf<Pair<String, ShoppingItemPurchase>>()

        private fun failIfOffline() {
            if (offline) throw IOException("Unable to resolve host")
        }

        private fun failWrite() {
            failIfOffline()
            failure?.let { throw it }
        }

        override suspend fun fetchBills(householdId: String) = bills.also { failIfOffline() }
        override suspend fun fetchMembers(householdId: String) = members.also { failIfOffline() }
        override suspend fun fetchItems(householdId: String) = items.also { failIfOffline() }

        override suspend fun createBill(params: CreateBillParams): String {
            failWrite()
            createdBills += params
            return "server-bill"
        }

        override suspend fun insertItem(insert: ShoppingItemInsert): String {
            failWrite()
            insertedItems += insert
            return insert.id
        }

        override suspend fun markPurchased(id: String, update: ShoppingItemPurchase) {
            failWrite()
            purchases += id to update
        }
    }

    private lateinit var db: RentMateDatabase
    private lateinit var household: CurrentHousehold
    private val remote = FakeRemote()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RentMateDatabase::class.java)
            .allowMainThreadQueries().build()
        household = CurrentHousehold(InMemoryHouseholdIdStore("h1"), Dispatchers.Unconfined)
        runBlocking {
            db.memberDao().upsert(
                listOf(HouseholdMemberEntity("h1", "u1", "Sipho"), HouseholdMemberEntity("h1", "u2", "Thandi"),
                    HouseholdMemberEntity("h1", "u3", null))
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun bills() = CachedBillsRepository(remote, db.billDao(), db.memberDao(), OutboxQueue(db), household)
    private fun shopping() = CachedShoppingRepository(remote, db.shoppingItemDao(), db.memberDao(), OutboxQueue(db), household)

    private fun share(userId: String, value: String = "0") = SplitShare(userId, 0L, BigDecimal(value))

    private fun newBill(
        amount: String = "100.00",
        mode: SplitMode = SplitMode.Equal,
        shares: List<SplitShare> = listOf(share("u1"), share("u2"), share("u3"))
    ) = NewBill("h1", "Electricity", BigDecimal(amount), LocalDate.of(2026, 12, 1), mode, shares, RepeatMode.Monthly, "u1")

    private suspend fun outboxCount() = OutboxQueue(db).pendingCount.first()

    @Test
    fun offlineAddItem_storesPendingRowAndOutboxEntry() = runBlocking {
        remote.offline = true
        val repo = shopping()

        val result = repo.addItem("h1", "  Milk ", "u1")

        assertTrue(result is WriteResult.Queued)
        val row = db.shoppingItemDao().getById(result.id)!!
        assertEquals(SyncState.PENDING_CREATE, row.syncState)
        assertEquals("Milk", row.name)
        assertEquals("Sipho", row.added_by_name)

        val entry = db.outboxDao().getAllOldestFirst().single()
        assertEquals(OutboxOp.INSERT, entry.op)
        assertEquals("shopping_items", entry.tableName)
        assertEquals(result.id, entry.entityId)
        assertEquals(0, entry.attemptCount)
        val payload = OutboxJson.decodeFromString(ShoppingItemInsert.serializer(), entry.payloadJson)
        assertEquals(ShoppingItemInsert(result.id, "h1", "Milk", "u1"), payload)

        val emitted = repo.items.first().single()
        assertEquals(result.id, emitted.id)
        assertTrue(emitted.pending)
        assertEquals(1, outboxCount())
    }

    @Test
    fun onlineAddItem_sendsAndQueuesNothing() = runBlocking {
        val repo = shopping()

        val result = repo.addItem("h1", "Bread", "u1")

        assertTrue(result is WriteResult.Sent)
        assertEquals(listOf("Bread"), remote.insertedItems.map { it.name })
        assertTrue(db.outboxDao().getAllOldestFirst().isEmpty())
        assertEquals(0, outboxCount())
    }

    @Test
    fun serverRejection_isThrownAndNothingIsQueued() = runBlocking {
        remote.failure = IllegalStateException("new row violates row-level security policy")
        val shoppingRepo = shopping()
        val billsRepo = bills()

        assertTrue(runCatching { shoppingRepo.addItem("h1", "Milk", "u1") }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { billsRepo.createBill(newBill()) }.exceptionOrNull() is IllegalStateException)

        assertTrue(db.outboxDao().getAllOldestFirst().isEmpty())
        assertTrue(shoppingRepo.items.first().isEmpty())
        assertTrue(billsRepo.bills.first().isEmpty())
    }

    @Test
    fun wrappedNetworkFailure_isQueued() = runBlocking {
        remote.failure = IllegalStateException("request failed", UnknownHostException("host"))
        assertTrue(shopping().addItem("h1", "Eggs", "u1") is WriteResult.Queued)
        assertEquals(1, outboxCount())
    }

    @Test
    fun offlineCreateBill_storesBillAndSharesInCents() = runBlocking {
        remote.offline = true
        val repo = bills()

        val result = repo.createBill(newBill(amount = "100.00"))

        assertTrue(result is WriteResult.Queued)
        val stored = db.billDao().observeBills("h1").first().single()
        assertEquals(result.id, stored.bill.id)
        assertEquals(SyncState.PENDING_CREATE, stored.bill.syncState)
        assertEquals(10_000L, stored.bill.amount_cents)
        assertEquals("Monthly", stored.bill.repeat)
        assertEquals("u1", stored.bill.issued_by_user_id)
        assertTrue(stored.shares.all { it.syncState == SyncState.PENDING_CREATE && !it.is_paid })
        // Equal split: the last member absorbs the remainder, as create_bill does.
        val cents = stored.shares.associate { it.user_id to it.amount_owed_cents }
        assertEquals(mapOf("u1" to 3_333L, "u2" to 3_333L, "u3" to 3_334L), cents)
        assertEquals(stored.bill.amount_cents, cents.values.sum())
        assertEquals("Thandi", stored.shares.first { it.user_id == "u2" }.display_name)
        assertNull(stored.shares.first { it.user_id == "u3" }.display_name)

        val entry = db.outboxDao().getAllOldestFirst().single()
        assertEquals(OutboxOp.RPC, entry.op)
        assertEquals("create_bill", entry.tableName)
        assertEquals(result.id, entry.entityId)
        val params = OutboxJson.decodeFromString(CreateBillParams.serializer(), entry.payloadJson)
        assertEquals("h1", params.household_id_in)
        assertEquals("Electricity", params.title_in)
        assertEquals(100.0, params.amount_in, 0.0)
        assertEquals("2026-12-01T00:00:00Z", params.due_date_in)
        assertEquals("Equal", params.split_method_in)
        assertEquals("Monthly", params.repeat_in)
        assertEquals(listOf("u1", "u2", "u3"), params.shares_in.map { it.user_id })

        val row = repo.bills.first().single()
        assertTrue(row.pending)
        assertEquals(100.0, row.amount, 0.0)
        assertEquals(3, row.bill_shares.size)
    }

    @Test
    fun offlineCreateBill_percentageSharesSumToTotal() = runBlocking {
        remote.offline = true
        bills().createBill(
            newBill(amount = "99.99", mode = SplitMode.Percentage,
                shares = listOf(share("u1", "33.33"), share("u2", "33.33"), share("u3", "33.34")))
        )
        val stored = db.billDao().observeBills("h1").first().single()
        assertEquals(9_999L, stored.bill.amount_cents)
        assertEquals(9_999L, stored.shares.sumOf { it.amount_owed_cents })
    }

    @Test
    fun onlineCreateBill_sendsAndQueuesNothing() = runBlocking {
        val result = bills().createBill(newBill())
        assertEquals(WriteResult.Sent("server-bill"), result)
        assertEquals(1, remote.createdBills.size)
        assertTrue(db.outboxDao().getAllOldestFirst().isEmpty())
    }

    @Test
    fun invalidSplit_isRejectedBeforeAnyWrite() = runBlocking {
        remote.offline = true
        val bad = newBill(mode = SplitMode.Percentage, shares = listOf(share("u1", "50"), share("u2", "20")))
        assertTrue(runCatching { bills().createBill(bad) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(db.outboxDao().getAllOldestFirst().isEmpty())
    }

    @Test
    fun offlinePurchase_marksRowPendingUpdate() = runBlocking {
        remote.items = listOf(
            ShoppingItemRow(id = "i1", household_id = "h1", name = "Milk", is_purchased = false, created_at = "c")
        )
        val repo = shopping()
        repo.refresh()
        remote.offline = true

        assertTrue(repo.markPurchased("i1", "u2") is WriteResult.Queued)

        val row = db.shoppingItemDao().getById("i1")!!
        assertEquals(SyncState.PENDING_UPDATE, row.syncState)
        assertTrue(row.is_purchased)
        assertEquals("Thandi", row.purchased_by_name)
        val entry = db.outboxDao().getAllOldestFirst().single()
        assertEquals(OutboxOp.UPDATE, entry.op)
        assertEquals("i1", entry.entityId)
        val update = OutboxJson.decodeFromString(ShoppingItemPurchase.serializer(), entry.payloadJson)
        assertEquals("u2", update.purchased_by_user_id)
        assertTrue(update.is_purchased)
        assertTrue(repo.items.first().single().pending)
    }

    @Test
    fun purchaseOfItemNotYetUploaded_isQueuedBehindItsInsert() = runBlocking {
        remote.offline = true
        val repo = shopping()
        val id = repo.addItem("h1", "Milk", "u1").id
        remote.offline = false

        // Online again, but the insert has not been replayed: the update must wait behind it.
        assertTrue(repo.markPurchased(id, "u1") is WriteResult.Queued)
        assertTrue(remote.purchases.isEmpty())
        assertEquals(listOf(OutboxOp.INSERT, OutboxOp.UPDATE), db.outboxDao().getAllOldestFirst().map { it.op })
        val row = db.shoppingItemDao().getById(id)!!
        assertEquals(SyncState.PENDING_CREATE, row.syncState)
        assertTrue(row.is_purchased)
    }

    @Test
    fun onlinePurchase_sendsAndQueuesNothing() = runBlocking {
        remote.items = listOf(
            ShoppingItemRow(id = "i1", household_id = "h1", name = "Milk", is_purchased = false, created_at = "c")
        )
        val repo = shopping()
        repo.refresh()
        assertTrue(repo.markPurchased("i1", "u1") is WriteResult.Sent)
        assertEquals(listOf("i1"), remote.purchases.map { it.first })
        assertTrue(db.outboxDao().getAllOldestFirst().isEmpty())
    }

    @Test
    fun pendingRowsAndOutboxSurviveRefresh() = runBlocking {
        remote.offline = true
        val shoppingRepo = shopping()
        val billsRepo = bills()
        val itemId = shoppingRepo.addItem("h1", "Milk", "u1").id
        val billId = billsRepo.createBill(newBill()).id

        remote.offline = false
        remote.items = listOf(ShoppingItemRow(id = "i9", household_id = "h1", name = "Tea", is_purchased = false, created_at = "c"))
        remote.bills = listOf(
            BillWithSharesRow(id = "b9", household_id = "h1", title = "Rent", amount = 1.0, due_date = "2026-01-01",
                split_method = "Equal", issued_by_user_id = "u1", created_at = "c", updated_at = "u")
        )
        shoppingRepo.refresh()
        billsRepo.refresh()

        val items = shoppingRepo.items.first().associateBy { it.id }
        assertEquals(setOf(itemId, "i9"), items.keys)
        assertTrue(items.getValue(itemId).pending)
        assertFalse(items.getValue("i9").pending)

        val bills = billsRepo.bills.first().associateBy { it.id }
        assertEquals(setOf(billId, "b9"), bills.keys)
        assertTrue(bills.getValue(billId).pending)
        assertEquals(3, bills.getValue(billId).bill_shares.size)
        assertFalse(bills.getValue("b9").pending)

        assertEquals(listOf(itemId, billId), db.outboxDao().getAllOldestFirst().map { it.entityId })
        assertEquals(2, outboxCount())
    }

    @Test
    fun signOutClear_emptiesOutboxAndRoster() = runBlocking {
        remote.offline = true
        shopping().addItem("h1", "Milk", "u1")
        bills().createBill(newBill())
        assertEquals(2, outboxCount())

        LocalCache(db).clear()

        assertTrue(db.outboxDao().getAllOldestFirst().isEmpty())
        assertEquals(0, outboxCount())
        assertTrue(db.memberDao().observeMembers("h1").first().isEmpty())
        assertTrue(db.billDao().observeBills("h1").first().isEmpty())
        assertTrue(db.shoppingItemDao().observeItems("h1").first().isEmpty())
    }

    @Test
    fun refresh_cachesTheMemberRoster() = runBlocking {
        remote.members = listOf(HouseholdMemberRow("u7", "Lindiwe"))
        val repo = bills()
        repo.refresh()
        assertEquals(listOf(HouseholdMemberRow("u7", "Lindiwe")), repo.members.first())

        // Offline, the roster stays for Add Bill.
        remote.offline = true
        assertTrue(runCatching { repo.refreshMembers() }.isFailure)
        assertEquals(listOf("u7"), repo.members.first().map { it.user_id })
    }
}
