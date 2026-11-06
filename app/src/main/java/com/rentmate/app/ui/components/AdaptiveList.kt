package com.rentmate.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rentmate.app.ui.theme.LocalScreenLayout
import com.rentmate.app.ui.theme.Spacing

/**
 * RM-0056 part 7: the list used by every list screen. It shows one column of
 * cards on a phone and two side by side at 600 dp and wider, with a 12 dp gap
 * between cards (the plan's gap between cards).
 *
 * It is a [LazyVerticalGrid], so screens add rows with `items` as before. Use
 * [fullWidthItem] for headers, banners and other rows that must span both columns.
 */
@Composable
fun AdaptiveList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(LocalScreenLayout.current.listColumns),
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content
    )
}

/** Adds a row to an [AdaptiveList] that spans every column, such as a day header. */
fun LazyGridScope.fullWidthItem(key: Any? = null, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}
