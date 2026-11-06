package com.rentmate.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.rentmate.app.ui.theme.Sizes

/**
 * RM-0056: a row of pill chips where exactly one is selected.
 *
 * Use it for list filters (Open, Paid, Repeating on Money) and for one-of-few
 * choices in forms (Split: Equally, By %, Exact amounts; Repeats: Never,
 * Weekly, Monthly). The selected chip has the aloe-soft fill, an aloe border
 * and aloe SemiBold text, like the mockups. The row wraps at large font sizes
 * (see [ChipRow]). Each chip is at least 48 dp tall for touch, and TalkBack
 * reads it with its selected state.
 *
 * @param options the choices, in display order.
 * @param label the text for one option, already translated.
 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    ChipRow(modifier = modifier) {
        options.forEach { option ->
            RentMateChip(text = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

/**
 * One pill chip in the RentMate style. Prefer [ChoiceChips]; use this alone
 * only for a chip that toggles on and off by itself.
 */
@Composable
fun RentMateChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else null
            )
        },
        shape = CircleShape,
        modifier = modifier.heightIn(min = Sizes.minTouch),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surface,
            labelColor = scheme.onSurface,
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = scheme.outline,
            selectedBorderColor = scheme.primary,
            borderWidth = Sizes.border,
            selectedBorderWidth = Sizes.border
        )
    )
}
