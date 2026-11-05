package com.rentmate.app.ui.screens

import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.data.BillSplit
import com.rentmate.app.data.SplitError
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.SplitResult
import com.rentmate.app.ui.components.DatePickerField
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.parseAmount
import com.rentmate.app.ui.validation.validateAmount
import com.rentmate.app.ui.validation.validateDueDate
import com.rentmate.app.ui.validation.validateRequiredText
import java.math.RoundingMode
import java.time.LocalDate

/**
 * S5 - Add Bill (US-6): title, amount in rand and due date, split equally, by
 * percentage or by exact amounts (RM-0010).
 *
 * Every field is validated inline (RM-0036): the amount opens the decimal
 * keypad, the due date uses a date picker so it can't be malformed, and Save
 * shows all outstanding errors instead of failing silently. [BillSplit.compute]
 * checks the split adds up before anything is sent, and the create_bill RPC
 * checks it again on the server.
 */
@Composable
fun AddBillScreen(onClose: () -> Unit, viewModel: AddBillViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsState()
    val state by viewModel.state.collectAsState()

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(LocalDate.now().plusDays(7)) }
    var mode by remember { mutableStateOf(SplitMode.Equal) }
    var splitInputs by remember { mutableStateOf(emptyMap<String, String>()) }
    var submitted by remember { mutableStateOf(false) }

    val titleError = validateRequiredText(title, Limits.TITLE_MAX)
    val amountError = validateAmount(amount)
    val dateError = validateDueDate(dueDate)
    val parsedAmount = parseAmount(amount)
    val split = BillSplit.compute(mode, parsedAmount, members.map { it.user_id }, splitInputs)
    val formValid = titleError == null && amountError == null && dateError == null &&
        members.isNotEmpty() && split is SplitResult.Valid

    LaunchedEffect(state) {
        if (state is AddBillUiState.Saved) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(stringResource(R.string.add_bill_title), style = MaterialTheme.typography.headlineSmall)

        ValidatedTextField(
            value = title,
            onValueChange = { title = it },
            label = stringResource(R.string.add_bill_name),
            error = titleError,
            showErrors = submitted,
            maxLength = Limits.TITLE_MAX + 10
        )
        ValidatedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = stringResource(R.string.add_bill_amount),
            error = amountError,
            showErrors = submitted,
            maxLength = 15,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        )
        DatePickerField(
            date = dueDate,
            onDateChange = { dueDate = it },
            label = stringResource(R.string.add_bill_due),
            error = dateError,
            showErrors = true
        )

        // Split mode (RM-0010)
        SectionLabel(stringResource(R.string.split_mode_label), modifier = Modifier.padding(top = Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(selected = mode == SplitMode.Equal, onClick = { mode = SplitMode.Equal },
                label = { Text(stringResource(R.string.split_equal)) })
            FilterChip(selected = mode == SplitMode.Percentage, onClick = { mode = SplitMode.Percentage },
                label = { Text(stringResource(R.string.split_percentage)) })
            FilterChip(selected = mode == SplitMode.ExactAmount, onClick = { mode = SplitMode.ExactAmount },
                label = { Text(stringResource(R.string.split_exact)) })
        }

        when {
            members.isEmpty() -> Text(
                stringResource(R.string.error_no_members),
                style = MaterialTheme.typography.bodySmall,
                color = if (submitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            mode == SplitMode.Equal -> Text(
                pluralStringResource(R.plurals.add_bill_split, members.size, members.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> members.forEach { member ->
                val name = member.profiles?.display_name ?: stringResource(R.string.housemate_fallback)
                ValidatedTextField(
                    value = splitInputs[member.user_id].orEmpty(),
                    onValueChange = { splitInputs = splitInputs + (member.user_id to it) },
                    label = stringResource(
                        if (mode == SplitMode.Percentage) R.string.split_percent_for else R.string.split_amount_for,
                        name
                    ),
                    error = null,
                    showErrors = false,
                    maxLength = 12,
                    keyboardType = KeyboardType.Decimal
                )
            }
        }

        // Inline message until the split adds up; amount and title errors show under their own fields.
        if (split is SplitResult.Invalid && parsedAmount != null && parsedAmount.signum() > 0 && members.isNotEmpty()) {
            val message = when (val e = split.error) {
                is SplitError.PercentTotal ->
                    stringResource(R.string.split_error_percent, e.total.stripTrailingZeros().toPlainString())
                is SplitError.ExactTotal -> stringResource(
                    R.string.split_error_exact,
                    e.total.toPlainString(),
                    parsedAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                )
                SplitError.NotANumber -> stringResource(R.string.split_error_number)
                SplitError.Negative -> stringResource(R.string.split_error_negative)
                SplitError.TooLarge -> stringResource(R.string.split_error_too_large)
                SplitError.InvalidAmount, SplitError.NoMembers -> null
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        PrimaryButton(
            text = stringResource(R.string.save),
            loading = state is AddBillUiState.Loading,
            onClick = {
                submitted = true
                val date = dueDate
                if (formValid && parsedAmount != null && date != null && split is SplitResult.Valid) {
                    viewModel.createBill(title.trim(), parsedAmount, date, mode, split.shares)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
        )

        (state as? AddBillUiState.Error)?.let {
            Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error)
        }
    }
}
