package com.rentmate.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.rentmate.app.R
import com.rentmate.app.ui.validation.FieldError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Text field that shows a [FieldError] inline under the input (RM-0036).
 *
 * The error only appears once the user has typed in the field or tried to
 * submit ([showErrors]), so an empty form doesn't open covered in red.
 * [maxLength] hard-caps what can be typed or pasted, so oversized input never
 * reaches validation or the network.
 */
@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: FieldError?,
    showErrors: Boolean,
    modifier: Modifier = Modifier,
    maxLength: Int = 200,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null
) {
    var touched by remember { mutableStateOf(false) }
    val visibleError = if (touched || showErrors) error else null

    OutlinedTextField(
        value = value,
        onValueChange = {
            touched = true
            onValueChange(it.take(maxLength))
        },
        label = { Text(label) },
        isError = visibleError != null,
        supportingText = when {
            visibleError != null -> { { Text(stringResource(visibleError.messageRes)) } }
            supportingText != null -> { { Text(supportingText) } }
            else -> null
        },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = imeAction
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Read-only field that opens a Material date picker, replacing the old
 * "yyyy-mm-dd" free-text box so a malformed date can't be entered at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    error: FieldError?,
    showErrors: Boolean,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) open = true }
    }

    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val visibleError = if (showErrors) error else null

    OutlinedTextField(
        value = date?.format(formatter).orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.pick_date)) },
        isError = visibleError != null,
        supportingText = visibleError?.let { e -> { Text(stringResource(e.messageRes)) } },
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth()
    )

    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }
}
