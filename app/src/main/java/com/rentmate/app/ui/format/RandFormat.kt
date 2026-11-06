package com.rentmate.app.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * RM-0056 part 2: the one formatter for money in the app. Every screen and the
 * activity feed show rand as `R 1 245,00`, the South African format the
 * redesign plan asks for.
 *
 * It starts from [NumberFormat.getNumberInstance] for en-ZA, then pins the
 * symbols and the pattern. The reason: the en-ZA data differs between Android
 * versions and the JVM. Some give `R1 245,00`, some `R 1 245.00`. Pinning it
 * means a phone on Android 8 and a unit test on the laptop print the same text.
 *
 * Both spaces are non-breaking (U+00A0), so at a large font size an amount
 * never wraps between the R and the digits, or inside the number.
 */
object RandFormat {
    /** The non-breaking space used after R and between thousands. */
    const val NBSP = ' '

    /** en-ZA, the locale whose currency format this follows. */
    val locale: Locale = Locale("en", "ZA")

    private fun newFormat(): DecimalFormat {
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        // Only the plain separators are set. The monetary ones need API 34, and
        // the pattern below has no currency sign, so they are never used.
        val symbols = DecimalFormatSymbols(locale).apply {
            groupingSeparator = NBSP
            decimalSeparator = ','
            minusSign = '-'
        }
        format.decimalFormatSymbols = symbols
        // 'R' is a literal, so no currency data is read. The negative pattern puts the minus before R.
        format.applyPattern("'R'$NBSP#,##0.00;-'R'$NBSP#,##0.00")
        format.decimalFormatSymbols = symbols
        format.roundingMode = RoundingMode.HALF_UP
        return format
    }

    // DecimalFormat is not thread-safe, so each thread gets its own copy.
    private val format = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat = newFormat()
    }

    /** Formats [amount] as rand, for example 1245.0 -> `R 1 245,00`. */
    fun format(amount: Double): String = format.get()!!.format(amount)

    /** Formats [amount] as rand, rounding half up to cents. */
    fun format(amount: BigDecimal): String = format.get()!!.format(amount)
}
