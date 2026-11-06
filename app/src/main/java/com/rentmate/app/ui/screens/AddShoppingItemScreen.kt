package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateRequiredText

/**
 * Add Shopping Item (US-13): name only; the list records who added it.
 * The name is required and capped at [Limits.ITEM_NAME_MAX] characters (RM-0036).
 */
@Composable
fun AddShoppingItemScreen(onClose: () -> Unit, viewModel: AddShoppingItemViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    var name by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val nameError = validateRequiredText(name, Limits.ITEM_NAME_MAX)

    LaunchedEffect(state) {
        if (state is AddShoppingItemUiState.Saved) onClose()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.add_item_title), onBack = onClose)
        FormLoadGate(loadState, onRetry = {}) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ValidatedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.add_item_name),
                    error = nameError,
                    showErrors = submitted,
                    maxLength = Limits.ITEM_NAME_MAX + 10,
                    imeAction = ImeAction.Done
                )

                PrimaryButton(
                    text = stringResource(R.string.save),
                    loading = state is AddShoppingItemUiState.Loading,
                    onClick = {
                        submitted = true
                        if (nameError == null) viewModel.add(name.trim())
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                )
            }
        }
    }
}
