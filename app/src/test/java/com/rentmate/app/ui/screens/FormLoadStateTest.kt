package com.rentmate.app.ui.screens

import com.rentmate.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** RM-0056 part 5: which shared state view an Add screen shows. */
class FormLoadStateTest {

    @Test
    fun unknownHouseholdIsLoading() {
        assertEquals(FormLoadState.Loading, formLoadState(HouseholdStatus.Unknown, true, 3, RosterRefresh.Done))
    }

    @Test
    fun noHouseholdIsEmpty() {
        assertEquals(FormLoadState.NoHousehold, formLoadState(HouseholdStatus.None, false, 0, RosterRefresh.Done))
        assertEquals(FormLoadState.NoHousehold, formLoadState(HouseholdStatus.None, true, 0, RosterRefresh.Failed))
    }

    @Test
    fun addItemIsReadyWithoutRoster() {
        assertEquals(FormLoadState.Ready, formLoadState(HouseholdStatus.Present, false, 0, RosterRefresh.Running))
    }

    @Test
    fun cachedRosterIsReadyEvenWhenRefreshFails() {
        assertEquals(FormLoadState.Ready, formLoadState(HouseholdStatus.Present, true, 2, RosterRefresh.Failed))
        assertEquals(FormLoadState.Ready, formLoadState(HouseholdStatus.Present, true, 2, RosterRefresh.Running))
    }

    @Test
    fun emptyRosterWaitsForRefresh() {
        assertEquals(FormLoadState.Loading, formLoadState(HouseholdStatus.Present, true, 0, RosterRefresh.Running))
    }

    @Test
    fun failedRefreshWithNoCacheIsError() {
        assertEquals(
            FormLoadState.Failed(R.string.form_error_members),
            formLoadState(HouseholdStatus.Present, true, 0, RosterRefresh.Failed)
        )
    }

    @Test
    fun emptyRosterAfterRefreshSaysNoMembers() {
        assertEquals(
            FormLoadState.Failed(R.string.error_no_members),
            formLoadState(HouseholdStatus.Present, true, 0, RosterRefresh.Done)
        )
    }
}
