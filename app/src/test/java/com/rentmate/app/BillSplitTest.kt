package com.rentmate.app

import com.rentmate.app.data.BillSplit
import com.rentmate.app.data.SplitError
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.SplitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** RM-0010: the client-side split rules must match create_bill in migration 0008. */
class BillSplitTest {
    private val ids = listOf("a", "b", "c")

    private fun cents(r: SplitResult) = (r as SplitResult.Valid).shares.map { it.cents }
    private fun error(r: SplitResult) = (r as SplitResult.Invalid).error

    @Test
    fun equalSplitLastShareAbsorbsRemainder() {
        val r = BillSplit.compute(SplitMode.Equal, BigDecimal("100"), ids, emptyMap())
        assertEquals(listOf(3333L, 3333L, 3334L), cents(r))
    }

    @Test
    fun equalSplitDividesEvenly() {
        val r = BillSplit.compute(SplitMode.Equal, BigDecimal("300.00"), ids, emptyMap())
        assertEquals(listOf(10000L, 10000L, 10000L), cents(r))
    }

    @Test
    fun percentageSplitComputesShares() {
        val r = BillSplit.compute(SplitMode.Percentage, BigDecimal("200"), ids, mapOf("a" to "50", "b" to "30", "c" to "20"))
        assertEquals(listOf(10000L, 6000L, 4000L), cents(r))
    }

    @Test
    fun percentageWithinToleranceStillSumsToBill() {
        val r = BillSplit.compute(SplitMode.Percentage, BigDecimal("100"), ids, mapOf("a" to "33.33", "b" to "33.33", "c" to "33.33"))
        assertEquals(10000L, cents(r).sum())
    }

    @Test
    fun percentageNotHundredIsRejected() {
        val r = BillSplit.compute(SplitMode.Percentage, BigDecimal("200"), ids, mapOf("a" to "50", "b" to "30", "c" to "10"))
        assertTrue(error(r) is SplitError.PercentTotal)
    }

    @Test
    fun percentageOverHundredIsRejected() {
        val r = BillSplit.compute(SplitMode.Percentage, BigDecimal("200"), listOf("a", "b"), mapOf("a" to "60", "b" to "50"))
        assertTrue(error(r) is SplitError.PercentTotal)
    }

    @Test
    fun exactSplitMustAddUpToBill() {
        val ok = BillSplit.compute(SplitMode.ExactAmount, BigDecimal("200"), ids, mapOf("a" to "100", "b" to "60.50", "c" to "39.50"))
        assertEquals(listOf(10000L, 6050L, 3950L), cents(ok))

        val short = BillSplit.compute(SplitMode.ExactAmount, BigDecimal("200"), ids, mapOf("a" to "100", "b" to "60", "c" to "30"))
        assertEquals(SplitError.ExactTotal(BigDecimal("190.00")), error(short))
    }

    @Test
    fun blankFieldsCountAsZeroAndCommaIsADecimalSeparator() {
        val r = BillSplit.compute(SplitMode.ExactAmount, BigDecimal("150"), ids, mapOf("a" to "100,50", "b" to "49.50"))
        assertEquals(listOf(10050L, 4950L, 0L), cents(r))
    }

    @Test
    fun badInputIsRejected() {
        assertEquals(SplitError.NotANumber, error(BillSplit.compute(SplitMode.ExactAmount, BigDecimal("10"), ids, mapOf("a" to "abc"))))
        assertEquals(SplitError.Negative, error(BillSplit.compute(SplitMode.ExactAmount, BigDecimal("10"), listOf("a", "b"), mapOf("a" to "-5", "b" to "15"))))
        assertEquals(SplitError.InvalidAmount, error(BillSplit.compute(SplitMode.Equal, null, ids, emptyMap())))
        assertEquals(SplitError.InvalidAmount, error(BillSplit.compute(SplitMode.Equal, BigDecimal.ZERO, ids, emptyMap())))
        assertEquals(SplitError.NoMembers, error(BillSplit.compute(SplitMode.Equal, BigDecimal.TEN, emptyList(), emptyMap())))
    }

    @Test
    fun totalAtTheAmountLimitSplits() {
        val r = BillSplit.compute(SplitMode.Equal, BigDecimal("999999.99"), ids, emptyMap())
        assertEquals(99_999_999L, cents(r).sum())
    }

    @Test
    fun totalAboveTheAmountLimitIsTooLarge() {
        assertEquals(SplitError.TooLarge, error(BillSplit.compute(SplitMode.Equal, BigDecimal("1000000"), ids, emptyMap())))
    }
}
