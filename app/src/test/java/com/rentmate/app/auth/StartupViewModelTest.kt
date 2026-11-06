package com.rentmate.app.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** RM-0002: a saved session skips Login; anything else falls back to Login safely. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {

    private class FakeSessionRepository(private val result: suspend () -> StartDestination) : SessionRepository {
        override suspend fun restoreSession(): StartDestination = result()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun startWith(result: suspend () -> StartDestination) =
        StartupViewModel(FakeSessionRepository(result)).state.value

    @Test
    fun `a saved session with a household starts on the Dashboard`() {
        assertEquals(StartupState.Ready(StartDestination.DASHBOARD), startWith { StartDestination.DASHBOARD })
    }

    @Test
    fun `a saved session without a household starts on household setup`() {
        assertEquals(
            StartupState.Ready(StartDestination.HOUSEHOLD_SETUP),
            startWith { StartDestination.HOUSEHOLD_SETUP }
        )
    }

    @Test
    fun `no saved session starts on Login`() {
        assertEquals(StartupState.Ready(StartDestination.LOGIN), startWith { StartDestination.LOGIN })
    }

    @Test
    fun `a failed restore falls back to Login instead of crashing`() {
        assertEquals(StartupState.Ready(StartDestination.LOGIN), startWith { throw IOException("offline") })
    }

    @Test
    fun `shows the splash until the session has loaded`() {
        val gate = CompletableDeferred<StartDestination>()
        val viewModel = StartupViewModel(FakeSessionRepository { gate.await() })
        assertEquals(StartupState.Loading, viewModel.state.value)

        gate.complete(StartDestination.DASHBOARD)

        assertEquals(StartupState.Ready(StartDestination.DASHBOARD), viewModel.state.value)
    }
}
