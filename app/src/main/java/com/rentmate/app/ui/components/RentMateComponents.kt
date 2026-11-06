package com.rentmate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.rentmate.app.R
import com.rentmate.app.ui.theme.Aloe
import com.rentmate.app.ui.theme.Ink
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.format.RandFormat
import java.math.BigDecimal

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

/**
 * The one large filled pill button for the main action on a screen, such as
 * "Pay Sipho" on Home or "Save bill" on the bill form. Use one per screen;
 * other actions use [SecondaryButton] or a text button.
 *
 * RM-0056: it is 52 dp tall with SemiBold text, and pass
 * Modifier.fillMaxWidth() for the mockups' full-width look. When disabled it
 * stays aloe but faded, so a form shows where Save is before it is valid.
 * Shows a spinner while [loading].
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = Sizes.primaryButton),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = scheme.primary.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = scheme.onPrimary.copy(alpha = DISABLED_CONTENT_ALPHA)
        ),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.spinner),
                strokeWidth = Sizes.stroke,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** How faded a disabled [PrimaryButton] is, matching the mockup (opacity 0.45). */
private const val DISABLED_ALPHA = 0.45f
private const val DISABLED_CONTENT_ALPHA = 0.8f

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

/**
 * White pill button for the main action on the aloe gradient (Login and Lock).
 *
 * [PrimaryButton] is filled with the brand colour, so it disappears on the
 * gradient. This button uses a white container with ink text instead, and keeps
 * the same pill shape and touch target.
 *
 * @param text label shown when the button is not loading.
 * @param onClick called when the user taps the button.
 * @param leadingIcon optional content drawn before [text], for example a
 *   fingerprint icon or a sign-in badge. It is hidden while [loading].
 * @param enabled false greys the container and ignores taps.
 * @param loading true shows an aloe spinner in place of [leadingIcon] and
 *   ignores taps.
 * @param loadingText label shown next to the spinner while [loading]. When null,
 *   the button shows only the spinner.
 */
@Composable
fun OnBrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = Sizes.onBrandButton),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Ink,
            disabledContainerColor = Color.White.copy(alpha = 0.7f),
            disabledContentColor = Ink
        ),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.spinner),
                strokeWidth = Sizes.stroke,
                color = Aloe
            )
            if (loadingText != null) {
                Spacer(Modifier.size(Spacing.md))
                Text(loadingText, style = MaterialTheme.typography.titleSmall)
            }
        } else {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.size(Spacing.md))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * A row of chips or pills that wraps onto the next line when it runs out of
 * room. RM-0056: at the largest font size three chips no longer fit on a
 * phone, and a plain Row clips the last one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.sm,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) { content() }
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
    // The initial is decoration: the name always sits next to the avatar, so
    // TalkBack skips it instead of reading a lone letter.
    Box(
        modifier = modifier
            .size(size)
            .background(colors.background, CircleShape)
            .clearAndSetSemantics { },
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

/**
 * Formats an amount as South African rand, for example `R 1 250,00`.
 * Every screen uses it, so money looks the same everywhere. RM-0056: it calls
 * [RandFormat], the one shared en-ZA formatter.
 */
fun formatRand(amount: Double): String = RandFormat.format(amount)

/** Short rand for list rows: `R 1 245`, or `R 12,50` when there are cents. See [RandFormat.formatShort]. */
fun formatRandShort(amount: Double): String = RandFormat.formatShort(amount)

/** [formatRand] for exact amounts, such as the split totals on Add Bill. */
fun formatRand(amount: BigDecimal): String = RandFormat.format(amount)
