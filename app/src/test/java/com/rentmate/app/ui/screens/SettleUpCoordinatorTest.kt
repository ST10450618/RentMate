package com.rentmate.app.ui.screens

import com.rentmate.app.R
import com.rentmate.app.data.BillShareWithProfileRow
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.DebtSimplifier
import com.rentmate.app.data.HouseholdMemberRow
import com.rentmate.app.data.Obligation
import com.rentmate.app.data.ProfileNameRow
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.data.SettleUpSource
import com.rentmate.app.data.local.centsToRand
import com.rentmate.app.data.localSettleUp
import com.rentmate.app.data.sync.SyncPhase
import com.rentmate.app.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** RM-0031: Settle-up shows a labelled local preview offline and switches to the server plan after a sync. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettleUpCoordinatorTest {

    /** settle_up on a fake server; throws IOException while [offline]. */
    private class FakeSettleUpSource(var offline: Boolean, var rows: List<SettleUpRow> = emptyList()) : SettleUpSource {
        var calls = 0
        var failure: Exception? = null
        override suspend fun settleUp(householdId: String): List<SettleUpRow> {
            calls++
            if (offline) throw IOException("Unable to resolve host")
            failure?.let { throw it }
            return rows
        }
    }

    private val members = MutableStateFlow(
        listOf(HouseholdMemberRow("a", "Ann"), HouseholdMemberRow("b", "Ben"), HouseholdMemberRow("c", "Cara"))
    )
    private val bills = MutableStateFlow(seededLedger())
    private val sync = MutableStateFlow(SyncStatus(SyncPhase.Idle, lastSyncedAt = null, pendingCount = 0, rejected = emptyList()))
    private val errors = mutableListOf<Throwable>()

    private val serverPlan = listOf(SettleUpRow("b", "Ben", "a", "Ann", 123.45))

    private fun share(id: String, user: String, rand: Double, paid: Boolean = false, name: String? = null) =
        BillShareWithProfileRow(id = id, user_id = user, amount_owed = rand, is_paid = paid, profiles = name?.let { ProfileNameRow(it) })

    private fun bill(id: String, issuer: String, vararg shares: BillShareWithProfileRow, pending: Boolean = false) =
        BillWithSharesRow(
            id = id, household_id = "h1", title = "Bill $id", amount = shares.sumOf { it.amount_owed },
            due_date = "2026-11-01T00:00:00Z", split_method = "Equal", issued_by_user_id = issuer,
            created_at = "c", updated_at = "u", bill_shares = shares.toList(), pending = pending
        )

    /** Ann paid rent, Ben paid power; one share is already paid, and issuers' own shares are ignored. */
    private fun seededLedger() = listOf(
        bill("rent", "a", share("r1", "a", 300.0), share("r2", "b", 300.0), share("r3", "c", 300.0)),
        bill("power", "b", share("p1", "a", 50.25), share("p2", "b", 50.25), share("p3", "c", 50.25)),
        bill("wifi", "c", share("w1", "a", 20.0, paid = true), share("w2", "b", 20.0), share("w3", "c", 20.0))
    )

    private fun expectedFor(ledger: List<BillWithSharesRow>): List<Triple<String, String, Double>> {
        val obligations = ledger.flatMap { b ->
            b.bill_shares.filter { !it.is_paid }.map { Obligation(b.issued_by_user_id, it.user_id, Math.round(it.amount_owed * 100)) }
        }
        return DebtSimplifier.simplify(obligations).map { Triple(it.from, it.to, it.cents.centsToRand()) }
    }

    private fun List<SettleUpRow>.triples() = map { Triple(it.from_user_id, it.to_user_id, it.amount) }

    private fun coordinator(scope: CoroutineScope, source: SettleUpSource) = SettleUpCoordinator(
        scope = scope,
        source = source,
        householdId = { "h1" },
        bills = bills,
        members = members,
        syncStatus = sync,
        onError = { errors += it }
    )

    @Test
    fun localPreview_equalsDebtSimplifierOutput_withMemberNames() {
        val rows = localSettleUp(seededLedger(), members.value)
        assertEquals(expectedFor(seededLedger()), rows.triples())
        assertTrue(rows.isNotEmpty())
        val names = mapOf("a" to "Ann", "b" to "Ben", "c" to "Cara")
        rows.forEach {
            assertEquals(names[it.from_user_id], it.from_display_name)
            assertEquals(names[it.to_user_id], it.to_display_name)
        }
    }

    @Test
    fun localPreview_includesPendingBills() {
        val ledger = seededLedger() + bill("gas", "c", share("g1", "a", 75.5), share("g2", "c", 75.5), pending = true)
        val rows = localSettleUp(ledger, members.value)
        assertEquals(expectedFor(ledger), rows.triples())
        assertTrue(rows != localSettleUp(seededLedger(), members.value))
    }

    @Test
    fun localPreview_unknownNamesAreBlank_shareNameUsedWhenNoMember() {
        val ledger = listOf(bill("x", "z", share("x1", "y", 10.0, name = "Yusuf")))
        val rows = localSettleUp(ledger, emptyList())
        assertEquals(listOf(SettleUpRow("y", "Yusuf", "z", "", 10.0)), rows)
    }

    @Test
    fun online_showsServerPlan_notProvisional() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSettleUpSource(offline = false, rows = serverPlan)
        val c = coordinator(backgroundScope, source)

        c.open()

        assertEquals(SettleUpState(visible = true, rows = serverPlan), c.state.value)
        assertNull(c.confirmBlockedReason())
        assertEquals(1, source.calls)
    }

    @Test
    fun offline_showsProvisionalLocalPreview() = runTest(UnconfinedTestDispatcher()) {
        val c = coordinator(backgroundScope, FakeSettleUpSource(offline = true))

        c.open()

        val s = c.state.value
        assertTrue(s.visible)
        assertTrue(s.provisional)
        assertFalse(s.loading)
        assertEquals(localSettleUp(bills.value, members.value), s.rows)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun confirm_isBlockedWhileProvisional() = runTest(UnconfinedTestDispatcher()) {
        val c = coordinator(backgroundScope, FakeSettleUpSource(offline = true))
        c.open()
        assertEquals(R.string.settle_up_confirm_needs_sync, c.confirmBlockedReason())
    }

    @Test
    fun pendingBill_showsPreviewWithoutAskingTheServer() = runTest(UnconfinedTestDispatcher()) {
        bills.value = seededLedger() + bill("gas", "c", share("g1", "a", 75.5), pending = true)
        val source = FakeSettleUpSource(offline = false, rows = serverPlan)
        val c = coordinator(backgroundScope, source)

        c.open()

        assertTrue(c.state.value.provisional)
        assertEquals(localSettleUp(bills.value, members.value), c.state.value.rows)
        assertEquals(0, source.calls)
    }

    @Test
    fun previewFollowsTheLedgerWhileOpen() = runTest(UnconfinedTestDispatcher()) {
        val c = coordinator(backgroundScope, FakeSettleUpSource(offline = true))
        c.open()
        bills.value = seededLedger().drop(1)
        assertEquals(localSettleUp(bills.value, members.value), c.state.value.rows)
    }

    @Test
    fun afterSync_switchesToServerPlan() = runTest(UnconfinedTestDispatcher()) {
        val pendingBill = bill("gas", "c", share("g1", "a", 75.5), pending = true)
        bills.value = seededLedger() + pendingBill
        sync.value = sync.value.copy(pendingCount = 1)
        val source = FakeSettleUpSource(offline = true, rows = serverPlan)
        val c = coordinator(backgroundScope, source)
        c.open()
        assertTrue(c.state.value.provisional)

        // Connection back: the worker uploads, refreshes the bills, then reports success.
        source.offline = false
        sync.value = sync.value.copy(phase = SyncPhase.Syncing)
        bills.value = seededLedger() + pendingBill.copy(pending = false)
        sync.value = sync.value.copy(phase = SyncPhase.Idle, pendingCount = 0, lastSyncedAt = 1_000L)

        assertEquals(SettleUpState(visible = true, rows = serverPlan), c.state.value)
        assertNull(c.confirmBlockedReason())
    }

    @Test
    fun successfulBillsRefresh_switchesToServerPlan() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSettleUpSource(offline = true, rows = serverPlan)
        val c = coordinator(backgroundScope, source)
        c.open()
        assertTrue(c.state.value.provisional)

        source.offline = false
        c.onBillsRefreshed()

        assertEquals(SettleUpState(visible = true, rows = serverPlan), c.state.value)
    }

    @Test
    fun retryTimer_switchesToServerPlanWhenNetworkReturns() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSettleUpSource(offline = true, rows = serverPlan)
        val c = coordinator(backgroundScope, source)
        c.open()

        advanceTimeBy(SETTLE_UP_RETRY_MS + 1)
        runCurrent()
        assertTrue("still offline, still a preview", c.state.value.provisional)

        source.offline = false
        advanceTimeBy(SETTLE_UP_RETRY_MS + 1)
        runCurrent()
        assertEquals(SettleUpState(visible = true, rows = serverPlan), c.state.value)
    }

    @Test
    fun serverErrorThatIsNotOffline_closesAndReports() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSettleUpSource(offline = false).apply { failure = IllegalStateException("boom") }
        val c = coordinator(backgroundScope, source)

        c.open()

        assertEquals(SettleUpState(), c.state.value)
        assertEquals(1, errors.size)
    }

    @Test
    fun dismiss_stopsReconciling() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSettleUpSource(offline = true, rows = serverPlan)
        val c = coordinator(backgroundScope, source)
        c.open()
        c.dismiss()
        val callsAfterDismiss = source.calls

        source.offline = false
        sync.value = sync.value.copy(lastSyncedAt = 5L)
        advanceTimeBy(SETTLE_UP_RETRY_MS * 2)
        runCurrent()

        assertEquals(SettleUpState(), c.state.value)
        assertEquals(callsAfterDismiss, source.calls)
    }
}
