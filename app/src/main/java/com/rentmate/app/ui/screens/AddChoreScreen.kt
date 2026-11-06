package com.rentmate.app.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.ui.components.ChoiceChips
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validatePoints
import com.rentmate.app.ui.validation.validateRequiredText

private const val TAG = "AddChoreScreen"

/** Recurrence values stored in chores.recurrence, with their display label. */
private enum class Recurrence(val value: String, @StringRes val labelRes: Int) {
    ONCE("Once", R.string.recurrence_once),
    WEEKLY("Weekly", R.string.recurrence_weekly),
    MONTHLY("Monthly", R.string.recurrence_monthly)
}

/**
 * S7, New chore (US-9, US-12), redesigned for RM-0058: the chore name, how
 * often it repeats, the points and the "Takes turns" order, with "Add chore"
 * at the bottom.
 *
 * Points open the number keypad and must be 1 to 100 (RM-0036), which also
 * stops one chore being worth enough to dominate the leaderboard. Everyone is
 * in the rotation by default, with the person adding it first (see
 * [defaultRotation]). "Add chore" stays disabled until the form is valid, and
 * each field says how to fix it once it has been touched.
 *
 * The frequency chips are Once, Weekly and Monthly because those are the
 * values chores.recurrence accepts (migration 0001). Daily needs a migration.
 */
@Composable
fun AddChoreScreen(onClose: () -> Unit, viewModel: AddChoreViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsState()
    val state by viewModel.state.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var recurrence by rememberSaveable { mutableStateOf(Recurrence.WEEKLY) }
    var pointValue by rememberSaveable { mutableStateOf("1") }

    val titleError = validateRequiredText(title, Limits.TITLE_MAX)
    val pointsError = validatePoints(pointValue)
    val formValid = titleError == null && pointsError == null && members.isNotEmpty()

    LaunchedEffect(state) {
        if (state is AddChoreUiState.Saved) onClose()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.chores_new_title), onBack = onClose)
        FormLoadGate(loadState, onRetry = viewModel::loadMembers) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    ValidatedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.add_chore_name),
                        error = titleError,
                        showErrors = false,
                        maxLength = Limits.TITLE_MAX + 10
                    )

                    ListHeading(stringResource(R.string.add_chore_recurrence))
                    ChoiceChips(
                        options = Recurrence.entries,
                        selected = recurrence,
                        onSelect = { recurrence = it },
                        label = { stringResource(it.labelRes) }
                    )

                    ValidatedTextField(
                        value = pointValue,
                        onValueChange = { input -> pointValue = input.filter { it.isDigit() } },
                        label = stringResource(R.string.chores_points_label),
                        error = pointsError,
                        showErrors = false,
                        maxLength = 3,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        supportingText = stringResource(R.string.chores_points_help)
                    )

                    ListHeading(stringResource(R.string.chores_takes_turns))
                    if (members.isEmpty()) {
                        Text(
                            stringResource(R.string.error_no_members),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        RotationList(members)
                    }
                }
                PrimaryButton(
                    text = stringResource(R.string.add_chore_title),
                    enabled = formValid,
                    loading = state is AddChoreUiState.Loading,
                    onClick = {
                        val points = pointValue.toIntOrNull()
                        if (formValid && points != null) {
                            Log.d(TAG, "add chore recurrence=${recurrence.value} points=$points")
                            viewModel.create(title.trim(), recurrence.value, points)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                )
            }
        }
    }
}

/** The "Takes turns" list: avatar, name ("You first" for the user) and the turn number. */
@Composable
private fun RotationList(members: List<RotationMember>) {
    val fallback = stringResource(R.string.housemate_fallback)
    val youFirst = stringResource(R.string.chores_you_first)
    ListGroup(members.withIndex().toList()) { (index, member) ->
        val name = member.name ?: fallback
        ListRow(
            title = if (member.isYou) youFirst else name,
            leading = { MemberAvatar(name, size = Sizes.avatarHeader, userId = member.userId) },
            trailing = {
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
