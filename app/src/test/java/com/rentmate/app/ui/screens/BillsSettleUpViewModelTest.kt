package com.rentmate.app.ui.screens

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rentmate.app.R
import com.rentmate.app.data.BillShareWithProfileRow
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.BillsRepository
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.HouseholdMemberRow
import com.rentmate.app.data.InMemoryHouseholdIdStore
import com.rentmate.app.data.NewBill
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.data.SettleUpSource
import com.rentmate.app.data.WriteResult
import com.rentmate.app.data.local.OutboxQueue
import com.rentmate.app.data.local.RentMateDatabase
import com.rentmate.app.data.sync.FakeAttachments
import com.rentmate.app.data.sync.RecordingActivity
import com.rentmate.app.data.sync.SyncStatusTracker
import com.rentmate.app.ui.feedback.FeedbackCenter
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/** RM-0031: the Bills screen's view model shows an offline preview and will not confirm from it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillsSettleUpViewModelTest {

    /** Cached bills in a flow; refresh fails while [offline]. */
    private class FakeBillsRepository(var offline: Boolean) : BillsRepository {
        override val bills = MutableStateFlow(
            listOf(
                BillWithSharesRow(
                    id = "rent", household_id = "h1", title = "Rent", amount = 200.0, due_date = "2026-11-01T00:00:00Z",
                    split_method = "Equal", issued_by_user_id = "a", created_at = "c", updated_at = "u",
                    bill_shares = listOf(
                        BillShareWithProfileRow("s1", "a", 100.0, false),
                        BillShareWithProfileRow("s2", "b", 100.0, false)
                    )
                )
            )
        )
        override val members = MutableStateFlow(listOf(HouseholdMemberRow("a", "Ann"), HouseholdMemberRow("b", "Ben")))
        override suspend fun refresh() {
            if (offline) throw IOException("Unable to resolve host")
        }
        override suspend fun refreshMembers() = Unit
        override suspend fun createBill(bill: NewBill) = WriteResult.Queued("x")
    }

    private class FakeSource(var offline: Boolean) : SettleUpSource {
        override suspend fun settleUp(householdId: String): List<SettleUpRow> {
            if (offline) throw IOException("Unable to resolve host")
            return listOf(SettleUpRow("b", "Ben", "a", "Ann", 100.0))
        }
    }

    private lateinit var db: RentMateDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RentMateDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun offlinePreview_thenOnlineRefresh_switchesToServerPlan_andConfirmIsBlockedMeanwhile() {
        // No requests are made through this client in these paths.
        val supabase = createSupabaseClient("https://example.supabase.co", "test-key") {
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
            }
            install(Postgrest)
        }
        val household = CurrentHousehold(InMemoryHouseholdIdStore(), Dispatchers.Unconfined)
        household.set("h1")
        val repository = FakeBillsRepository(offline = true)
        val source = FakeSource(offline = true)
        val feedback = FeedbackCenter()
        val tracker = SyncStatusTracker(OutboxQueue(db), db.syncRejectionDao())
        val vm = BillsViewModel(supabase, household, FakeAttachments(), RecordingActivity(), repository, feedback, source, tracker)

        vm.loadSettleUp()
        val offline = vm.state.value
        assertTrue(offline.settleUpProvisional)
        assertEquals(listOf(SettleUpRow("b", "Ben", "a", "Ann", 100.0)), offline.settleUp)

        feedback.drain()
        vm.confirmSettlement("b", 100.0)
        assertEquals(listOf(R.string.settle_up_confirm_needs_sync), feedback.drain().map { it.messageRes })
        assertTrue("dialog stays open", vm.state.value.settleUp != null)

        repository.offline = false
        source.offline = false
        vm.refresh()
        val online = vm.state.value
        assertFalse(online.settleUpProvisional)
        assertEquals(listOf(SettleUpRow("b", "Ben", "a", "Ann", 100.0)), online.settleUp)
    }
}
