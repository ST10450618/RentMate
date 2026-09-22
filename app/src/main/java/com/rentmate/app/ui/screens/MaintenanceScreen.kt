package com.rentmate.app.ui.screens

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    val openCount = requests.count { it.status == RequestStatus.OPEN }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                stringResource(R.string.maintenance_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.maintenance_new),
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; error = null },
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

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.maintenance_detail)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
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
                    onClick = { viewModel.sendOpenRequests() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.maintenance_send_to_landlord, openCount))
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
            RequestCard(request = request, onAdvance = { viewModel.advance(request.id) })
        }
    }
}

@Composable
private fun RequestCard(request: MaintenanceRequest, onAdvance: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(request.title, style = MaterialTheme.typography.titleMedium)
            if (request.description.isNotBlank()) {
                Text(request.description, style = MaterialTheme.typography.bodyMedium)
            }
            ChipRow {
                AssistChip(onClick = {}, label = { Text(stringResource(request.category.labelRes)) })
                AssistChip(onClick = {}, label = { Text(stringResource(request.urgency.labelRes)) })
            }

            StatusTimeline(request)

            if (request.status.canAdvance) {
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
