package com.rentmate.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** RM-0056 part 7: 24 dp gutter and two columns from 600 dp. */
class ScreenLayoutTest {

    @Test
    fun phonesGetOneColumnAndA16dpGutter() {
        listOf(320, 360, 411, 599).forEach {
            assertEquals("$it dp", ScreenLayout(16.dp, 1), screenLayoutFor(it))
        }
    }

    @Test
    fun from600dpTabletsGetTwoColumnsAndA24dpGutter() {
        listOf(600, 800, 1280).forEach {
            assertEquals("$it dp", ScreenLayout(24.dp, 2), screenLayoutFor(it))
        }
    }

    @Test
    fun gutterMatchesTheSpacingTokens() {
        assertEquals(Spacing.lg, PhoneLayout.gutter)
        assertEquals(Spacing.xl, TabletLayout.gutter)
    }
}
