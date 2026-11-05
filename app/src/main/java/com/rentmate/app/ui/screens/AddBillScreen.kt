package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.data.BillSplit
import com.rentmate.app.data.SplitError
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.SplitResult
import java.math.BigDecimal
import java.time.LocalDate

// US-6: title, amount in ZAR, due date; split equally, by percentage or by exact amounts (RM-0010).
@Composable
fun AddBillScreen(onClose: () -> Unit, viewModel: AddBillViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsState()
    val state by viewModel.state.collectAsState()

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var mode by remember { mutableStateOf(SplitMode.Equal) }
    var splitInputs by remember { mutableStateOf(emptyMap<String, String>()) }

    LaunchedEffect(state) {
        if (state is AddBillUiState.Saved) onClose()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Add bill", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (ZAR)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = dueDate,
            onValueChange = { dueDate = it },
            label = { Text("Due date (yyyy-mm-dd)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Text(stringResource(R.string.split_mode_label), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == SplitMode.Equal, onClick = { mode = SplitMode.Equal },
                label = { Text(stringResource(R.string.split_equal)) })
            FilterChip(selected = mode == SplitMode.Percentage, onClick = { mode = SplitMode.Percentage },
                label = { Text(stringResource(R.string.split_percentage)) })
            FilterChip(selected = mode == SplitMode.ExactAmount, onClick = { mode = SplitMode.ExactAmount },
                label = { Text(stringResource(R.string.split_exact)) })
        }
        Spacer(Modifier.height(8.dp))

        val parsedAmount = amount.trim().replace(',', '.').toBigDecimalOrNull()
        val parsedDate = runCatching { LocalDate.parse(dueDate) }.getOrNull()
        val split = BillSplit.compute(mode, parsedAmount, members.map { it.user_id }, splitInputs)

        if (mode == SplitMode.Equal) {
            Text(stringResource(R.string.split_equal_hint, members.size), style = MaterialTheme.typography.bodySmall)
        } else {
            members.forEach { member ->
                OutlinedTextField(
                    value = splitInputs[member.user_id].orEmpty(),
                    onValueChange = { splitInputs = splitInputs + (member.user_id to it) },
                    label = {
                        Text(
                            stringResource(
                                if (mode == SplitMode.Percentage) R.string.split_percent_for else R.string.split_amount_for,
                                member.profiles?.display_name ?: stringResource(R.string.split_unknown_member)
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }

        // Inline message until the split adds up; the amount/title errors are shown by the field state itself.
        if (split is SplitResult.Invalid && parsedAmount != null && parsedAmount.signum() > 0 && members.isNotEmpty()) {
            val message = when (val e = split.error) {
                is SplitError.PercentTotal -> stringResource(R.string.split_error_percent, e.total.stripTrailingZeros().toPlainString())
                is SplitError.ExactTotal -> stringResource(R.string.split_error_exact, e.total.toPlainString(), parsedAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                SplitError.NotANumber -> stringResource(R.string.split_error_number)
                SplitError.Negative -> stringResource(R.string.split_error_negative)
                SplitError.TooLarge -> stringResource(R.string.split_error_too_large)
                SplitError.InvalidAmount, SplitError.NoMembers -> null
            }
            message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (parsedAmount != null && parsedDate != null && split is SplitResult.Valid) {
                    viewModel.createBill(title, parsedAmount, parsedDate, mode, split.shares)
                }
            },
            enabled = title.isNotBlank() && parsedDate != null && split is SplitResult.Valid &&
                state !is AddBillUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        when (val s = state) {
            is AddBillUiState.Loading -> Row { CircularProgressIndicator() }
            is AddBillUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
