package com.rentmate.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.ui.components.ChipRow
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validatePoints
import com.rentmate.app.ui.validation.validateRequiredText

/** Recurrence values stored in chores.recurrence, with their display label. */
private enum class Recurrence(val value: String, @StringRes val labelRes: Int) {
    ONCE("Once", R.string.recurrence_once),
    WEEKLY("Weekly", R.string.recurrence_weekly),
    MONTHLY("Monthly", R.string.recurrence_monthly)
}

/**
 * S7 - Add Chore (US-9, US-12): title, recurrence and point value. The
 * rotation order follows household membership.
 *
 * Points open the number keypad and must be 1 to 100 (RM-0036), which also
 * stops one chore being worth enough to dominate the leaderboard.
 */
@Composable
fun AddChoreScreen(onClose: () -> Unit, viewModel: AddChoreViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsState()
    val state by viewModel.state.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    var title by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf(Recurrence.WEEKLY) }
    var pointValue by remember { mutableStateOf("1") }
    var submitted by remember { mutableStateOf(false) }

    val titleError = validateRequiredText(title, Limits.TITLE_MAX)
    val pointsError = validatePoints(pointValue)
    val formValid = titleError == null && pointsError == null && members.isNotEmpty()

    LaunchedEffect(state) {
        if (state is AddChoreUiState.Saved) onClose()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.add_chore_title), onBack = onClose)
        FormLoadGate(loadState, onRetry = viewModel::loadMembers) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ValidatedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.add_chore_name),
                    error = titleError,
                    showErrors = submitted,
                    maxLength = Limits.TITLE_MAX + 10
                )

                SectionLabel(stringResource(R.string.add_chore_recurrence))
                ChipRow {
                    Recurrence.entries.forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(stringResource(option.labelRes)) }
                        )
                    }
                }

                ValidatedTextField(
                    value = pointValue,
                    onValueChange = { input -> pointValue = input.filter { it.isDigit() } },
                    label = stringResource(R.string.add_chore_points),
                    error = pointsError,
                    showErrors = submitted,
                    maxLength = 3,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )

                Text(
                    if (members.isEmpty()) stringResource(R.string.error_no_members)
                    else pluralStringResource(R.plurals.add_chore_rotation, members.size, members.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (members.isEmpty() && submitted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                PrimaryButton(
                    text = stringResource(R.string.save),
                    loading = state is AddChoreUiState.Loading,
                    onClick = {
                        submitted = true
                        val points = pointValue.toIntOrNull()
                        if (formValid && points != null) viewModel.create(title.trim(), recurrence.value, points)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                )
            }
        }
    }
}
