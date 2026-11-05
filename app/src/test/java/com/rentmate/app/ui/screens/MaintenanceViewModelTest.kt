package com.rentmate.app.ui.screens

import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRepository
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.Urgency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * RM-0001: being offline must show an error state on S9, never crash.
 * The fake repository throws IOException, which is what Ktor raises in airplane mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelTest {

    /** Fails every call while [offline] is true; succeeds otherwise. */
    private class FakeMaintenanceRepository(var offline: Boolean) : MaintenanceRepository {
        override val requests: StateFlow<List<MaintenanceRequest>> = MutableStateFlow(emptyList())
        var refreshCalls = 0

        private fun failIfOffline() {
            if (offline) throw IOException("Unable to resolve host")
        }

        override suspend fun refresh() {
            refreshCalls++
            failIfOffline()
        }

        override suspend fun add(request: MaintenanceRequest) = failIfOffline()
        override suspend fun advance(id: String) = failIfOffline()
        override suspend fun sendOpenRequests(): Int {
            failIfOffline()
            return 0
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading offline shows the load error instead of crashing`() {
        val viewModel = MaintenanceViewModel(FakeMaintenanceRepository(offline = true))

        assertEquals(MaintenanceError.LOAD, viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `logging a request offline shows the save error`() {
        val viewModel = MaintenanceViewModel(FakeMaintenanceRepository(offline = true))

        val titleError = viewModel.log("Leaking tap", "", MaintenanceCategory.PLUMBING, Urgency.HIGH, null)

        assertNull(titleError)
        assertEquals(MaintenanceError.SAVE, viewModel.state.value.error)
    }

    @Test
    fun `advancing and sending offline show their own errors`() {
        val viewModel = MaintenanceViewModel(FakeMaintenanceRepository(offline = true))

        viewModel.advance("any-id")
        assertEquals(MaintenanceError.UPDATE, viewModel.state.value.error)

        viewModel.sendOpenRequests()
        assertEquals(MaintenanceError.SEND, viewModel.state.value.error)
    }

    @Test
    fun `retry after reconnecting clears the error`() {
        val repository = FakeMaintenanceRepository(offline = true)
        val viewModel = MaintenanceViewModel(repository)
        assertEquals(MaintenanceError.LOAD, viewModel.state.value.error)

        repository.offline = false
        viewModel.refresh()

        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun `dismissing hides the error`() {
        val viewModel = MaintenanceViewModel(FakeMaintenanceRepository(offline = true))

        viewModel.dismissError()

        assertNull(viewModel.state.value.error)
    }
}
