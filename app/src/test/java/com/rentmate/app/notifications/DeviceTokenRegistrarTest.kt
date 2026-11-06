package com.rentmate.app.notifications

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Tests for DeviceTokenRegistrar (RM-0021), with Firebase and Supabase faked. */
class DeviceTokenRegistrarTest {

    private class FakeSource(var token: String? = "tok-1", var fail: Boolean = false) : PushTokenSource {
        override suspend fun currentToken(): String? = if (fail) throw IOException("no Play services") else token
    }

    private class FakeStore(var signedIn: Boolean = true, var fail: Boolean = false) : DeviceTokenStore {
        val saved = mutableListOf<String>()
        val removed = mutableListOf<String>()
        override suspend fun register(token: String): Boolean {
            if (fail) throw IOException("offline")
            if (!signedIn) return false
            saved += token
            return true
        }
        override suspend fun unregister(token: String) {
            if (fail) throw IOException("offline")
            removed += token
        }
    }

    @Test fun `the current token is saved for the signed-in user`() = runTest {
        val store = FakeStore()
        assertTrue(DeviceTokenRegistrar(FakeSource(), store, this).syncCurrent())
        assertEquals(listOf("tok-1"), store.saved)
    }

    @Test fun `nothing is saved when signed out or without Firebase`() = runTest {
        val signedOut = FakeStore(signedIn = false)
        assertFalse(DeviceTokenRegistrar(FakeSource(), signedOut, this).syncCurrent())

        val store = FakeStore()
        assertFalse(DeviceTokenRegistrar(FakeSource(token = null), store, this).syncCurrent())
        assertFalse(DeviceTokenRegistrar(FakeSource(), store, this).register("  "))
        assertTrue(store.saved.isEmpty())
    }

    @Test fun `failures are swallowed so sign-in and sign-out still work`() = runTest {
        assertFalse(DeviceTokenRegistrar(FakeSource(fail = true), FakeStore(), this).syncCurrent())
        assertFalse(DeviceTokenRegistrar(FakeSource(), FakeStore(fail = true), this).syncCurrent())
        DeviceTokenRegistrar(FakeSource(), FakeStore(fail = true), this).unregisterCurrent()
        DeviceTokenRegistrar(FakeSource(fail = true), FakeStore(), this).unregisterCurrent()
    }

    @Test fun `sign-out removes this device's token`() = runTest {
        val store = FakeStore()
        DeviceTokenRegistrar(FakeSource(), store, this).unregisterCurrent()
        assertEquals(listOf("tok-1"), store.removed)
    }

    @Test fun `a new token from FCM is saved in the background`() {
        val scope = TestScope()
        val store = FakeStore()
        DeviceTokenRegistrar(FakeSource(), store, scope).registerInBackground("tok-2")
        scope.advanceUntilIdle()
        assertEquals(listOf("tok-2"), store.saved)
    }
}
