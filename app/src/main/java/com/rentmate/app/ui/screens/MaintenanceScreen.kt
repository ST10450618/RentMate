package com.rentmate.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rentmate.app.R
import com.rentmate.app.data.LandlordContact
import com.rentmate.app.data.LandlordEmail
import com.rentmate.app.data.LandlordEmailText
import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.RequestStatus
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.Urgency
import com.rentmate.app.data.attachments.AttachmentKind
import com.rentmate.app.data.attachments.PhotoLink
import com.rentmate.app.data.buildLandlordEmail
import com.rentmate.app.data.isValidEmail
import com.rentmate.app.data.validateTitle
import com.rentmate.app.ui.components.AttachmentThumbnails
import com.rentmate.app.ui.components.ChoiceChips
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.PhotoAttachmentPicker
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.ValidatedTextField
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateOptionalText
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MaintenanceScreen"

/**
 * S9, Repairs (US-10): log a fault with a photo, send open faults to the
 * landlord as one itemised email, and track each one to resolved.
 *
 * RM-0059: the screen has three pages, kept here because navigation gives
 * Maintenance one route. The list shows open faults first, done ones folded,
 * "Log a fault" as a text action and "Send n to landlord" as the one filled
 * button. "Log a fault" opens the form, and a tap on a fault opens its detail.
 * System Back returns from the form or the detail to the list.
 */
@Composable
fun MaintenanceScreen(
    viewModel: MaintenanceViewModel = hiltViewModel(),
    photosViewModel: PhotoAttachmentsViewModel = hiltViewModel()
) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    // RM-0059: null shows the list, LOG_PAGE the form, anything else a request ID.
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) {
        Log.d(TAG, "back from $page to the list")
        page = null
    }

    val openRequests = requests.filter { it.status == RequestStatus.OPEN }

    // RM-0009: "Send to landlord" opens the phone's email app with every open
    // fault itemised, then marks them sent. The first time, it asks for the
    // landlord's address and saves it on the household for everyone.
    val context = LocalContext.current
    val emailText = landlordEmailText()
    var askForEmail by remember { mutableStateOf(false) }
    // RM-0029: each fault lists 7-day signed links to its photos.
    val openEmail: (LandlordContact) -> Unit = { contact ->
        val toSend = openRequests
        viewModel.photoLinksForEmail(toSend.map { it.id }) { photoLinks ->
            val email = buildLandlordEmail(contact, toSend, emailText, photoLinks) { millis ->
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
            }
            try {
                context.startActivity(email.toSendIntent())
                viewModel.sendOpenRequests()
            } catch (e: ActivityNotFoundException) {
                viewModel.noEmailApp()
            }
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

    val current = page
    when {
        current == LOG_PAGE -> LogFaultForm(
            viewModel = viewModel,
            photosViewModel = photosViewModel,
            onClose = { page = null }
        )
        current != null -> {
            val request = requests.firstOrNull { it.id == current }
            if (request == null) {
                // The request went away (for example after a refresh); go back to the list.
                LaunchedEffect(current) { page = null }
            } else {
                val statusLabel = request.status.next()?.let { stringResource(it.labelRes) }
                RepairDetail(
                    request = request,
                    photos = uiState.photos[request.id].orEmpty(),
                    currentUserId = uiState.currentUserId,
                    onAdvance = { viewModel.advance(request.id, statusLabel) },
                    onBack = { page = null }
                )
            }
        }
        else -> RepairsList(
            requests = requests,
            uiState = uiState,
            onRetry = viewModel::refresh,
            onDismissError = viewModel::dismissError,
            onOpen = { page = it },
            onLogFault = { page = LOG_PAGE },
            onSend = {
                val landlord = uiState.landlord
                if (landlord != null && landlord.hasEmail) openEmail(landlord) else askForEmail = true
            }
        )
    }
}

/** The [MaintenanceScreen] page key for the Log a fault form. Request IDs are UUIDs, so they never clash. */
private const val LOG_PAGE = "log"

/**
 * RM-0059: the Repairs list. Open faults come first, then a folded
 * "Done repairs (n)" row. "Send n to landlord" is the one filled button and
 * shows only while faults are open.
 */
@Composable
private fun RepairsList(
    requests: List<MaintenanceRequest>,
    uiState: MaintenanceUiState,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onOpen: (String) -> Unit,
    onLogFault: () -> Unit,
    onSend: () -> Unit
) {
    val open = requests.filter { it.status != RequestStatus.RESOLVED }
    val done = requests.filter { it.status == RequestStatus.RESOLVED }
    val openCount = requests.count { it.status == RequestStatus.OPEN }
    var showDone by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.maintenance_title), eyebrow = stringResource(R.string.nav_house))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (uiState.loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            uiState.error?.let { messageRes ->
                item { ErrorCard(message = stringResource(messageRes), onRetry = onRetry, onDismiss = onDismissError) }
            }
            if (requests.isEmpty() && !uiState.loading) {
                item {
                    EmptyState(
                        illustration = R.drawable.illus_empty_shopping,
                        title = stringResource(R.string.house_repairs_empty_title),
                        body = stringResource(R.string.house_repairs_empty_body)
                    )
                }
            }
            if (open.isNotEmpty()) {
                item { ListHeading(stringResource(R.string.house_open_heading, open.size)) }
                item(key = "open") {
                    ListGroup(open) { request ->
                        RepairRow(request, uiState.photos[request.id].orEmpty(), onClick = { onOpen(request.id) })
                    }
                }
            }
            if (done.isNotEmpty()) {
                item(key = "done-toggle") {
                    ListGroup {
                        ListRow(
                            title = stringResource(R.string.house_done_count, done.size),
                            onClick = { showDone = !showDone },
                            trailing = {
                                Text(
                                    stringResource(if (showDone) R.string.house_hide else R.string.house_show),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
                if (showDone) {
                    item(key = "done") {
                        ListGroup(done) { request ->
                            RepairRow(request, uiState.photos[request.id].orEmpty(), onClick = { onOpen(request.id) })
                        }
                    }
                }
            }
            item {
                TextButton(
                    onClick = onLogFault,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch)
                ) {
                    Text(stringResource(R.string.house_log_fault), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (openCount > 0) {
            PrimaryButton(
                text = pluralStringResource(R.plurals.maintenance_send_to_landlord, openCount, openCount),
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md)
            )
        }
    }
}

/** One fault: photo thumbnail, title, "category · urgency · age", and a status pill. */
@Composable
private fun RepairRow(request: MaintenanceRequest, photos: List<PhotoLink>, onClick: () -> Unit) {
    val age = DateUtils.getRelativeTimeSpanString(request.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val supporting = listOf(
        stringResource(request.category.labelRes),
        stringResource(request.urgency.labelRes),
        age
    ).joinToString(" · ")
    ListRow(
        title = request.title,
        supporting = supporting,
        onClick = onClick,
        leading = { RepairThumbnail(request.title, photos.firstOrNull(), Sizes.thumbnail) },
        trailing = { RepairStatusPill(request) }
    )
}

/** The first photo of a fault, or a plain tile when it has none. */
@Composable
private fun RepairThumbnail(title: String, photo: PhotoLink?, size: Dp) {
    val shapeModifier = Modifier
        .size(size)
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (photo == null) {
        Box(shapeModifier)
    } else {
        AsyncImage(
            model = photo.url,
            contentDescription = stringResource(R.string.house_photo_of, title),
            contentScale = ContentScale.Crop,
            modifier = shapeModifier
        )
    }
}

/** Open faults show their urgency when it is high; others show their status. */
@Composable
private fun RepairStatusPill(request: MaintenanceRequest) {
    if (request.status == RequestStatus.OPEN && request.urgency == Urgency.HIGH) {
        StatusPill(stringResource(Urgency.HIGH.labelRes), PillTone.Danger)
    } else {
        val tone = when (request.status) {
            RequestStatus.OPEN -> PillTone.Attention
            RequestStatus.SENT, RequestStatus.ACKNOWLEDGED -> PillTone.Neutral
            RequestStatus.RESOLVED -> PillTone.Positive
        }
        StatusPill(stringResource(request.status.labelRes), tone)
    }
}

/**
 * RM-0059: the Log a fault form. "Log fault" sits at the bottom and stays off
 * until the fault has a title and no photo is still uploading (RM-0028). A
 * helper line under it says why it is off.
 */
@Composable
private fun LogFaultForm(
    viewModel: MaintenanceViewModel,
    photosViewModel: PhotoAttachmentsViewModel,
    onClose: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(MaintenanceCategory.OTHER) }
    var urgency by rememberSaveable { mutableStateOf(Urgency.MEDIUM) }
    // RM-0028: picked photos are compressed below 1 MB and uploaded straight away.
    val photos by photosViewModel.photos.collectAsStateWithLifecycle()
    val photosBusy = photos.any { it.status is PhotoStatus.Compressing || it.status is PhotoStatus.Uploading }
    val titleError = validateTitle(title)
    val helper = when {
        photosBusy -> stringResource(R.string.house_fault_wait_photo)
        titleError == TitleError.BLANK -> stringResource(R.string.house_fault_needs_title)
        titleError == TitleError.TOO_LONG -> stringResource(R.string.error_title_long)
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.house_log_fault), onBack = onClose)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            ValidatedTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.house_what_is_wrong),
                error = null,
                showErrors = false,
                maxLength = Limits.TITLE_MAX + 10,
                supportingText = if (titleError == TitleError.TOO_LONG) stringResource(R.string.error_title_long) else null
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
            SectionLabel(stringResource(R.string.house_type))
            ChoiceChips(
                options = MaintenanceCategory.entries,
                selected = category,
                onSelect = { category = it },
                label = { stringResource(it.labelRes) }
            )
            SectionLabel(stringResource(R.string.house_how_urgent))
            ChoiceChips(
                options = Urgency.entries,
                selected = urgency,
                onSelect = { urgency = it },
                label = { stringResource(it.labelRes) }
            )
            PhotoAttachmentPicker(
                photos = photos,
                onPicked = { photosViewModel.add(it, AttachmentKind.MAINTENANCE) },
                onRetry = photosViewModel::retry,
                onRemove = photosViewModel::remove,
                maxPhotos = PhotoAttachmentsViewModel.MAX_PHOTOS
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrimaryButton(
                text = stringResource(R.string.house_log_fault_button),
                enabled = helper == null,
                onClick = {
                    val error = viewModel.log(title, description, category, urgency, photosViewModel.uploadedPaths)
                    if (error == null) {
                        Log.d(TAG, "fault logged, back to the list")
                        photosViewModel.clear()
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (helper != null) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * RM-0059: one fault in full: a large photo, its status, a Progress list and
 * the RM-0009 rule as a sentence. Only the housemate who logged a fault (or
 * who sent it) may move it on, and the sentence says so instead of hiding
 * the button without a word.
 */
@Composable
private fun RepairDetail(
    request: MaintenanceRequest,
    photos: List<PhotoLink>,
    currentUserId: String?,
    onAdvance: () -> Unit,
    onBack: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val canChange = request.canChangeStatus(currentUserId)
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = request.title, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            photos.firstOrNull()?.let { photo ->
                AsyncImage(
                    model = photo.url,
                    contentDescription = stringResource(R.string.house_photo_of, request.title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = Sizes.photoPreviewMax)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            if (photos.size > 1) AttachmentThumbnails(photos.drop(1))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RepairStatusPill(request)
                Text(
                    stringResource(request.category.labelRes) + " · " +
                        stringResource(R.string.house_logged_on, formatter.format(Date(request.createdAt))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.description.isNotBlank()) {
                Text(request.description, style = MaterialTheme.typography.bodyLarge)
            }
            ListHeading(stringResource(R.string.house_progress))
            val steps = listOf(
                RequestStatus.OPEN to R.string.house_step_logged,
                RequestStatus.SENT to R.string.house_step_sent,
                RequestStatus.ACKNOWLEDGED to R.string.house_step_replied,
                RequestStatus.RESOLVED to R.string.house_step_fixed
            )
            ListGroup(steps) { (status, labelRes) ->
                val stamp = request.timestampFor(status)
                ListRow(
                    title = stringResource(labelRes),
                    leading = {
                        if (stamp != null) StatusPill(stringResource(R.string.house_step_done), PillTone.Positive)
                        else StatusPill(stringResource(R.string.house_step_next), PillTone.Neutral)
                    },
                    trailing = {
                        if (stamp != null) {
                            Text(
                                formatter.format(Date(stamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
            if (request.status.canAdvance) {
                val rule = when {
                    canChange -> R.string.house_rule_you
                    request.status == RequestStatus.OPEN -> R.string.house_rule_logger
                    else -> R.string.house_rule_sender
                }
                Text(
                    stringResource(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val next = request.status.next()
        if (next != null && canChange) {
            PrimaryButton(
                text = stringResource(R.string.maintenance_mark_as, stringResource(next.labelRes)),
                onClick = onAdvance,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md)
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
        Column(modifier = Modifier.padding(Spacing.card), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    }
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
        itemPhotos = stringResource(R.string.landlord_email_photos),
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
            PrimaryButton(
                text = stringResource(R.string.landlord_email_open),
                onClick = { if (isValidEmail(address)) onConfirm(address.trim()) else showError = true }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_prompt_cancel)) }
        }
    )
}
