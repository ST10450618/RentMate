package com.rentmate.app.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarColorsTest {

    private fun lum(c: Color): Double {
        fun ch(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    @Test
    fun sameIdAlwaysGivesSameColour() {
        val id = "3f2b6c1e-9a47-4d0e-8c55-1b7e2a9d4f10"
        val first = avatarColorsFor(id)
        repeat(100) { assertEquals(first, avatarColorsFor(id)) }
    }

    @Test
    fun indexIsAlwaysInRange() {
        val ids = (1..2000).map { "user-$it-${it * 7919}" } + listOf("", "é中", "a")
        assertTrue(ids.all { avatarIndex(it) in AvatarPalette.indices })
    }

    @Test
    fun differentIdsSpreadOverPalette() {
        val used = (1..200).map { avatarIndex("00000000-0000-4000-8000-%012d".format(it)) }.toSet()
        assertTrue("only ${used.size} of ${AvatarPalette.size} colours used", used.size >= AvatarPalette.size - 1)
    }

    @Test
    fun everyPaletteEntryHasReadableInitial() {
        AvatarPalette.forEach {
            val (hi, lo) = listOf(lum(it.background), lum(it.foreground)).sortedDescending()
            val ratio = (hi + 0.05) / (lo + 0.05)
            assertTrue("ratio $ratio", ratio >= 4.5)
        }
    }
}
