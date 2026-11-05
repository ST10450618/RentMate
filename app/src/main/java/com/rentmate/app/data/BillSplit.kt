package com.rentmate.app.data

import java.math.BigDecimal
import java.math.RoundingMode

/** The three ways a bill can be divided. [wire] is the value stored in bills.split_method. */
enum class SplitMode(val wire: String) {
    Equal("Equal"),
    Percentage("Percentage"),
    ExactAmount("ExactAmount")
}

/** One member's share in cents, plus the raw value the server's create_bill expects. */
data class SplitShare(val userId: String, val cents: Long, val value: BigDecimal) {
    val amount: BigDecimal get() = BigDecimal.valueOf(cents, 2)
}

sealed interface SplitError {
    data object InvalidAmount : SplitError
    data object NoMembers : SplitError
    /** A member's field is not a number. */
    data object NotANumber : SplitError
    data object Negative : SplitError
    /** Percentages must total 100; [total] is what they actually add up to. */
    data class PercentTotal(val total: BigDecimal) : SplitError
    /** Exact amounts must total the bill; [total] is what they add up to (rand). */
    data class ExactTotal(val total: BigDecimal) : SplitError
    data object TooLarge : SplitError
}

sealed interface SplitResult {
    data class Valid(val shares: List<SplitShare>) : SplitResult
    data class Invalid(val error: SplitError) : SplitResult
}

/**
 * Pure split maths, mirroring the create_bill SQL function in migration 0008 so
 * the form can reject bad splits before they are sent. Everything is done in
 * whole cents; for Equal and Percentage the last share absorbs the rounding
 * remainder (the same rule as the API's C# version).
 */
object BillSplit {
    private val HUNDRED = BigDecimal(100)
    private val PERCENT_TOLERANCE = BigDecimal("0.01")

    /**
     * @param amount bill total in rand, null if the field is empty or not a number
     * @param memberIds everyone in the split, in display order
     * @param inputs the text typed per member (percent or rand); ignored for Equal, blank counts as 0
     */
    fun compute(
        mode: SplitMode,
        amount: BigDecimal?,
        memberIds: List<String>,
        inputs: Map<String, String>
    ): SplitResult {
        if (amount == null || amount.signum() <= 0) return SplitResult.Invalid(SplitError.InvalidAmount)
        val totalCents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        if (totalCents.signum() <= 0) return SplitResult.Invalid(SplitError.InvalidAmount)
        if (totalCents > BigDecimal(99_999_999L)) return SplitResult.Invalid(SplitError.TooLarge)
        if (memberIds.isEmpty()) return SplitResult.Invalid(SplitError.NoMembers)
        val total = totalCents.toLong()

        val values: List<BigDecimal> = if (mode == SplitMode.Equal) {
            memberIds.map { BigDecimal.ZERO }
        } else {
            memberIds.map { id ->
                val text = inputs[id].orEmpty().trim().replace(',', '.')
                if (text.isEmpty()) BigDecimal.ZERO
                else text.toBigDecimalOrNull() ?: return SplitResult.Invalid(SplitError.NotANumber)
            }
        }
        if (values.any { it.signum() < 0 }) return SplitResult.Invalid(SplitError.Negative)

        val cents = LongArray(memberIds.size)
        when (mode) {
            SplitMode.Equal -> {
                val each = BigDecimal(total).divide(BigDecimal(memberIds.size), 0, RoundingMode.HALF_UP).toLong()
                cents.fill(each)
            }
            SplitMode.Percentage -> {
                val sum = values.fold(BigDecimal.ZERO, BigDecimal::add)
                if (sum.subtract(HUNDRED).abs() > PERCENT_TOLERANCE) {
                    return SplitResult.Invalid(SplitError.PercentTotal(sum))
                }
                values.forEachIndexed { i, pct ->
                    cents[i] = BigDecimal(total).multiply(pct).divide(HUNDRED, 0, RoundingMode.HALF_UP).toLong()
                }
            }
            SplitMode.ExactAmount -> {
                values.forEachIndexed { i, v -> cents[i] = v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong() }
                val sum = cents.sum()
                if (sum != total) {
                    return SplitResult.Invalid(SplitError.ExactTotal(BigDecimal.valueOf(sum, 2)))
                }
            }
        }

        if (mode != SplitMode.ExactAmount) {
            val last = memberIds.lastIndex
            cents[last] = total - cents.take(last).sum()
            if (cents[last] < 0) return SplitResult.Invalid(SplitError.InvalidAmount)
        }

        return SplitResult.Valid(memberIds.mapIndexed { i, id -> SplitShare(id, cents[i], values[i]) })
    }
}
