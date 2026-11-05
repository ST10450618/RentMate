package com.rentmate.app.ui.validation

import androidx.annotation.StringRes
import com.rentmate.app.R
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Input rules for every RentMate form (RM-0036).
 *
 * Pure functions with no Android dependencies so they run in plain JUnit tests
 * and can be called from both Compose screens (inline messages) and ViewModels
 * (final guard before anything is sent to Supabase). Each rule returns null when
 * the input is valid, or a [FieldError] whose string resource the UI shows under
 * the field, so messages are translatable.
 */
enum class FieldError(@StringRes val messageRes: Int) {
    REQUIRED(R.string.error_required),
    TOO_SHORT(R.string.error_too_short),
    TOO_LONG(R.string.error_too_long),
    NOT_A_NUMBER(R.string.error_not_a_number),
    MUST_BE_POSITIVE(R.string.error_must_be_positive),
    AMOUNT_TOO_LARGE(R.string.error_amount_too_large),
    TOO_MANY_DECIMALS(R.string.error_too_many_decimals),
    POINTS_OUT_OF_RANGE(R.string.error_points_range),
    DATE_TOO_FAR_PAST(R.string.error_date_past),
    DATE_TOO_FAR_FUTURE(R.string.error_date_future),
    INVITE_CODE_FORMAT(R.string.error_invite_code)
}

object Limits {
    const val TITLE_MAX = 80
    const val HOUSEHOLD_NAME_MIN = 2
    const val HOUSEHOLD_NAME_MAX = 50
    const val ITEM_NAME_MAX = 60
    const val DESCRIPTION_MAX = 500
    const val POINTS_MIN = 1
    const val POINTS_MAX = 100
    const val INVITE_CODE_LENGTH = 6

    /** Largest bill accepted: R1,000,000. Anything bigger is almost certainly a typo. */
    val AMOUNT_MAX: BigDecimal = BigDecimal("1000000")

    /** A bill can be logged late, but not more than a year after it was due. */
    const val DUE_DATE_PAST_DAYS = 365L

    /** Bills are planned at most two years ahead. */
    const val DUE_DATE_FUTURE_DAYS = 730L
}

/** Invite codes use the alphabet from generate_invite_code (no 0/O, 1/I/L). */
private val INVITE_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toSet()

/** Required free text with an upper bound, e.g. a bill or chore title. */
fun validateRequiredText(value: String, max: Int = Limits.TITLE_MAX, min: Int = 1): FieldError? {
    val trimmed = value.trim()
    return when {
        trimmed.isEmpty() -> FieldError.REQUIRED
        trimmed.length < min -> FieldError.TOO_SHORT
        trimmed.length > max -> FieldError.TOO_LONG
        else -> null
    }
}

/** Optional free text such as a maintenance description: only the length is checked. */
fun validateOptionalText(value: String, max: Int = Limits.DESCRIPTION_MAX): FieldError? =
    if (value.trim().length > max) FieldError.TOO_LONG else null

/**
 * Parses a rand amount typed by the user. Accepts "1250", "1250.5", "1 250,50"
 * and "R1250"; spaces and a leading R are ignored and a comma is read as the
 * decimal separator (South African convention). Returns null when it can't parse.
 */
fun parseAmount(raw: String): BigDecimal? {
    val cleaned = raw.trim()
        .removePrefix("R").removePrefix("r")
        .replace(" ", "").replace(" ", "")
        .replace(',', '.')
    if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
    if (!cleaned.all { it.isDigit() || it == '.' || it == '-' }) return null
    return cleaned.toBigDecimalOrNull()
}

/** A money amount: present, numeric, above zero, at most 2 decimals and at most [Limits.AMOUNT_MAX]. */
fun validateAmount(raw: String): FieldError? {
    if (raw.isBlank()) return FieldError.REQUIRED
    val amount = parseAmount(raw) ?: return FieldError.NOT_A_NUMBER
    return when {
        amount.signum() <= 0 -> FieldError.MUST_BE_POSITIVE
        amount > Limits.AMOUNT_MAX -> FieldError.AMOUNT_TOO_LARGE
        amount.stripTrailingZeros().scale() > 2 -> FieldError.TOO_MANY_DECIMALS
        else -> null
    }
}

/** Chore points: a whole number from [Limits.POINTS_MIN] to [Limits.POINTS_MAX]. */
fun validatePoints(raw: String): FieldError? {
    if (raw.isBlank()) return FieldError.REQUIRED
    val points = raw.trim().toIntOrNull() ?: return FieldError.NOT_A_NUMBER
    return if (points in Limits.POINTS_MIN..Limits.POINTS_MAX) null else FieldError.POINTS_OUT_OF_RANGE
}

/** A bill due date relative to [today]: no more than a year overdue, no more than two years out. */
fun validateDueDate(date: LocalDate?, today: LocalDate = LocalDate.now()): FieldError? = when {
    date == null -> FieldError.REQUIRED
    date.isBefore(today.minusDays(Limits.DUE_DATE_PAST_DAYS)) -> FieldError.DATE_TOO_FAR_PAST
    date.isAfter(today.plusDays(Limits.DUE_DATE_FUTURE_DAYS)) -> FieldError.DATE_TOO_FAR_FUTURE
    else -> null
}

/** Six characters from the invite-code alphabet. Lower case is accepted and upper-cased. */
fun validateInviteCode(raw: String): FieldError? {
    val code = normaliseInviteCode(raw)
    return when {
        code.isEmpty() -> FieldError.REQUIRED
        code.length != Limits.INVITE_CODE_LENGTH || !code.all { it in INVITE_CODE_ALPHABET } -> FieldError.INVITE_CODE_FORMAT
        else -> null
    }
}

fun normaliseInviteCode(raw: String): String = raw.trim().uppercase().replace(" ", "")
