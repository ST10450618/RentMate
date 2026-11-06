package com.rentmate.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rentmate.app.data.local.BillEntity
import com.rentmate.app.data.local.BillShareEntity
import com.rentmate.app.data.local.ChoreEntity
import com.rentmate.app.data.local.OutboxQueue
import com.rentmate.app.data.local.RentMateDatabase
import com.rentmate.app.data.local.ShoppingItemEntity
import com.rentmate.app.data.local.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * RM-0016: repositories read from Room and refresh from a fake server.
 * A failed refresh (airplane mode) must keep showing what was cached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CachedRepositoriesTest {

    private class FakeRemote : BillsRemoteSource, ChoresRemoteSource, ShoppingRemoteSource {
        var offline = false
        var bills: List<BillWithSharesRow> = emptyList()
        var chores: List<ChoreRow> = emptyList()
        var items: List<ShoppingItemRow> = emptyList()

        private fun failIfOffline() {
            if (offline) throw IOException("Unable to resolve host")
        }

        override suspend fun fetchBills(householdId: String) = bills.also { failIfOffline() }
        override suspend fun fetchChores(householdId: String) = chores.also { failIfOffline() }
        override suspend fun fetchItems(householdId: String) = items.also { failIfOffline() }
        override suspend fun fetchMembers(householdId: String) = emptyList<HouseholdMemberRow>().also { failIfOffline() }
        override suspend fun createBill(params: CreateBillParams): String = error("not used here")
        override suspend fun insertItem(insert: ShoppingItemInsert): String = error("not used here")
        override suspend fun markPurchased(id: String, update: ShoppingItemPurchase) = error("not used here")
        override suspend fun replayPurchase(id: String, update: ShoppingItemPurchase): Int = error("not used here")
        override suspend fun findMatchingBill(params: CreateBillParams, issuerId: String?, createdAtOrAfterIso: String): String? =
            error("not used here")
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
    }

    @After
    fun tearDown() = db.close()

    private fun billRow(id: String, amount: Double, shares: List<BillShareWithProfileRow> = emptyList()) =
        BillWithSharesRow(
            id = id, household_id = "h1", title = "Bill $id", amount = amount, due_date = "2026-01-01",
            split_method = "EQUAL", issued_by_user_id = "u1", created_at = "c", updated_at = "u",
            repeat = "Monthly", bill_shares = shares
        )

    private fun shareRow(id: String, amount: Double, name: String? = "Thandi") =
        BillShareWithProfileRow(id = id, user_id = "u2", amount_owed = amount, is_paid = false,
            profiles = name?.let { ProfileNameRow(it) })

    private fun choreRow(id: String) = ChoreRow(id, "h1", "Chore $id", "WEEKLY", 5, listOf("u1", "u2"), 0, "c-$id")

    private fun itemRow(id: String) = ShoppingItemRow(
        id = id, household_id = "h1", name = "Item $id", added_by_user_id = ProfileNameRow("Sipho"),
        is_purchased = false, created_at = "c-$id"
    )

    private fun bills() = CachedBillsRepository(remote, db.billDao(), db.memberDao(), OutboxQueue(db), household)
    private fun chores() = CachedChoresRepository(remote, db.choreDao(), household)
    private fun shopping() = CachedShoppingRepository(remote, db.shoppingItemDao(), db.memberDao(), OutboxQueue(db), household)

    @Test
    fun bills_failedRefreshAfterOnlineLoad_stillEmitsCachedRows() = runBlocking {
        val repo = bills()
        remote.bills = listOf(billRow("b1", 300.0, listOf(shareRow("s1", 150.0))))
        repo.refresh()

        remote.offline = true
        val failure = runCatching { repo.refresh() }.exceptionOrNull()

        assertTrue(failure is IOException)
        val cached = repo.bills.first()
        assertEquals(listOf("b1"), cached.map { it.id })
        assertEquals(listOf("s1"), cached.single().bill_shares.map { it.id })
    }

    @Test
    fun bills_newRepositoryOffline_readsCacheFromAnEarlierSession() = runBlocking {
        remote.bills = listOf(billRow("b1", 300.0))
        bills().refresh()

        // Cold start: a fresh repository and a saved household id, no network.
        remote.offline = true
        household = CurrentHousehold(InMemoryHouseholdIdStore("h1"), Dispatchers.Unconfined)
        household.awaitRestored()
        assertEquals(listOf("b1"), bills().bills.first().map { it.id })
    }

    @Test
    fun bills_centsRoundTripExactly() = runBlocking {
        val repo = bills()
        remote.bills = listOf(billRow("b1", 123.45, listOf(shareRow("s1", 61.73), shareRow("s2", 61.72))))
        repo.refresh()

        val stored = db.billDao().observeBills("h1").first().single()
        assertEquals(12345L, stored.bill.amount_cents)
        assertEquals(setOf(6173L, 6172L), stored.shares.map { it.amount_owed_cents }.toSet())

        val row = repo.bills.first().single()
        assertEquals(123.45, row.amount, 0.0)
        assertEquals(setOf(61.73, 61.72), row.bill_shares.map { it.amount_owed }.toSet())
        assertEquals("Thandi", row.bill_shares.first { it.id == "s1" }.profiles?.display_name)
        assertEquals("Monthly", row.repeat)
    }

    @Test
    fun bills_unsyncedLocalRowsSurviveRefresh() = runBlocking {
        val repo = bills()
        db.billDao().upsertBills(
            listOf(BillEntity("local", "h1", "Offline bill", 5_000L, "2026-03-01", "EQUAL", "u1", "c", "u",
                SyncState.PENDING_CREATE))
        )
        db.billDao().upsertShares(
            listOf(BillShareEntity("ls", "local", "h1", "u2", 2_500L, false, syncState = SyncState.PENDING_CREATE))
        )
        remote.bills = listOf(billRow("b1", 100.0))

        repo.refresh()

        val ids = repo.bills.first().map { it.id }.toSet()
        assertEquals(setOf("b1", "local"), ids)
        val local = repo.bills.first().first { it.id == "local" }
        assertEquals(50.0, local.amount, 0.0)
        assertEquals(listOf("ls"), local.bill_shares.map { it.id })
    }

    @Test
    fun bills_refreshDropsBillsDeletedOnServer() = runBlocking {
        val repo = bills()
        remote.bills = listOf(billRow("b1", 1.0), billRow("b2", 2.0))
        repo.refresh()
        remote.bills = listOf(billRow("b2", 2.0))
        repo.refresh()
        assertEquals(listOf("b2"), repo.bills.first().map { it.id })
    }

    @Test
    fun bills_otherHouseholdIsNotShown() = runBlocking {
        val repo = bills()
        remote.bills = listOf(billRow("b1", 1.0))
        repo.refresh()
        household.set("h2")
        assertTrue(repo.bills.first().isEmpty())
    }

    @Test
    fun chores_failedRefreshKeepsCacheAndUnsyncedRows() = runBlocking {
        val repo = chores()
        db.choreDao().upsert(
            listOf(ChoreEntity("local", "h1", "Offline", "WEEKLY", 1, listOf("u1"), 0, "c-z", SyncState.PENDING_CREATE))
        )
        remote.chores = listOf(choreRow("c1"))
        repo.refresh()
        assertEquals(setOf("c1", "local"), repo.chores.first().map { it.id }.toSet())

        remote.offline = true
        assertTrue(runCatching { repo.refresh() }.isFailure)
        assertEquals(setOf("c1", "local"), repo.chores.first().map { it.id }.toSet())
    }

    @Test
    fun shopping_failedRefreshKeepsCacheAndUnsyncedRows() = runBlocking {
        val repo = shopping()
        db.shoppingItemDao().upsert(
            listOf(ShoppingItemEntity(id = "local", household_id = "h1", name = "Milk", is_purchased = false,
                created_at = "c-z", syncState = SyncState.PENDING_UPDATE))
        )
        remote.items = listOf(itemRow("i1"))
        repo.refresh()

        remote.offline = true
        assertTrue(runCatching { repo.refresh() }.isFailure)
        val items = repo.items.first()
        assertEquals(setOf("i1", "local"), items.map { it.id }.toSet())
        assertEquals("Sipho", items.first { it.id == "i1" }.added_by_user_id?.display_name)
    }

    @Test
    fun refresh_withoutHousehold_doesNothing() = runBlocking {
        household = CurrentHousehold(InMemoryHouseholdIdStore(null), Dispatchers.Unconfined)
        remote.bills = listOf(billRow("b1", 1.0))
        bills().refresh()
        assertTrue(bills().bills.first().isEmpty())
        assertFalse(db.billDao().observeBills("h1").first().isNotEmpty())
    }
}
