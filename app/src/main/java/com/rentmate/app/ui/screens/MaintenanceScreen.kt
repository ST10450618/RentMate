package com.rentmate.app.ui.screens

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import java.text.DateFormat
import com.rentmate.app.data.isValidEmail
import com.rentmate.app.data.buildLandlordEmail
import com.rentmate.app.data.LandlordEmailText
import com.rentmate.app.data.LandlordEmail
import com.rentmate.app.data.LandlordContact
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import android.net.Uri
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentmate.app.R
import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.RequestStatus
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.Urgency
import androidx.compose.ui.text.input.ImeAction
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateOptionalText
import com.rentmate.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S9 - Maintenance. Delivers US-10: log a fault with a photo, send open items to
 * the landlord as one itemised message, and track each through to resolved.
 */
@Composable
fun MaintenanceScreen(viewModel: MaintenanceViewModel = hiltViewModel()) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MaintenanceCategory.OTHER) }
    var urgency by remember { mutableStateOf(Urgency.MEDIUM) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<TitleError?>(null) }

    // TakePicturePreview returns a thumbnail and needs no CAMERA permission or
    // FileProvider. Full-resolution photos go to blob storage at the final POE.
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> if (bitmap != null) photo = bitmap }

    val openRequests = requests.filter { it.status == RequestStatus.OPEN }
    val openCount = openRequests.size

    // RM-0009: "Send to landlord" opens the phone's email app with every open
    // fault itemised, then marks them sent. The first time, it asks for the
    // landlord's address and saves it on the household for everyone.
    val context = LocalContext.current
    val emailText = landlordEmailText()
    var askForEmail by remember { mutableStateOf(false) }
    val openEmail: (LandlordContact) -> Unit = { contact ->
        val email = buildLandlordEmail(contact, openRequests, emailText) { millis ->
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        }
        try {
            context.startActivity(email.toSendIntent())
            viewModel.sendOpenRequests()
        } catch (e: ActivityNotFoundException) {
            viewModel.noEmailApp()
        }
    }

    if (askForEmail) {
        LandlordEmailDialog(
            initial = uiState.landlord?.landlordEmail.orEmpty(),
            onDismiss = { askForEmail = false },
            onConfirm = { address ->
                askForEmail = false
                viewModel.saveLandlordEmail(address) {
                    openEmail((uiState.landlord ?: LandlordContact(householdName = "")).copy(landlordEmail = address))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item {
            Text(
                stringResource(R.string.maintenance_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        if (uiState.loading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        uiState.error?.let { messageRes ->
            item {
                ErrorCard(
                    message = stringResource(messageRes),
                    onRetry = viewModel::refresh,
                    onDismiss = viewModel::dismissError
                )
            }
        }

        item {
            RentMateCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        stringResource(R.string.maintenance_new),
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(Limits.TITLE_MAX + 10); error = null },
                        label = { Text(stringResource(R.string.maintenance_whats_wrong)) },
                        isError = error != null,
                        supportingText = {
                            when (error) {
                                TitleError.BLANK -> Text(stringResource(R.string.error_title_blank))
                                TitleError.TOO_LONG -> Text(stringResource(R.string.error_title_long))
                                null -> {}
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ValidatedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = stringResource(R.string.maintenance_detail),
                        error = validateOptionalText(description, Limits.DESCRIPTION_MAX),
                        showErrors = true,
                        maxLength = Limits.DESCRIPTION_MAX + 20,
                        singleLine = false,
                        minLines = 2,
                        imeAction = ImeAction.Default,
                        supportingText = "${description.length} / ${Limits.DESCRIPTION_MAX}"
                    )

                    Text(
                        stringResource(R.string.maintenance_category),
                        style = MaterialTheme.typography.labelMedium
                    )
                    ChipRow {
                        MaintenanceCategory.entries.forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = { category = option },
                                label = { Text(stringResource(option.labelRes)) }
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.maintenance_urgency),
                        style = MaterialTheme.typography.labelMedium
                    )
                    ChipRow {
                        Urgency.entries.forEach { option ->
                            FilterChip(
                                selected = urgency == option,
                                onClick = { urgency = option },
                                label = { Text(stringResource(option.labelRes)) }
                            )
                        }
                    }

                    val bitmap = photo
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.maintenance_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { takePhoto.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (bitmap == null) R.string.maintenance_add_photo
                                else R.string.maintenance_retake_photo
                            )
                        )
                    }

                    Button(
                        onClick = {
                            error = viewModel.log(title, description, category, urgency, photo)
                            if (error == null) {
                                title = ""
                                description = ""
                                category = MaintenanceCategory.OTHER
                                urgency = Urgency.MEDIUM
                                photo = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.maintenance_log_request))
                    }
                }
            }
        }

        if (openCount > 0) {
            item {
                Button(
                    onClick = {
                        val landlord = uiState.landlord
                        if (landlord != null && landlord.hasEmail) openEmail(landlord) else askForEmail = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(pluralStringResource(R.plurals.maintenance_send_to_landlord, openCount, openCount))
                }
            }
        }

        item {
            Text(
                stringResource(R.string.maintenance_logged),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (requests.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.maintenance_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
        }

        items(requests, key = { it.id }) { request ->
            RequestCard(
                request = request,
                canChangeStatus = request.canChangeStatus(uiState.currentUserId),
                onAdvance = { viewModel.advance(request.id) }
            )
        }
    }
}

/** Friendly error shown when a maintenance call fails, e.g. in airplane mode. */
@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    }
}

@Composable
private fun RequestCard(request: MaintenanceRequest, canChangeStatus: Boolean, onAdvance: () -> Unit) {
    RentMateCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(request.title, style = MaterialTheme.typography.titleMedium)
            if (request.description.isNotBlank()) {
                Text(request.description, style = MaterialTheme.typography.bodyMedium)
            }
            ChipRow {
                AssistChip(onClick = {}, label = { Text(stringResource(request.category.labelRes)) })
                AssistChip(onClick = {}, label = { Text(stringResource(request.urgency.labelRes)) })
            }

            StatusTimeline(request)

            if (request.status.canAdvance && !canChangeStatus) {
                Text(
                    stringResource(R.string.maintenance_status_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.status.canAdvance && canChangeStatus) {
                OutlinedButton(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            R.string.maintenance_mark_as,
                            stringResource(request.status.next()!!.labelRes)
                        )
                    )
                }
            }
        }
    }
}

/** open -> sent -> acknowledged -> resolved, with the date each step happened. */
@Composable
private fun StatusTimeline(request: MaintenanceRequest) {
    val formatter = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RequestStatus.entries.forEach { status ->
            val stamp = request.timestampFor(status)
            val reached = stamp != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (reached) "\u25CF  " else "\u25CB  ",
                    color = if (reached) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = stringResource(status.labelRes) +
                        if (stamp != null) "  -  " + formatter.format(Date(stamp)) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

/** RM-0009: the email's wording, from string resources so it follows the app language. */
@Composable
private fun landlordEmailText(): LandlordEmailText {
    val context = LocalContext.current
    return LandlordEmailText(
        subject = stringResource(R.string.landlord_email_subject),
        greetingNamed = stringResource(R.string.landlord_email_greeting_named),
        greetingGeneric = stringResource(R.string.landlord_email_greeting),
        intro = stringResource(R.string.landlord_email_intro),
        itemCategory = stringResource(R.string.landlord_email_category),
        itemUrgency = stringResource(R.string.landlord_email_urgency),
        itemLogged = stringResource(R.string.landlord_email_logged),
        signOff = stringResource(R.string.landlord_email_sign_off),
        categoryLabel = { context.getString(it.labelRes) },
        urgencyLabel = { context.getString(it.labelRes) }
    )
}

/** mailto: with the subject and body in the URI as well as the extras, which some email apps ignore. */
private fun LandlordEmail.toSendIntent(): Intent {
    val uri = Uri.parse("mailto:" + Uri.encode(to) + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body))
    return Intent(Intent.ACTION_SENDTO, uri)
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
}

/** Asks once for the landlord's address; it is saved on the household for every housemate. */
@Composable
private fun LandlordEmailDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var address by remember { mutableStateOf(initial) }
    var showError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.landlord_email_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.landlord_email_dialog_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; showError = false },
                    label = { Text(stringResource(R.string.landlord_email_field)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.error_email_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (isValidEmail(address)) onConfirm(address.trim()) else showError = true }) {
                Text(stringResource(R.string.landlord_email_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_prompt_cancel)) }
        }
    )
}
