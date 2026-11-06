package com.rentmate.app.ui.screens

import androidx.annotation.StringRes
import com.rentmate.app.R

/*
 * RM-0056 part 5: the Add screens show the shared LoadingState, EmptyState and
 * ErrorState from StateViews.kt until the form has the data it needs. The
 * rules are pure functions, so unit tests can cover them without a device.
 */

/** Whether the saved household id is known yet. */
enum class HouseholdStatus {
    /** The saved id is still being read from disk. */
    Unknown,

    /** The user has no household. */
    None,

    /** A household is selected. */
    Present
}

/** Progress of the request that loads the housemates a form splits between. */
enum class RosterRefresh { Running, Done, Failed }

/** What an Add screen shows in place of, or as, its form. */
sealed interface FormLoadState {
    /** Waiting for the household or the housemates. Shows LoadingState. */
    data object Loading : FormLoadState

    /** The user has no household, so there is nothing to add to. Shows EmptyState. */
    data object NoHousehold : FormLoadState

    /** The housemates could not be loaded. Shows ErrorState with Retry. */
    data class Failed(@StringRes val messageRes: Int) : FormLoadState

    /** The form can be filled in. */
    data object Ready : FormLoadState
}

/**
 * Decides what an Add screen shows.
 *
 * @param household whether the saved household id is known.
 * @param needsRoster true for forms that split work or money between housemates
 *   (Add Bill, Add Chore). Add Item needs only the household.
 * @param rosterSize housemates known so far, cached or fresh.
 * @param refresh progress of the request that reloads the housemates.
 */
fun formLoadState(
    household: HouseholdStatus,
    needsRoster: Boolean,
    rosterSize: Int,
    refresh: RosterRefresh
): FormLoadState = when {
    household == HouseholdStatus.Unknown -> FormLoadState.Loading
    household == HouseholdStatus.None -> FormLoadState.NoHousehold
    !needsRoster || rosterSize > 0 -> FormLoadState.Ready
    refresh == RosterRefresh.Running -> FormLoadState.Loading
    refresh == RosterRefresh.Failed -> FormLoadState.Failed(R.string.form_error_members)
    else -> FormLoadState.Failed(R.string.error_no_members)
}
