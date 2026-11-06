package com.rentmate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.rentmate.app.R
import com.rentmate.app.ui.theme.Aloe
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

/*
 * Shared building blocks for every RentMate screen (RM-0034).
 *
 * Screens compose these instead of styling Material components directly, so a
 * change to card borders, button shape or label style happens in one place and
 * the whole app stays visually consistent.
 */

/**
 * The standard content card: white surface, hairline border, 16dp padding.
 *
 * @param accent border colour; pass the secondary (amber) colour for cards that
 *   need a decision from the user, e.g. an overdue bill.
 * @param contentPadding inner padding; list-style cards (Settings rows) pass a
 *   smaller value because each row pads itself.
 */
@Composable
fun RentMateCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.outline,
    contentPadding: PaddingValues = PaddingValues(Spacing.card),
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Sizes.border, accent),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = content
        )
    }
}

/** Short caption in sentence case that introduces a card or a group of rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier.semantics { heading() }
    )
}

/** Filled pill button for the main action on a screen or card. Shows a spinner while [loading]. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = Sizes.minTouch),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.spinner),
                strokeWidth = Sizes.stroke,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Outlined pill button for secondary actions that sit next to a [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Sizes.minTouch),
        shape = CircleShape,
        border = BorderStroke(Sizes.border, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Visual weight of a [StatusPill]. */
enum class PillTone { Positive, Attention, Danger, Neutral }

/** Compact rounded label for a status such as "Paid", "Overdue" or "Sent". */
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        PillTone.Positive -> scheme.primaryContainer to scheme.onPrimaryContainer
        PillTone.Attention -> scheme.secondaryContainer to scheme.onSecondaryContainer
        PillTone.Danger -> scheme.error.copy(alpha = 0.12f) to scheme.error
        PillTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .background(bg, CircleShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

/**
 * Circle with a housemate's initial, used wherever a person is shown.
 *
 * @param userId stable id that picks the colour, so one housemate keeps one colour
 *   on every screen. Without it the colour comes from [name].
 * @param size [Sizes.avatarRow] in rows, [Sizes.avatarHeader] in headers.
 */
@Composable
fun MemberAvatar(name: String, modifier: Modifier = Modifier, size: Dp = Sizes.avatarRow, userId: String? = null) {
    val colors = avatarColorsFor(userId ?: name)
    Box(
        modifier = modifier
            .size(size)
            .background(colors.background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.labelLarge,
            color = colors.foreground
        )
    }
}

/** The RentMate house mark on an aloe disc, the same artwork as the launcher icon. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = Sizes.brandMark) {
    Box(
        modifier = modifier
            .size(size)
            .background(Aloe, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(size * 1.35f)
        )
    }
}

private val zarFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

/**
 * Formats an amount as South African rand, e.g. `R1 250,00`, so money looks the
 * same on every screen instead of each one using its own `"R%.2f"` template.
 */
fun formatRand(amount: Double): String = zarFormat.format(amount)
