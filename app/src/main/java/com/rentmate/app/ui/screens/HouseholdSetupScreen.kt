package com.rentmate.app.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.feedback.FeedbackViewModel
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateInviteCode
import com.rentmate.app.ui.validation.validateRequiredText
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing

private const val TAG = "HouseholdSetup"

/**
 * US-5 and RM-0061: household setup, one question at a time.
 *
 * The first step offers two choices, Join a household and Create a household,
 * with one Continue button at the bottom. Join then asks only for the invite
 * code. Create asks only for the household name, and then shows the code to
 * share. The server calls (create_household and join_household) and the
 * RM-0006 rules do not change.
 *
 * @param onDone called when the user is in a household and can go to Home.
 */
@Composable
fun HouseholdSetupScreen(
    onDone: () -> Unit,
    viewModel: HouseholdSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var step by rememberSaveable { mutableStateOf(SetupStep.CHOOSE) }
    var choice by rememberSaveable { mutableStateOf(SetupStep.JOIN) }
    val busy = state is HouseholdSetupUiState.Loading

    LaunchedEffect(state) {
        if (state is HouseholdSetupUiState.Joined) onDone()
    }

    val created = state as? HouseholdSetupUiState.Created
    // Back from a question returns to the choice, unless a request is running or the household exists.
    BackHandler(enabled = step != SetupStep.CHOOSE && !busy && created == null) {
        Log.d(TAG, "back to the choice")
        step = SetupStep.CHOOSE
    }
    val goBack = { if (!busy) step = SetupStep.CHOOSE }

    when {
        created != null -> CreatedStep(created.inviteCode, onDone)
        step == SetupStep.JOIN -> JoinStep(busy = busy, onBack = goBack, onJoin = viewModel::join)
        step == SetupStep.CREATE -> CreateStep(busy = busy, onBack = goBack, onCreate = viewModel::create)
        else -> ChooseStep(
            firstName = viewModel.firstName,
            choice = choice,
            onChoice = { choice = it },
            onContinue = {
                Log.d(TAG, "continue with $choice")
                step = choice
            }
        )
    }
}

/**
 * The page frame every setup step uses: the header on the ground, the content
 * in a scrolling column, and the one main button at the bottom in thumb reach.
 */
@Composable
private fun SetupFrame(
    title: String,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    button: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ScreenHeader(title = title, eyebrow = eyebrow, onBack = onBack, trailing = {})
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content
        )
        Column(modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg)) {
            button()
        }
    }
}

/** Step 1: two choice cards and Continue. Join is chosen first, because most people join. */
@Composable
private fun ChooseStep(
    firstName: String?,
    choice: SetupStep,
    onChoice: (SetupStep) -> Unit,
    onContinue: () -> Unit
) {
    SetupFrame(
        title = stringResource(R.string.setup_title),
        eyebrow = firstName?.let { stringResource(R.string.setup_welcome, it) },
        button = {
            PrimaryButton(
                text = stringResource(R.string.continue_label),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ChoiceCard(
                title = stringResource(R.string.setup_join_title),
                body = stringResource(R.string.setup_join_body),
                selected = choice == SetupStep.JOIN,
                onClick = { onChoice(SetupStep.JOIN) }
            )
            ChoiceCard(
                title = stringResource(R.string.setup_create_title),
                body = stringResource(R.string.setup_create_body),
                selected = choice == SetupStep.CREATE,
                onClick = { onChoice(SetupStep.CREATE) }
            )
        }
    }
}

/**
 * A selectable card with a title and one line that explains it. The chosen
 * card has an aloe border. TalkBack reads it as a radio button.
 * Candidate for ui/components.
 */
@Composable
private fun ChoiceCard(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = scheme.surface,
        border = BorderStroke(
            if (selected) Sizes.stroke else Sizes.border,
            if (selected) scheme.primary else scheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * Join step: only the invite code. Paste works, and case, spaces and dashes
 * are ignored. Join stays disabled until the code has six characters and shows
 * a spinner while the request runs. A wrong code shows the translated message
 * in a snackbar with Retry, and the field keeps the code so the user can fix it.
 */
@Composable
private fun JoinStep(busy: Boolean, onBack: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val error = validateInviteCode(code)
    SetupFrame(
        title = stringResource(R.string.setup_join_title),
        onBack = onBack,
        button = {
            PrimaryButton(
                text = stringResource(R.string.household_join),
                onClick = { onJoin(code) },
                enabled = canJoin(code, busy = false),
                loading = busy,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        ValidatedTextField(
            value = code,
            onValueChange = { code = cleanInviteCodeInput(it) },
            label = stringResource(R.string.household_invite_code),
            // Show the format error only once the code is full length but still wrong.
            error = error.takeIf { code.length == Limits.INVITE_CODE_LENGTH },
            showErrors = false,
            // Room for a pasted code with spaces or a dash; the filter shortens it.
            maxLength = Limits.INVITE_CODE_LENGTH * 3,
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
            supportingText = stringResource(R.string.setup_code_help)
        )
    }
}

/** Create step: only the household name. Create stays disabled until the name is valid. */
@Composable
private fun CreateStep(busy: Boolean, onBack: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    SetupFrame(
        title = stringResource(R.string.setup_create_title),
        onBack = onBack,
        button = {
            PrimaryButton(
                text = stringResource(R.string.household_create),
                onClick = { onCreate(name.trim()) },
                enabled = canCreate(name, busy = false),
                loading = busy,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        ValidatedTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.household_name),
            error = validateRequiredText(name, Limits.HOUSEHOLD_NAME_MAX, Limits.HOUSEHOLD_NAME_MIN),
            showErrors = false,
            maxLength = Limits.HOUSEHOLD_NAME_MAX + 10,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
            supportingText = stringResource(R.string.setup_name_help)
        )
    }
}

/** After Create: the invite code to share, a Copy action, and Continue to Home. */
@Composable
private fun CreatedStep(inviteCode: String, onDone: () -> Unit, feedback: FeedbackViewModel = hiltViewModel()) {
    val clipboard = LocalClipboardManager.current
    SetupFrame(
        title = stringResource(R.string.setup_share_title),
        button = {
            PrimaryButton(
                text = stringResource(R.string.continue_label),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Text(
            stringResource(R.string.setup_share_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            inviteCode,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
        TextButton(
            onClick = {
                Log.d(TAG, "copy invite code")
                clipboard.setText(AnnotatedString(inviteCode))
                feedback.center.show(R.string.setup_code_copied)
            },
            modifier = Modifier.heightIn(min = Sizes.minTouch)
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
            Text(stringResource(R.string.setup_copy_code), modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}
