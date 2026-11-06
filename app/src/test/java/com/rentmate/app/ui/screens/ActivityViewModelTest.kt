package com.rentmate.app.ui.screens

import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.InMemoryHouseholdIdStore
import com.rentmate.app.data.activity.ActivityEvent
import com.rentmate.app.data.activity.ActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** RM-0035: the Activity error state's Retry button subscribes to the feed again. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    /** Fails the first subscription, then returns an empty feed. */
    private class FlakyRepository : ActivityRepository {
        var subscriptions = 0
        override fun observe(householdId: String, limit: Int): Flow<List<ActivityEvent>> {
            subscriptions++
            return if (subscriptions == 1) flow { throw IOException("offline") } else flowOf(emptyList())
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
    fun `retry after a failed load subscribes again and shows the feed`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FlakyRepository()
        val household = CurrentHousehold(InMemoryHouseholdIdStore(), Dispatchers.Unconfined)
        household.set("h1")
        val viewModel = ActivityViewModel(repository, household)
        val collector = backgroundScope.launch { viewModel.state.collect {} }

        assertTrue(viewModel.state.value is ActivityUiState.Error)

        viewModel.retry()

        assertEquals(2, repository.subscriptions)
        assertEquals(ActivityUiState.Events(emptyList()), viewModel.state.value)
        collector.cancel()
    }
}
