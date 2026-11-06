package com.rentmate.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x contrast for the colours the app really uses (RM-0050). */
class ThemeContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        println("contrast $name = ${"%.2f".format(r)}")
        assertTrue("$name is ${"%.2f".format(r)}:1, needs 4.5:1", r >= 4.5)
    }

    @Test
    fun lightBodyTextPairsPass() {
        val s = RentMateLightColors
        check("light onBackground/background", s.onBackground, s.background)
        check("light onSurface/surface", s.onSurface, s.surface)
        check("light onSurface/surfaceVariant", s.onSurface, s.surfaceVariant)
        check("light onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
        check("light onSurfaceVariant/background", s.onSurfaceVariant, s.background)
        check("light onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
        check("light onPrimary/primary", s.onPrimary, s.primary)
        check("light primary/background", s.primary, s.background)
        check("light onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        check("light onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        check("light error/surface", s.error, s.surface)
        check("light onError/error", s.onError, s.error)
    }

    @Test
    fun darkBodyTextPairsPass() {
        val s = RentMateDarkColors
        check("dark onBackground/background", s.onBackground, s.background)
        check("dark onSurface/surface", s.onSurface, s.surface)
        check("dark onSurface/surfaceVariant", s.onSurface, s.surfaceVariant)
        check("dark onSurface/surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest)
        check("dark onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
        check("dark onSurfaceVariant/background", s.onSurfaceVariant, s.background)
        check("dark onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
        check("dark onPrimary/primary", s.onPrimary, s.primary)
        check("dark primary/background", s.primary, s.background)
        check("dark primary/surface", s.primary, s.surface)
        check("dark onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        check("dark onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        check("dark error/surface", s.error, s.surface)
        check("dark error/background", s.error, s.background)
    }

    @Test
    fun planColoursAreUsed() {
        assertEquals(Color(0xFFF7FBFA), RentMateLightColors.background)
        assertEquals(Color(0xFFF1F4F3), RentMateLightColors.surfaceVariant)
        assertEquals(Color(0xFFDCE3E1), RentMateLightColors.outline)
        assertEquals(Color(0xFF0C110F), RentMateDarkColors.background)
        assertEquals(Color(0xFF579487), RentMateDarkColors.primary)
    }

    @Test
    fun typeScaleHasNothingUnder12spAndBodyIs16() {
        val t = Typography
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall
        )
        assertTrue(styles.all { it.fontSize.value >= 12f })
        assertEquals(16f, t.bodyLarge.fontSize.value)
        assertEquals(7, styles.map { it.fontSize.value to it.fontWeight }.distinct().size)
    }
}
