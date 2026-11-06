package com.rentmate.app.ui.screens

import com.rentmate.app.R
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.InMemoryHouseholdIdStore
import com.rentmate.app.ui.feedback.FeedbackCenter
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.data.ShoppingRepository
import com.rentmate.app.data.WriteResult
import com.rentmate.app.data.activity.NoOpActivityLogger
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import com.rentmate.app.ui.feedback.UndoQueue
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** RM-0016: offline, the Shopping List keeps showing the cached items plus an error. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {

    /** Cache held in a flow; refresh fails while [offline] is true. */
    private class FakeShoppingRepository(var offline: Boolean) : ShoppingRepository {
        override val items = MutableStateFlow<List<ShoppingItemRow>>(emptyList())
        override suspend fun refresh() {
            if (offline) throw IOException("Unable to resolve host")
        }
        override suspend fun addItem(householdId: String, name: String, addedByUserId: String) = WriteResult.Queued("x")
        override suspend fun markPurchased(id: String, userId: String) = WriteResult.Queued(id)
        val deleted = mutableListOf<String>()
        override suspend fun deleteItem(id: String) {
            if (offline) throw IOException("Unable to resolve host")
            deleted += id
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private val undoScope = TestScope()
    private val feedback = FeedbackCenter()

    // Never called on the refresh path; only the constructor needs a client.
    private val supabase = createSupabaseClient("https://example.supabase.co", "test-key") { install(Postgrest) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(id: String) = ShoppingItemRow(id = id, household_id = "h1", name = "Item $id",
        is_purchased = false, created_at = "c")

    private fun viewModel(repository: ShoppingRepository): ShoppingListViewModel {
        val household = CurrentHousehold(InMemoryHouseholdIdStore(), Dispatchers.Unconfined)
        household.set("h1")
        return ShoppingListViewModel(supabase, household, NoOpActivityLogger, repository, feedback, UndoQueue(feedback, undoScope, UndoQueue.UNDO_WINDOW_MS))
    }

    @Test
    fun offlineRefresh_keepsCachedItemsAndShowsError() {
        val repository = FakeShoppingRepository(offline = true)
        repository.items.value = listOf(item("i1"), item("i2"))

        val vm = viewModel(repository)

        val state = vm.state.value
        assertEquals(listOf("i1", "i2"), state.items.map { it.id })
        assertEquals(R.string.error_no_connection, state.error)
        assertFalse(state.loading)
    }

    @Test
    fun cacheUpdates_reachTheScreen() {
        val repository = FakeShoppingRepository(offline = false)
        val vm = viewModel(repository)
        repository.items.value = listOf(item("i3"))
        assertEquals(listOf("i3"), vm.state.value.items.map { it.id })
        assertNull(vm.state.value.error)
    }

    /** RM-0056: delete hides the item at once and sends nothing for 5 s. */
    @Test
    fun delete_hidesAtOnceAndDeletesAfterUndoWindow() {
        val repository = FakeShoppingRepository(offline = false)
        repository.items.value = listOf(item("i1"), item("i2"))
        val vm = viewModel(repository)

        vm.delete("i1")
        assertEquals(listOf("i2"), vm.state.value.visibleItems.map { it.id })
        assertTrue(repository.deleted.isEmpty())

        undoScope.advanceTimeBy(UndoQueue.UNDO_WINDOW_MS + 1)
        undoScope.runCurrent()
        assertEquals(listOf("i1"), repository.deleted)
        assertEquals(listOf("i2"), vm.state.value.visibleItems.map { it.id })
    }

    @Test
    fun undo_bringsTheItemBackAndNeverDeletes() {
        val repository = FakeShoppingRepository(offline = false)
        repository.items.value = listOf(item("i1"))
        val vm = viewModel(repository)

        vm.delete("i1")
        feedback.drain().single().undo!!.invoke()
        undoScope.advanceTimeBy(UndoQueue.UNDO_WINDOW_MS * 2)
        undoScope.runCurrent()

        assertEquals(listOf("i1"), vm.state.value.visibleItems.map { it.id })
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun offlineDelete_bringsTheItemBackWithRetry() {
        val repository = FakeShoppingRepository(offline = true)
        repository.items.value = listOf(item("i1"))
        val vm = viewModel(repository)
        feedback.drain()

        vm.delete("i1")
        feedback.drain()
        undoScope.advanceTimeBy(UndoQueue.UNDO_WINDOW_MS + 1)
        undoScope.runCurrent()

        assertEquals(listOf("i1"), vm.state.value.visibleItems.map { it.id })
        val message = feedback.drain().single()
        assertEquals(R.string.feedback_delete_item_failed, message.messageRes)
        assertNotNull(message.retry)
    }
}
