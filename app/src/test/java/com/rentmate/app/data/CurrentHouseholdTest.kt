package com.rentmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [HouseholdIdStore] kept in memory, for tests. */
class InMemoryHouseholdIdStore(var saved: String? = null) : HouseholdIdStore {
    override suspend fun read(): String? = saved
    override suspend fun write(id: String?) {
        saved = id
    }
}

/** RM-0016: the selected household survives a restart and is removed on sign-out. */
class CurrentHouseholdTest {

    @Test
    fun restore_loadsTheSavedId() = runBlocking {
        val household = CurrentHousehold(InMemoryHouseholdIdStore("h1"), Dispatchers.Unconfined)
        household.awaitRestored()
        assertEquals("h1", household.id.value)
    }

    @Test
    fun set_savesTheIdForTheNextLaunch() = runBlocking {
        val store = InMemoryHouseholdIdStore()
        CurrentHousehold(store, Dispatchers.Unconfined).set("h2")
        assertEquals("h2", store.saved)

        val nextLaunch = CurrentHousehold(store, Dispatchers.Unconfined)
        nextLaunch.awaitRestored()
        assertEquals("h2", nextLaunch.id.value)
    }

    @Test
    fun forget_removesTheSavedId() = runBlocking {
        val store = InMemoryHouseholdIdStore("h1")
        val household = CurrentHousehold(store, Dispatchers.Unconfined)
        household.awaitRestored()
        household.forget()
        assertNull(household.id.value)
        assertNull(store.saved)
    }

    @Test
    fun failingStore_leavesNoHouseholdAndDoesNotThrow() = runBlocking {
        val broken = object : HouseholdIdStore {
            override suspend fun read(): String? = throw IllegalStateException("disk")
            override suspend fun write(id: String?) = throw IllegalStateException("disk")
        }
        val household = CurrentHousehold(broken, Dispatchers.Unconfined)
        household.awaitRestored()
        assertNull(household.id.value)
        household.set("h1")
        assertEquals("h1", household.id.value)
    }
}
