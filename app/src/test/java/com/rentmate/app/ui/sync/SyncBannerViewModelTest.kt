package com.rentmate.app.ui.sync

import com.rentmate.app.data.local.SyncRejectionDao
import com.rentmate.app.data.local.SyncRejectionEntity
import com.rentmate.app.data.sync.ConnectivityMonitor
import com.rentmate.app.data.sync.DropReason
import com.rentmate.app.data.sync.SyncPhase
import com.rentmate.app.data.sync.SyncScheduler
import com.rentmate.app.data.sync.SyncStatus
import com.rentmate.app.data.sync.SyncStatusSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** RM-0019: the banner rules and the once-only rejection notice, with fakes instead of Android. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncBannerViewModelTest {

    private class FakeConnectivity : ConnectivityMonitor {
        val online = MutableStateFlow(true)
        override val isOnline: Flow<Boolean> = online
    }

    /** Holds the rejection rows, so deleting them updates the status like Room would. */
    private class FakeSync : SyncStatusSource, SyncRejectionDao {
        val run = MutableStateFlow<Pair<SyncPhase, Int>>(SyncPhase.Idle to 0)
        val rows = MutableStateFlow<List<SyncRejectionEntity>>(emptyList())
        val deleted = mutableListOf<List<Long>>()

        override val status: Flow<SyncStatus> = kotlinx.coroutines.flow.combine(run, rows) { (phase, pending), r ->
            SyncStatus(phase, null, pending, r)
        }

        override suspend fun insert(rejection: SyncRejectionEntity): Long {
            val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
            rows.update { listOf(rejection.copy(id = id)) + it }
            return id
        }

        override fun observeAll(): Flow<List<SyncRejectionEntity>> = rows.map { it }
        override suspend fun deleteById(id: Long) = deleteByIds(listOf(id))
        override suspend fun deleteByIds(ids: List<Long>) {
            deleted += ids
            rows.update { list -> list.filterNot { it.id in ids } }
        }

        override suspend fun clear() = rows.update { emptyList() }
    }

    private class CountingScheduler : SyncScheduler {
        var requests = 0
        var syncNows = 0
        override fun requestSync() { requests++ }
        override fun syncNow() { syncNows++ }
    }

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivity()
    private val sync = FakeSync()
    private val scheduler = CountingScheduler()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.start(): SyncBannerViewModel {
        val vm = SyncBannerViewModel(connectivity, sync, sync, scheduler)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun SyncBannerViewModel.banner() = state.value.banner

    private fun rejection(reason: DropReason, dependant: Boolean = false, at: Long = 1) =
        SyncRejectionEntity(op = "INSERT", tableName = "bills", entityId = "b$at", reason = reason.name, dependant = dependant, rejectedAt = at)

    @Test
    fun `online with nothing pending shows no banner`() = runTest(dispatcher) {
        val vm = start()
        assertEquals(BannerState.Hidden, vm.banner())
    }

    @Test
    fun `offline shows the offline banner`() = runTest(dispatcher) {
        connectivity.online.value = false
        val vm = start()
        assertEquals(BannerState.Offline(0), vm.banner())
    }

    @Test
    fun `offline with pending changes shows the count`() = runTest(dispatcher) {
        connectivity.online.value = false
        sync.run.value = SyncPhase.Idle to 3
        val vm = start()
        assertEquals(BannerState.Offline(3), vm.banner())
    }

    @Test
    fun `back online with pending changes shows syncing`() = runTest(dispatcher) {
        connectivity.online.value = false
        sync.run.value = SyncPhase.Idle to 2
        val vm = start()

        connectivity.online.value = true
        sync.run.value = SyncPhase.Syncing to 2
        runCurrent()
        assertEquals(BannerState.Syncing(2), vm.banner())
    }

    @Test
    fun `the banner says synced and then clears once the queue is empty`() = runTest(dispatcher) {
        connectivity.online.value = false
        sync.run.value = SyncPhase.Idle to 2
        val vm = start()

        connectivity.online.value = true
        sync.run.value = SyncPhase.Syncing to 1
        runCurrent()
        sync.run.value = SyncPhase.Idle to 0
        runCurrent()
        assertEquals(BannerState.Synced, vm.banner())

        advanceTimeBy(SyncBannerViewModel.SYNCED_MS + 1)
        runCurrent()
        assertEquals(BannerState.Hidden, vm.banner())
    }

    @Test
    fun `a run that finds an empty queue does not flash the banner`() = runTest(dispatcher) {
        val vm = start()
        sync.run.value = SyncPhase.Syncing to 0
        runCurrent()
        assertEquals(BannerState.Hidden, vm.banner())
    }

    @Test
    fun `a failed run offers retry, which asks for a sync now`() = runTest(dispatcher) {
        sync.run.value = SyncPhase.Failed("HTTP 500") to 2
        val vm = start()
        assertEquals(BannerState.Failed(2), vm.banner())

        vm.retry()
        runCurrent()
        assertEquals(1, scheduler.syncNows)
        assertEquals(BannerState.Syncing(2), vm.banner())

        // The worker starts and fails again: Retry comes back.
        sync.run.value = SyncPhase.Syncing to 2
        runCurrent()
        sync.run.value = SyncPhase.Failed("HTTP 500") to 2
        runCurrent()
        assertEquals(BannerState.Failed(2), vm.banner())
    }

    @Test
    fun `retry is offered again if the worker never starts`() = runTest(dispatcher) {
        sync.run.value = SyncPhase.Failed("timeout") to 1
        val vm = start()
        vm.retry()
        runCurrent()
        assertEquals(BannerState.Syncing(1), vm.banner())

        advanceTimeBy(SyncBannerViewModel.RETRY_FEEDBACK_MS + 1)
        runCurrent()
        assertEquals(BannerState.Failed(1), vm.banner())
    }

    @Test
    fun `a failed run while offline shows the offline banner, not retry`() = runTest(dispatcher) {
        connectivity.online.value = false
        sync.run.value = SyncPhase.Failed("offline") to 1
        val vm = start()
        assertEquals(BannerState.Offline(1), vm.banner())
    }

    @Test
    fun `a rejection is shown until dismissed, then never again`() = runTest(dispatcher) {
        val vm = start()
        sync.insert(rejection(DropReason.NOT_ALLOWED))
        runCurrent()
        val notice = vm.state.value.notice!!
        assertEquals(RejectionKind.NOT_ALLOWED, notice.kind)
        assertEquals(0, notice.others)

        vm.dismiss(notice)
        runCurrent()
        assertEquals(listOf(notice.ids), sync.deleted)
        assertNull(vm.state.value.notice)

        // A later rejection is a new notice; the old one does not come back.
        sync.insert(rejection(DropReason.TARGET_MISSING, at = 2))
        runCurrent()
        val next = vm.state.value.notice!!
        assertEquals(RejectionKind.GONE, next.kind)
        assertEquals(1, next.ids.size)
    }

    @Test
    fun `queue emptied by a rejection skips the synced message`() = runTest(dispatcher) {
        sync.run.value = SyncPhase.Syncing to 1
        val vm = start()
        sync.insert(rejection(DropReason.INVALID))
        sync.run.value = SyncPhase.Idle to 0
        runCurrent()
        assertEquals(BannerState.Hidden, vm.banner())
        assertEquals(RejectionKind.NOT_SAVED, vm.state.value.notice?.kind)
    }

    @Test
    fun `the notice describes the oldest change and counts the others, not dependants`() {
        val rows = listOf(
            rejection(DropReason.INVALID, dependant = true, at = 4).copy(id = 4),
            rejection(DropReason.INVALID, at = 3).copy(id = 3),
            rejection(DropReason.TOO_MANY_ATTEMPTS, at = 2).copy(id = 2),
            rejection(DropReason.NOT_ALLOWED, at = 1).copy(id = 1)
        )
        val notice = SyncBannerViewModel.noticeFor(rows)!!
        assertEquals(RejectionKind.NOT_ALLOWED, notice.kind)
        assertEquals(2, notice.others)
        assertEquals(listOf(4L, 3L, 2L, 1L), notice.ids)
    }

    @Test
    fun `every drop reason maps to a user message`() {
        assertEquals(RejectionKind.NOT_ALLOWED, SyncBannerViewModel.kindOf(DropReason.NOT_ALLOWED.name))
        assertEquals(RejectionKind.GONE, SyncBannerViewModel.kindOf(DropReason.TARGET_MISSING.name))
        listOf(DropReason.INVALID, DropReason.TOO_MANY_ATTEMPTS, DropReason.DEPENDS_ON_DROPPED).forEach {
            assertEquals(RejectionKind.NOT_SAVED, SyncBannerViewModel.kindOf(it.name))
        }
        assertEquals(RejectionKind.NOT_SAVED, SyncBannerViewModel.kindOf("SOMETHING_NEW"))
    }
}
