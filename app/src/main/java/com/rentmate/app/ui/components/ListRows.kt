package com.rentmate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.theme.tabular

/*
 * RM-0056: grouped lists, the main pattern of the redesign mockups.
 *
 * A list screen shows its items as rows inside one rounded surface, with a
 * hairline between rows, instead of one card per item. Use ListGroup for any
 * list of similar things: bills, settle-up payments, this week's events,
 * chores, shopping items. Keep RentMateCard for a single block that is not a
 * list, such as the "You owe" card on Home.
 */

/**
 * One rounded surface (radius 14, 1 dp Line border) that holds rows.
 *
 * Put [ListRow]s inside and separate them with [ListDivider], or use the
 * overload that takes a list and adds the dividers for you.
 */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.border, MaterialTheme.colorScheme.outline)
    ) {
        Column(content = content)
    }
}

/**
 * A [ListGroup] with one row per item in [items] and a hairline between rows.
 *
 * @param row draws one item, normally a [ListRow].
 */
@Composable
fun <T> ListGroup(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    ListGroup(modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) ListDivider()
            row(item)
        }
    }
}

/** The hairline between two rows of a [ListGroup]. */
@Composable
fun ListDivider() {
    HorizontalDivider(thickness = Sizes.border, color = MaterialTheme.colorScheme.outline)
}

/**
 * One row of a [ListGroup]: optional leading avatar, a title, an optional muted
 * second line, and an optional trailing amount or pill.
 *
 * The row is at least 48 dp tall. When [onClick] is set the whole row is the
 * touch target, and TalkBack reads it as one button. Otherwise TalkBack still
 * reads the row as one item, so the title and amount are never split.
 *
 * @param leading usually a [MemberAvatar], so each housemate keeps one colour.
 * @param supporting the muted second line, for example "Monthly · split equally · 4".
 * @param trailing usually [ListAmount] or a [StatusPill], right-aligned.
 * @param emphasised true draws the title in SemiBold, for rows whose title is
 *   a name, such as a bill.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    emphasised: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interaction = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .then(interaction)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasised) FontWeight.SemiBold else null
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A bold, right-aligned amount for the trailing slot of a [ListRow]. It uses
 * tabular figures so amounts in a column line up. Pass text from
 * [formatRandShort] or [formatRand].
 */
@Composable
fun ListAmount(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.tabular(),
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * A small heading above a [ListGroup], such as "This week" or "Settle up".
 * It is a TalkBack heading.
 */
@Composable
fun ListHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = Spacing.xs).semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}
