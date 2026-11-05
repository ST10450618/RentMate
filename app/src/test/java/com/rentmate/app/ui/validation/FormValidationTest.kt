package com.rentmate.app.ui.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.random.Random

/** Unit tests for the form rules in FormValidation.kt (RM-0036). */
class FormValidationTest {

    // --- required text ---

    @Test fun `blank and whitespace text is required`() {
        assertEquals(FieldError.REQUIRED, validateRequiredText(""))
        assertEquals(FieldError.REQUIRED, validateRequiredText("   \n\t"))
    }

    @Test fun `text over the limit is too long, trimmed before counting`() {
        assertNull(validateRequiredText("  " + "a".repeat(80) + "  ", max = 80))
        assertEquals(FieldError.TOO_LONG, validateRequiredText("a".repeat(81), max = 80))
    }

    @Test fun `household name has a minimum length`() {
        assertEquals(FieldError.TOO_SHORT, validateRequiredText("A", Limits.HOUSEHOLD_NAME_MAX, Limits.HOUSEHOLD_NAME_MIN))
        assertNull(validateRequiredText("Flat 4", Limits.HOUSEHOLD_NAME_MAX, Limits.HOUSEHOLD_NAME_MIN))
    }

    @Test fun `optional text only checks length`() {
        assertNull(validateOptionalText(""))
        assertEquals(FieldError.TOO_LONG, validateOptionalText("x".repeat(Limits.DESCRIPTION_MAX + 1)))
    }

    // --- amounts ---

    @Test fun `amounts in South African formats parse`() {
        assertEquals(BigDecimal("1250"), parseAmount("1250"))
        assertEquals(BigDecimal("1250.50"), parseAmount("1 250,50"))
        assertEquals(BigDecimal("99.9"), parseAmount("R99.9"))
        assertEquals(BigDecimal("12"), parseAmount(" r12 "))
    }

    @Test fun `malformed amounts do not parse`() {
        listOf("", "abc", "1.2.3", "12a", "R", "1,2,3", "--5", "1e5").forEach {
            assertNull("expected null for '$it'", parseAmount(it))
        }
    }

    @Test fun `amount rules`() {
        assertEquals(FieldError.REQUIRED, validateAmount(""))
        assertEquals(FieldError.NOT_A_NUMBER, validateAmount("twelve"))
        assertEquals(FieldError.MUST_BE_POSITIVE, validateAmount("0"))
        assertEquals(FieldError.MUST_BE_POSITIVE, validateAmount("-50"))
        assertEquals(FieldError.AMOUNT_TOO_LARGE, validateAmount("1000000.01"))
        assertEquals(FieldError.TOO_MANY_DECIMALS, validateAmount("10.999"))
        assertNull(validateAmount("1000000"))
        assertNull(validateAmount("0.01"))
        assertNull(validateAmount("10.50"))
        assertNull(validateAmount("10.500"))
    }

    // --- points ---

    @Test fun `points must be a whole number from 1 to 100`() {
        assertEquals(FieldError.REQUIRED, validatePoints(""))
        assertEquals(FieldError.NOT_A_NUMBER, validatePoints("1.5"))
        assertEquals(FieldError.NOT_A_NUMBER, validatePoints("99999999999"))
        assertEquals(FieldError.POINTS_OUT_OF_RANGE, validatePoints("0"))
        assertEquals(FieldError.POINTS_OUT_OF_RANGE, validatePoints("101"))
        assertNull(validatePoints("1"))
        assertNull(validatePoints("100"))
    }

    // --- dates ---

    @Test fun `due date window is one year back to two years ahead`() {
        val today = LocalDate.of(2026, 11, 5)
        assertEquals(FieldError.REQUIRED, validateDueDate(null, today))
        assertNull(validateDueDate(today, today))
        assertNull(validateDueDate(today.minusDays(365), today))
        assertEquals(FieldError.DATE_TOO_FAR_PAST, validateDueDate(today.minusDays(366), today))
        assertNull(validateDueDate(today.plusDays(730), today))
        assertEquals(FieldError.DATE_TOO_FAR_FUTURE, validateDueDate(today.plusDays(731), today))
    }

    // --- invite codes ---

    @Test fun `invite codes are six characters from the unambiguous alphabet`() {
        assertNull(validateInviteCode("K7M3QX"))
        assertNull(validateInviteCode(" k7m 3qx "))
        assertEquals(FieldError.REQUIRED, validateInviteCode(""))
        assertEquals(FieldError.INVITE_CODE_FORMAT, validateInviteCode("K7M3Q"))
        assertEquals(FieldError.INVITE_CODE_FORMAT, validateInviteCode("K7M3QXZ"))
        assertEquals(FieldError.INVITE_CODE_FORMAT, validateInviteCode("K0M3QX")) // 0 is excluded
        assertEquals(FieldError.INVITE_CODE_FORMAT, validateInviteCode("K7M3Q!"))
        assertEquals("K7M3QX", normaliseInviteCode(" k7m 3qx "))
    }

    // --- fuzz: no validator may throw, whatever is typed or pasted ---

    @Test fun `validators never throw on random input`() {
        val random = Random(7314)
        val pool = "0123456789.,-+eE R r abcXYZ!@#\$%^&*()_=[]{};:'\"<>/?\\|`~\n\t\u0000😀"
        repeat(5_000) {
            val input = buildString { repeat(random.nextInt(0, 40)) { append(pool[random.nextInt(pool.length)]) } }
            validateRequiredText(input)
            validateOptionalText(input)
            validateAmount(input)
            parseAmount(input)
            validatePoints(input)
            validateInviteCode(input)
        }
    }
}
