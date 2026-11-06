package com.rentmate.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens on a 4dp grid. Screens use these instead of literal dp values
 * so gutters, card padding and gaps between items stay identical everywhere.
 */
object Spacing {
    /** No gap, for layouts whose children set their own spacing. */
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /**
     * Horizontal gutter for screen content: 16 dp on a phone, 24 dp at 600 dp
     * and wider (RM-0056). It reads [LocalScreenLayout], so it is only
     * available inside a composable.
     */
    val screen: Dp
        @Composable @ReadOnlyComposable
        get() = LocalScreenLayout.current.gutter

    /** Inner padding of a [com.rentmate.app.ui.components.RentMateCard]. */
    val card = 16.dp
}

/** Fixed sizes that are not spacing: touch targets, borders, avatars, thumbnails. */
object Sizes {
    /** Minimum touch target (WCAG 2.5.8, Android accessibility). */
    val minTouch = 48.dp

    /** Minimum height of one tab in [com.rentmate.app.ui.components.TextTabBar]. */
    val tabBar = 56.dp

    /** Height of [com.rentmate.app.ui.components.PrimaryButton], the one main action on a screen. */
    val primaryButton = 52.dp

    /** Height of [com.rentmate.app.ui.components.OnBrandButton], the main action on the aloe gradient. */
    val onBrandButton = 56.dp
    val border = 1.dp
    val stroke = 2.dp
    val spinner = 18.dp

    /** Avatar in a list row. */
    val avatarRow = 24.dp

    /** Avatar in a screen header. */
    val avatarHeader = 40.dp
    val thumbnail = 56.dp
    val thumbnailLarge = 72.dp
    val photoPreviewMax = 560.dp
    val brandMark = 96.dp
}

/**
 * Corner radii: 6 chips, 10 inputs, 14 cards, 18 sheets and dialogs. Buttons
 * are full pills through CircleShape, not through this scale.
 */
val RentMateShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    // AlertDialog and DatePickerDialog take their corners from extraLarge, so it
    // is the 18 dp sheet radius. Buttons use CircleShape directly for the pill.
    extraLarge = RoundedCornerShape(18.dp)
)
