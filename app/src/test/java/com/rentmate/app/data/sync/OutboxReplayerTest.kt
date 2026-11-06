package com.rentmate.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.BillsRemoteSource
import com.rentmate.app.data.CachedBillsRepository
import com.rentmate.app.data.CachedShoppingRepository
import com.rentmate.app.data.CreateBillParams
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.HouseholdMemberRow
import com.rentmate.app.data.InMemoryHouseholdIdStore
import com.rentmate.app.data.NewBill
import com.rentmate.app.data.RepeatMode
import com.rentmate.app.data.ShoppingItemInsert
import com.rentmate.app.data.ShoppingItemPurchase
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.data.ShoppingRemoteSource
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.SplitShare
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.attachments.Attachment
import com.rentmate.app.data.attachments.AttachmentKind
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.UploadProgress
import com.rentmate.app.data.local.HouseholdMemberEntity
import com.rentmate.app.data.local.OutboxQueue
import com.rentmate.app.data.local.RentMateDatabase
import com.rentmate.app.data.local.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.time.Duration

/**
 * RM-0018: the replayer against an in-memory Room database and a fake server.
 * Entries are queued through the real repositories while "offline", then
 * replayed once the fake comes back online.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboxReplayerTest {

    private lateinit var db: RentMateDatabase
    private lateinit var household: CurrentHousehold
    private val remote = FakeSyncRemote()
    private val activity = RecordingActivity()
    private val attachments = FakeAttachments()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RentMateDatabase::class.java).allowMainThreadQueries().build()
        household = CurrentHousehold(InMemoryHouseholdIdStore("h1"), Dispatchers.Unconfined)
        runBlocking {
            db.memberDao().upsert(listOf(HouseholdMemberEntity("h1", "u1", "Sipho"), HouseholdMemberEntity("h1", "u2", "Thandi")))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun bills() = CachedBillsRepository(remote, db.billDao(), db.memberDao(), OutboxQueue(db), household)
    private fun shopping() = CachedShoppingRepository(remote, db.shoppingItemDao(), db.memberDao(), OutboxQueue(db), household)
    private val status by lazy { SyncStatusTracker(OutboxQueue(db), db.syncRejectionDao()) }
    private fun replayer() =
        OutboxReplayer(db, remote, remote, bills(), shopping(), activity, attachments, household, status).also { it.now = { 5_000L } }

    private fun newBill(photos: List<String> = emptyList()) = NewBill(
        "h1", "Electricity", BigDecimal("100.00"), LocalDate.of(2026, 12, 1), SplitMode.Equal,
        listOf(SplitShare("u1", 0L, BigDecimal.ZERO), SplitShare("u2", 0L, BigDecimal.ZERO)), RepeatMode.Never, "u1", photos
    )

    @Test
    fun queuedInsertAndPurchase_replayInOrder() = runBlocking {
        remote.offline = true
        val itemId = shopping().addItem("h1", "Milk", "u1").id
        shopping().markPurchased(itemId, "u2")
        assertEquals(2, db.outboxDao().count())

        remote.offline = false
        assertEquals(ReplayResult.DONE, replayer().run())

        assertEquals(listOf("insert Milk", "purchase $itemId"), remote.calls)
        assertEquals(0, db.outboxDao().count())
        val row = db.shoppingItemDao().getById(itemId)!!
        assertEquals(SyncState.SYNCED, row.syncState)
        assertTrue(row.is_purchased)
        assertEquals(listOf("shoppingAdded Milk", "shoppingBought Milk"), activity.events)
        assertEquals(SyncPhase.Idle, status.phase)
        assertEquals(5_000L, status.lastSyncedAt)
    }

    @Test
    fun createBillReplay_replacesLocalPendingBillWithServerCopy() = runBlocking {
        remote.offline = true
        attachments.stored = setOf("h1/bill/a.jpg")
        val localId = bills().createBill(newBill(photos = listOf("h1/bill/a.jpg", "h1/bill/gone.jpg"))).id
        assertEquals(2, db.billDao().observeBills("h1").first().single().shares.size)
        assertEquals(listOf("h1/bill/a.jpg", "h1/bill/gone.jpg"), db.outboxDao().getAllOldestFirst().single().attachmentPaths)

        remote.offline = false
        val fetchesBefore = remote.billFetches
        assertEquals(ReplayResult.DONE, replayer().run())

        assertEquals(listOf("find_bill Electricity issuer=u1", "create_bill Electricity"), remote.calls)
        assertNull(db.billDao().getBill(localId))
        assertTrue(db.billDao().getUnsyncedShares("h1").isEmpty())
        assertTrue(remote.billFetches > fetchesBefore)
        val cached = db.billDao().observeBills("h1").first().single()
        assertEquals("server-bill-1", cached.bill.id)
        assertEquals(SyncState.SYNCED, cached.bill.syncState)
        assertEquals(0, db.outboxDao().count())
        assertEquals(listOf("billAdded Electricity server-bill-1"), activity.events)
        // The photo missing from Storage is skipped; the other is linked to the server id.
        assertEquals(listOf(AttachmentParent.Bill("server-bill-1") to listOf("h1/bill/a.jpg")), attachments.links)
    }

    @Test
    fun duplicateGuard_skipsABillThatIsAlreadyOnTheServer() = runBlocking {
        remote.offline = true
        val localId = bills().createBill(newBill()).id

        remote.offline = false
        remote.existingBillId = "server-earlier"
        assertEquals(ReplayResult.DONE, replayer().run())

        assertEquals(listOf("find_bill Electricity issuer=u1"), remote.calls)
        assertNull(db.billDao().getBill(localId))
        assertEquals(0, db.outboxDao().count())
        assertEquals(listOf("billAdded Electricity server-earlier"), activity.events)
    }

    @Test
    fun offlineFailure_stopsTheRunAndLosesNothing() = runBlocking {
        remote.offline = true
        val itemId = shopping().addItem("h1", "Bread", "u1").id
        bills().createBill(newBill())
        val before = db.outboxDao().getAllOldestFirst()

        assertEquals(ReplayResult.RETRY, replayer().run())

        assertEquals(before, db.outboxDao().getAllOldestFirst())
        assertEquals(SyncState.PENDING_CREATE, db.shoppingItemDao().getById(itemId)?.syncState)
        assertEquals(1, db.billDao().getUnsyncedBills("h1").size)
        assertTrue(activity.events.isEmpty())
        assertTrue(status.phase is SyncPhase.Failed)
    }

    @Test
    fun retryableFailure_neverSkipsAhead() = runBlocking {
        remote.offline = true
        val itemId = shopping().addItem("h1", "Eggs", "u1").id
        shopping().markPurchased(itemId, "u1")
        shopping().addItem("h1", "Tea", "u1")

        remote.offline = false
        remote.purchaseFailure = IllegalStateException("HTTP 502")
        assertEquals(ReplayResult.RETRY, replayer().run())

        // The insert went through; the purchase failed, so "Tea" behind it waits.
        assertEquals(listOf("insert Eggs"), remote.calls)
        val left = db.outboxDao().getAllOldestFirst()
        assertEquals(2, left.size)
        assertEquals(1, left.first().attemptCount)
        assertEquals("HTTP 502", left.first().lastError)
        // The item keeps its pending purchase, so a refresh cannot overwrite it.
        assertEquals(SyncState.PENDING_UPDATE, db.shoppingItemDao().getById(itemId)?.syncState)
    }

    @Test
    fun purchaseOfDeletedItem_isDroppedAndRecorded() = runBlocking {
        // A synced item another housemate deleted on the server while we were offline.
        remote.offline = true
        db.shoppingItemDao().upsert(
            listOf(com.rentmate.app.data.local.ShoppingItemEntity("gone", "h1", "Rice", is_purchased = false, created_at = "c"))
        )
        shopping().markPurchased("gone", "u1")

        remote.offline = false
        assertEquals(ReplayResult.DONE, replayer().run())

        assertEquals(0, db.outboxDao().count())
        assertNull(db.shoppingItemDao().getById("gone"))
        val rejection = db.syncRejectionDao().observeAll().first().single()
        assertEquals(DropReason.TARGET_MISSING.name, rejection.reason)
        assertEquals("gone", rejection.entityId)
    }

    @Test
    fun rejectedInsert_dropsItsPurchaseToo() = runBlocking {
        remote.offline = true
        val itemId = shopping().addItem("h1", "Soap", "u1").id
        shopping().markPurchased(itemId, "u1")
        // The insert payload cannot be read: a permanent failure.
        db.openHelper.writableDatabase.execSQL("UPDATE outbox SET payloadJson = 'not json' WHERE op = 'INSERT'")

        remote.offline = false
        assertEquals(ReplayResult.DONE, replayer().run())

        assertTrue(remote.calls.isEmpty())
        assertEquals(0, db.outboxDao().count())
        assertNull(db.shoppingItemDao().getById(itemId))
        val reasons = db.syncRejectionDao().observeAll().first().map { it.reason }.toSet()
        assertEquals(setOf(DropReason.INVALID.name, DropReason.DEPENDS_ON_DROPPED.name), reasons)
        assertNotNull(status.lastSyncedAt)
    }
}
