package com.rentmate.app.ui.theme

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TAG = "ScreenLayout"

/**
 * RM-0056 part 7: the layout values that change with the window width.
 *
 * @property gutter horizontal padding between the screen edge and its content.
 * @property listColumns how many cards a list screen shows side by side.
 */
data class ScreenLayout(val gutter: Dp, val listColumns: Int)

/** The tablet layout starts at this width. Material 3 calls 600 dp and wider the "medium" window class. */
const val TABLET_MIN_WIDTH_DP = 600

/** Phone layout: 16 dp gutter, one column. */
val PhoneLayout = ScreenLayout(gutter = 16.dp, listColumns = 1)

/** Tablet layout, 600 dp and wider: 24 dp gutter, two columns. */
val TabletLayout = ScreenLayout(gutter = 24.dp, listColumns = 2)

/**
 * Picks the layout for a window [widthDp] wide. This is a pure function, so
 * unit tests cover the breakpoint without a device.
 */
fun screenLayoutFor(widthDp: Int): ScreenLayout =
    if (widthDp >= TABLET_MIN_WIDTH_DP) TabletLayout else PhoneLayout

/** The layout for the current window. [RentMateTheme] provides it. */
val LocalScreenLayout = staticCompositionLocalOf { PhoneLayout }

/**
 * Provides [LocalScreenLayout] from the current window width. The value
 * changes when the window is resized or rotated, for example in split screen.
 */
@Composable
fun ProvideScreenLayout(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val layout = remember(widthDp) {
        screenLayoutFor(widthDp).also { Log.d(TAG, "width $widthDp dp -> $it") }
    }
    CompositionLocalProvider(LocalScreenLayout provides layout, content = content)
}
