package com.rentmate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.rentmate.app.R
import com.rentmate.app.ui.components.BrandMark
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.SecondaryButton
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.normaliseInviteCode
import com.rentmate.app.ui.validation.validateInviteCode
import com.rentmate.app.ui.validation.validateRequiredText
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.theme.Spacing

// US-5: create a household or join one with a six-character invite code.
@Composable
fun HouseholdSetupScreen(
    onDone: () -> Unit,
    viewModel: HouseholdSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var householdName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var createTried by remember { mutableStateOf(false) }
    var joinTried by remember { mutableStateOf(false) }
    val nameError = validateRequiredText(householdName, Limits.HOUSEHOLD_NAME_MAX, Limits.HOUSEHOLD_NAME_MIN)
    val codeError = validateInviteCode(inviteCode)
    val busy = state is HouseholdSetupUiState.Loading

    LaunchedEffect(state) {
        if (state is HouseholdSetupUiState.Joined) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark(size = 72.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Set up your household",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "Create a new home or join your housemates with their invite code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.xl))
        SectionLabel("New household")
        Spacer(Modifier.height(Spacing.sm))

        ValidatedTextField(
            value = householdName,
            onValueChange = { householdName = it },
            label = stringResource(R.string.household_name),
            error = nameError,
            showErrors = createTried,
            maxLength = Limits.HOUSEHOLD_NAME_MAX + 10,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton(
            text = stringResource(R.string.household_create),
            enabled = !busy,
            onClick = {
                createTried = true
                if (nameError == null) viewModel.create(householdName.trim())
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel("Join an existing one")
        Spacer(Modifier.height(Spacing.sm))

        ValidatedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.uppercase() },
            label = stringResource(R.string.household_invite_code),
            error = codeError,
            showErrors = joinTried,
            maxLength = Limits.INVITE_CODE_LENGTH + 2,
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(Spacing.sm))
        SecondaryButton(
            text = stringResource(R.string.household_join),
            enabled = !busy,
            onClick = {
                joinTried = true
                if (codeError == null) viewModel.join(normaliseInviteCode(inviteCode))
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is HouseholdSetupUiState.Loading -> Row { CircularProgressIndicator() }
            is HouseholdSetupUiState.Error -> Text(stringResource(s.messageRes), color = MaterialTheme.colorScheme.error)
            is HouseholdSetupUiState.Created -> {
                RentMateCard(accent = MaterialTheme.colorScheme.primary) {
                    SectionLabel("Household created. Share this invite code")
                    Text(
                        s.inviteCode,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 4.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
            else -> {}
        }
    }
}
