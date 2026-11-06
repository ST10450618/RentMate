package com.rentmate.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RM-0061: the input and button rules of the household setup steps. */
class HouseholdSetupRulesTest {

    @Test
    fun `a pasted code ignores case, spaces and dashes`() {
        assertEquals("K7Q2MX", cleanInviteCodeInput("k7q 2mx"))
        assertEquals("K7Q2MX", cleanInviteCodeInput(" K7Q-2MX\n"))
    }

    @Test
    fun `the code stops at six characters`() {
        assertEquals("ABCDEF", cleanInviteCodeInput("abcdefgh"))
    }

    @Test
    fun `the code drops characters that are not letters or digits`() {
        assertEquals("AB12", cleanInviteCodeInput("a!b@1#2é"))
    }

    @Test
    fun `join is enabled only for a full valid code and when idle`() {
        assertFalse(canJoin("K7Q2M", busy = false))
        assertTrue(canJoin("K7Q2MX", busy = false))
        assertFalse(canJoin("K7Q2MX", busy = true))
        // 0, O, 1 and I are not in the invite code alphabet.
        assertFalse(canJoin("K7Q2M0", busy = false))
    }

    @Test
    fun `create is enabled only for a valid name and when idle`() {
        assertFalse(canCreate("", busy = false))
        assertFalse(canCreate("A", busy = false))
        assertTrue(canCreate("Observatory house", busy = false))
        assertFalse(canCreate("Observatory house", busy = true))
    }

    @Test
    fun `first name is the first word of the full name`() {
        assertEquals("Nomsa", firstNameOf("  Nomsa Dlamini "))
        assertEquals("Thandi", firstNameOf("Thandi"))
    }

    @Test
    fun `no first name for an empty value or an email`() {
        assertNull(firstNameOf(null))
        assertNull(firstNameOf("   "))
        assertNull(firstNameOf("nomsa@example.com"))
    }
}
