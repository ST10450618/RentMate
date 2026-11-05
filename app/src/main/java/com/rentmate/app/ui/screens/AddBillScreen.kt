package com.rentmate.app.ui.screens

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.ui.components.DatePickerField
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.parseAmount
import com.rentmate.app.ui.validation.validateAmount
import com.rentmate.app.ui.validation.validateDueDate
import com.rentmate.app.ui.validation.validateRequiredText
import java.time.LocalDate

/**
 * S5 - Add Bill (US-6): title, amount in rand and due date, split equally
 * across the household.
 *
 * Every field is validated inline (RM-0036). The amount opens the decimal
 * keypad, the due date uses a date picker so it can't be malformed, and Save
 * shows all outstanding errors instead of failing silently.
 */
@Composable
fun AddBillScreen(onClose: () -> Unit, viewModel: AddBillViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsState()
    val state by viewModel.state.collectAsState()

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(LocalDate.now().plusDays(7)) }
    var submitted by remember { mutableStateOf(false) }

    val titleError = validateRequiredText(title, Limits.TITLE_MAX)
    val amountError = validateAmount(amount)
    val dateError = validateDueDate(dueDate)
    val formValid = titleError == null && amountError == null && dateError == null && members.isNotEmpty()

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

        Text(
            if (members.isEmpty()) stringResource(R.string.error_no_members)
            else pluralStringResource(R.plurals.add_bill_split, members.size, members.size),
            style = MaterialTheme.typography.bodySmall,
            color = if (members.isEmpty() && submitted) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        PrimaryButton(
            text = stringResource(R.string.save),
            loading = state is AddBillUiState.Loading,
            onClick = {
                submitted = true
                val parsed = parseAmount(amount)
                val date = dueDate
                if (formValid && parsed != null && date != null) {
                    viewModel.createEqualSplit(title.trim(), parsed.toDouble(), date)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
        )

        (state as? AddBillUiState.Error)?.let {
            Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error)
        }
    }
}
