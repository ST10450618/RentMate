package com.rentmate.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.rentmate.app.data.CachedBillsRepository
import com.rentmate.app.data.CachedShoppingRepository
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.InMemoryHouseholdIdStore
import com.rentmate.app.data.local.OutboxQueue
import com.rentmate.app.data.local.RentMateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * RM-0018: [SyncWorker] under Robolectric with TestListenableWorkerBuilder. The
 * worker is built by a small factory, as HiltWorkerFactory would do in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var db: RentMateDatabase
    private val remote = FakeSyncRemote()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RentMateDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun worker(): SyncWorker {
        val household = CurrentHousehold(InMemoryHouseholdIdStore("h1"), Dispatchers.Unconfined)
        val replayer = OutboxReplayer(
            db, remote, remote,
            CachedBillsRepository(remote, db.billDao(), db.memberDao(), OutboxQueue(db), household),
            CachedShoppingRepository(remote, db.shoppingItemDao(), db.memberDao(), OutboxQueue(db), household),
            RecordingActivity(), FakeAttachments(), household,
            SyncStatusTracker(OutboxQueue(db), db.syncRejectionDao())
        )
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                SyncWorker(appContext, workerParameters, replayer)
        }
        return TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build()
    }

    private suspend fun queueItem() {
        remote.offline = true
        CachedShoppingRepository(
            remote, db.shoppingItemDao(), db.memberDao(), OutboxQueue(db),
            CurrentHousehold(InMemoryHouseholdIdStore("h1"), Dispatchers.Unconfined)
        ).addItem("h1", "Milk", "u1")
    }

    @Test
    fun emptyOutbox_succeeds() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test
    fun queuedEntry_isSentAndSucceeds() = runBlocking {
        queueItem()
        remote.offline = false
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(listOf("insert Milk"), remote.calls)
        assertEquals(0, db.outboxDao().count())
    }

    @Test
    fun offline_asksForRetry() = runBlocking {
        queueItem()
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(1, db.outboxDao().count())
    }

    @Test
    fun workRequest_needsNetworkAndBacksOffExponentially() {
        val request = WorkManagerSyncScheduler.buildRequest()
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(androidx.work.BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(WorkManagerSyncScheduler.BACKOFF_SECONDS), request.workSpec.backoffDelayDuration)
    }
}
