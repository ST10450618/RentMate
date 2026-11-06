package com.rentmate.app.ui.format

import com.rentmate.app.ui.components.formatRand
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** RM-0056 part 2: rand amounts look like `R 1 245,00` on every screen. */
class RandFormatTest {

    /** Writes the expected text with plain spaces; the formatter uses non-breaking ones. */
    private fun rand(text: String) = text.replace(' ', RandFormat.NBSP)

    @Test
    fun formatsThePlanExample() {
        assertEquals(rand("R 1 245,00"), RandFormat.format(1245.0))
    }

    @Test
    fun smallAmountsHaveNoGroupingSpace() {
        assertEquals(rand("R 0,00"), RandFormat.format(0.0))
        assertEquals(rand("R 899,00"), RandFormat.format(899.0))
        assertEquals(rand("R 12,50"), RandFormat.format(12.5))
    }

    @Test
    fun groupsEveryThreeDigits() {
        assertEquals(rand("R 9 600,00"), RandFormat.format(9600.0))
        assertEquals(rand("R 1 234 567,89"), RandFormat.format(1_234_567.89))
    }

    @Test
    fun roundsHalfUpToCents() {
        assertEquals(rand("R 0,01"), RandFormat.format(BigDecimal("0.005")))
        assertEquals(rand("R 333,33"), RandFormat.format(BigDecimal("333.334")))
    }

    @Test
    fun negativeAmountsPutTheMinusFirst() {
        assertEquals(rand("-R 300,00"), RandFormat.format(-300.0))
    }

    @Test
    fun spacesAreNonBreakingSoAmountsNeverWrap() {
        assertEquals(false, RandFormat.format(1245.0).contains(' '))
    }

    @Test
    fun formatRandUsesTheSharedFormatter() {
        assertEquals(RandFormat.format(1200.0), formatRand(1200.0))
        assertEquals(RandFormat.format(BigDecimal("1200")), formatRand(BigDecimal("1200")))
    }

    @Test
    fun shortFormDropsZeroCentsOnly() {
        assertEquals(rand("R 1 245"), RandFormat.formatShort(1245.0))
        assertEquals(rand("R 9 600"), RandFormat.formatShort(9600.0))
        assertEquals(rand("R 12,50"), RandFormat.formatShort(12.5))
        assertEquals(rand("R 0"), RandFormat.formatShort(0.0))
    }
}
