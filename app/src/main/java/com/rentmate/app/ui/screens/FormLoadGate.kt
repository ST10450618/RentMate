package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rentmate.app.R
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.LoadingState

/**
 * RM-0056: draws the shared state view for [state], or [content] once the form
 * is [FormLoadState.Ready]. Add Bill, Add Chore and Add Item use it under their
 * header, so the three forms load and fail the same way as the list screens.
 *
 * @param onRetry reloads the housemates; shown on [FormLoadState.Failed].
 */
@Composable
fun FormLoadGate(state: FormLoadState, onRetry: () -> Unit, content: @Composable () -> Unit) {
    when (state) {
        FormLoadState.Loading -> LoadingState()
        FormLoadState.NoHousehold -> EmptyState(
            illustration = R.drawable.illus_empty_activity,
            title = stringResource(R.string.empty_dashboard_title),
            body = stringResource(R.string.empty_dashboard_body)
        )
        is FormLoadState.Failed -> ErrorState(message = stringResource(state.messageRes), onRetry = onRetry)
        FormLoadState.Ready -> content()
    }
}
