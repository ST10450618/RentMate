package com.rentmate.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DAO tests against an in-memory Room database, run on the JVM via Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoTest {

    private lateinit var db: RentMateDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RentMateDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun bill(id: String, hh: String = "h1", state: SyncState = SyncState.SYNCED) =
        BillEntity(id, hh, "Bill $id", 100.0, "2026-01-01", "EQUAL", "u1", "c", "u", state, 1L)

    private fun share(id: String, billId: String, hh: String = "h1") =
        BillShareEntity(id, billId, hh, "u1", 50.0, false)

    private fun chore(id: String, hh: String = "h1", state: SyncState = SyncState.SYNCED) =
        ChoreEntity(id, hh, "Chore $id", "WEEKLY", 5, listOf("a", "b", "c"), 0, "c", state)

    private fun item(id: String, hh: String = "h1", state: SyncState = SyncState.SYNCED) =
        ShoppingItemEntity(id = id, household_id = hh, name = "Item $id", is_purchased = false,
            created_at = "c", syncState = state)

    @Test
    fun bills_scopedByHousehold() = runBlocking {
        db.billDao().upsertBills(listOf(bill("b1"), bill("b2", "h2")))
        val ids = db.billDao().observeBills("h1").first().map { it.bill.id }
        assertEquals(listOf("b1"), ids)
    }

    @Test
    fun bills_includeShares() = runBlocking {
        db.billDao().upsertBills(listOf(bill("b1")))
        db.billDao().upsertShares(listOf(share("s1", "b1"), share("s2", "b1")))
        val result = db.billDao().observeBills("h1").first().single()
        assertEquals(setOf("s1", "s2"), result.shares.map { it.id }.toSet())
    }

    @Test
    fun deletingBill_cascadesToShares() = runBlocking {
        db.billDao().upsertBills(listOf(bill("b1")))
        db.billDao().upsertShares(listOf(share("s1", "b1")))
        db.billDao().deleteBill("b1")
        db.billDao().upsertBills(listOf(bill("b1")))
        assertTrue(db.billDao().observeBills("h1").first().single().shares.isEmpty())
    }

    @Test
    fun upsert_updatesExistingRow() = runBlocking {
        db.choreDao().upsert(listOf(chore("c1")))
        db.choreDao().upsert(listOf(chore("c1").copy(title = "Renamed")))
        val list = db.choreDao().observeChores("h1").first()
        assertEquals(1, list.size)
        assertEquals("Renamed", list[0].title)
    }

    @Test
    fun chores_rotationOrderRoundTrips() = runBlocking {
        db.choreDao().upsert(listOf(chore("c1")))
        assertEquals(listOf("a", "b", "c"), db.choreDao().observeChores("h1").first()[0].rotation_order)
    }

    @Test
    fun chores_scopedByHousehold() = runBlocking {
        db.choreDao().upsert(listOf(chore("c1"), chore("c2", "h2")))
        assertEquals(listOf("c1"), db.choreDao().observeChores("h1").first().map { it.id })
    }

    @Test
    fun shopping_scopedAndDeleted() = runBlocking {
        db.shoppingItemDao().upsert(listOf(item("i1"), item("i2"), item("i3", "h2")))
        db.shoppingItemDao().delete("i2")
        assertEquals(listOf("i1"), db.shoppingItemDao().observeItems("h1").first().map { it.id })
    }

    @Test
    fun syncState_updateAndUnsyncedQuery() = runBlocking {
        val dao = db.shoppingItemDao()
        dao.upsert(listOf(item("i1"), item("i2")))
        dao.setSyncState("i1", SyncState.PENDING_UPDATE)
        assertEquals(listOf("i1"), dao.getUnsynced("h1").map { it.id })
        dao.markSynced("i1")
        assertTrue(dao.getUnsynced("h1").isEmpty())
    }

    @Test
    fun pendingDelete_hiddenFromReadsButStillUnsynced() = runBlocking {
        val dao = db.choreDao()
        dao.upsert(listOf(chore("c1")))
        dao.setSyncState("c1", SyncState.PENDING_DELETE)
        assertTrue(dao.observeChores("h1").first().isEmpty())
        assertEquals(1, dao.getUnsynced("h1").size)
    }

    @Test
    fun choreReplace_keepsPendingDropsStaleSynced() = runBlocking {
        val dao = db.choreDao()
        dao.upsert(listOf(
            chore("stale"), chore("pend", state = SyncState.PENDING_CREATE),
            chore("other", "h2")
        ))
        dao.replaceForHousehold("h1", listOf(chore("fresh"), chore("pend").copy(title = "server")))
        val h1 = dao.observeChores("h1").first().associateBy { it.id }
        assertEquals(setOf("fresh", "pend"), h1.keys)
        assertEquals("Chore pend", h1.getValue("pend").title) // local pending edit wins
        assertEquals(1, dao.observeChores("h2").first().size)
    }

    @Test
    fun shoppingReplace_keepsPendingDropsStaleSynced() = runBlocking {
        val dao = db.shoppingItemDao()
        dao.upsert(listOf(item("stale"), item("pend", state = SyncState.PENDING_UPDATE)))
        dao.replaceForHousehold("h1", listOf(item("fresh")))
        assertEquals(setOf("fresh", "pend"), dao.observeItems("h1").first().map { it.id }.toSet())
    }

    @Test
    fun billReplace_keepsPendingBillAndSharesDropsStale() = runBlocking {
        val dao = db.billDao()
        dao.upsertBills(listOf(bill("stale"), bill("pend", state = SyncState.PENDING_CREATE)))
        dao.upsertShares(listOf(share("ss", "stale"), share("sp", "pend")))
        dao.replaceForHousehold("h1", listOf(bill("fresh")), listOf(share("sf", "fresh")))
        val result = dao.observeBills("h1").first().associateBy { it.bill.id }
        assertEquals(setOf("fresh", "pend"), result.keys)
        assertEquals(listOf("sp"), result.getValue("pend").shares.map { it.id })
        assertEquals(listOf("sf"), result.getValue("fresh").shares.map { it.id })
    }

    @Test
    fun billReplace_keepsPendingShareOfSyncedBillAndRefreshesBill() = runBlocking {
        val dao = db.billDao()
        dao.upsertBills(listOf(bill("b1")))
        dao.upsertShares(listOf(share("s1", "b1").copy(is_paid = true, syncState = SyncState.PENDING_UPDATE)))
        dao.replaceForHousehold(
            "h1",
            listOf(bill("b1").copy(title = "Server title")),
            listOf(share("s1", "b1").copy(is_paid = false))
        )
        val result = dao.observeBills("h1").first().single()
        assertEquals("Server title", result.bill.title)
        val s = result.shares.single()
        assertEquals(SyncState.PENDING_UPDATE, s.syncState)
        assertTrue(s.is_paid)
    }

    @Test
    fun billReplace_removesStaleSyncedShareOnKeptBill() = runBlocking {
        val dao = db.billDao()
        dao.upsertBills(listOf(bill("b1")))
        dao.upsertShares(listOf(
            share("keep", "b1").copy(syncState = SyncState.PENDING_UPDATE),
            share("stale", "b1")
        ))
        dao.replaceForHousehold("h1", listOf(bill("b1")), listOf(share("fresh", "b1")))
        val ids = dao.observeBills("h1").first().single().shares.map { it.id }.toSet()
        assertEquals(setOf("keep", "fresh"), ids)
    }

    @Test
    fun billSyncState_andUnsyncedQuery() = runBlocking {
        val dao = db.billDao()
        dao.upsertBills(listOf(bill("b1"), bill("b2")))
        dao.upsertShares(listOf(share("s1", "b1")))
        dao.setBillSyncState("b2", SyncState.PENDING_UPDATE)
        dao.setShareSyncState("s1", SyncState.PENDING_CREATE)
        assertEquals(listOf("b2"), dao.getUnsyncedBills("h1").map { it.id })
        assertEquals(listOf("s1"), dao.getUnsyncedShares("h1").map { it.id })
        dao.markBillSynced("b2")
        dao.markShareSynced("s1")
        assertTrue(dao.getUnsyncedBills("h1").isEmpty())
        assertTrue(dao.getUnsyncedShares("h1").isEmpty())
    }

    @Test
    fun outbox_enqueueOldestFirstAndDelete() = runBlocking {
        val dao = db.outboxDao()
        val newer = dao.enqueue(OutboxEntry(op = "INSERT", tableName = "chores", payloadJson = "{}", createdAt = 200))
        val older = dao.enqueue(OutboxEntry(op = "DELETE", tableName = "bills", payloadJson = "{}", createdAt = 100))
        assertEquals(listOf(older, newer), dao.getAllOldestFirst().map { it.id })
        dao.deleteById(older)
        assertEquals(listOf(newer), dao.getAllOldestFirst().map { it.id })
    }

    /** Sign-out (PRIVACY.md): nothing from the previous household may remain on the phone. */
    @Test
    fun localCache_clear_removesEveryTable() = runBlocking {
        db.billDao().upsertBills(listOf(bill("b1")))
        db.billDao().upsertShares(listOf(share("s1", "b1")))
        db.choreDao().upsert(listOf(chore("c1")))
        db.shoppingItemDao().upsert(listOf(item("i1", state = SyncState.PENDING_CREATE)))
        db.outboxDao().enqueue(OutboxEntry(op = "INSERT", tableName = "shopping_items", payloadJson = "{}", createdAt = 1))

        LocalCache(db).clear()

        assertTrue(db.billDao().observeBills("h1").first().isEmpty())
        assertTrue(db.billDao().getUnsyncedShares("h1").isEmpty())
        assertTrue(db.choreDao().observeChores("h1").first().isEmpty())
        assertTrue(db.shoppingItemDao().observeItems("h1").first().isEmpty())
        assertTrue(db.outboxDao().getAllOldestFirst().isEmpty())
    }
}
