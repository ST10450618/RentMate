package com.rentmate.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/** RM-0016: Rand to cents and back must never lose a cent. */
class MoneyTest {

    @Test
    fun toCents_isExactForTwoDecimalAmounts() {
        assertEquals(12345L, 123.45.toCents())
        assertEquals(10L, 0.1.toCents())
        assertEquals(29L, 0.29.toCents())
        assertEquals(100_000_000L, 1_000_000.0.toCents())
        assertEquals(0L, 0.0.toCents())
    }

    @Test
    fun toCents_roundsHalfUp() {
        assertEquals(3334L, 33.335.toCents())
    }

    @Test
    fun everyCentValueRoundTrips() {
        for (cents in 0L..100_000L) {
            assertEquals(cents, cents.centsToRand().toCents())
        }
    }

    @Test
    fun centsToRand_givesTheUiAmount() {
        assertEquals(123.45, 12345L.centsToRand(), 0.0)
    }
}
