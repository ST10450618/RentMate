package com.rentmate.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens on a 4dp grid. Screens use these instead of literal dp values
 * so gutters, card padding and gaps between items stay identical everywhere.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal gutter for screen content. */
    val screen = 16.dp

    /** Inner padding of a [com.rentmate.app.ui.components.RentMateCard]. */
    val card = 16.dp
}

/**
 * Corner radii. Cards and inputs share one radius; buttons are pill-shaped so
 * they read as actions rather than containers.
 */
val RentMateShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
