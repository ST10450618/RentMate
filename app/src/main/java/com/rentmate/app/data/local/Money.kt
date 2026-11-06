package com.rentmate.app.data.local

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts a Rand amount from Supabase (numeric(12,2), decoded as Double) to
 * whole cents. Goes through the shortest decimal text of the Double, so
 * 123.45 becomes 12345 and never 12344 from binary rounding.
 */
fun Double.toCents(): Long =
    BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

/** Converts whole cents back to a Rand amount for the UI row types. */
fun Long.centsToRand(): Double = BigDecimal.valueOf(this, 2).toDouble()
