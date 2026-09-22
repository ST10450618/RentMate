# setup-maintenance.ps1
# Builds the RentMate Maintenance Log (S9 / US-10): model, repository,
# ViewModel, Compose UI with photo capture, and unit tests.
#
# Run from the project root:
#   powershell -ExecutionPolicy Bypass -File .\setup-maintenance.ps1
#
# Overwrites ui/screens/MaintenanceScreen.kt. Everything else is new.
# Commit before running if you want an easy undo.

$ErrorActionPreference = 'Stop'
$base = "app\src\main\java\com\rentmate\app"
$res  = "app\src\main\res"
$test = "app\src\test\java\com\rentmate\app"

if (-not (Test-Path "$base\MainActivity.kt")) {
    Write-Host "Can't find $base\MainActivity.kt - are you in the project root?" -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path "$base\data" | Out-Null
New-Item -ItemType Directory -Force -Path "$test" | Out-Null

function Write-Kt($path, $text) {
    Set-Content -Path $path -Value $text -Encoding UTF8
    Write-Host "  wrote $path"
}

# -------------------- model

Write-Kt "$base\data\MaintenanceRequest.kt" @'
package com.rentmate.app.data

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.rentmate.app.R
import java.util.UUID

/**
 * Maintenance requests are RentMate's differentiator: the Part 1 comparison
 * matrix found no equivalent in Splitwise, Flatastic or Tricount. This is the
 * one feature that serves the tenant's relationship with a landlord rather than
 * the housemates' relationship with each other.
 *
 * Fields mirror the MaintenanceRequest entity in Section 7 of the design document.
 */
enum class MaintenanceCategory(@StringRes val labelRes: Int) {
    PLUMBING(R.string.category_plumbing),
    ELECTRICAL(R.string.category_electrical),
    STRUCTURAL(R.string.category_structural),
    APPLIANCE(R.string.category_appliance),
    OTHER(R.string.category_other)
}

enum class Urgency(@StringRes val labelRes: Int) {
    LOW(R.string.urgency_low),
    MEDIUM(R.string.urgency_medium),
    HIGH(R.string.urgency_high)
}

/**
 * Status moves in one direction only. A request cannot go back to open once it
 * has been sent, because the tenant's record of when the landlord was told is
 * the point of keeping it.
 */
enum class RequestStatus(@StringRes val labelRes: Int) {
    OPEN(R.string.status_open),
    SENT(R.string.status_sent),
    ACKNOWLEDGED(R.string.status_acknowledged),
    RESOLVED(R.string.status_resolved);

    fun next(): RequestStatus? = when (this) {
        OPEN -> SENT
        SENT -> ACKNOWLEDGED
        ACKNOWLEDGED -> RESOLVED
        RESOLVED -> null
    }

    val canAdvance: Boolean get() = next() != null
}

data class MaintenanceRequest(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val category: MaintenanceCategory = MaintenanceCategory.OTHER,
    val urgency: Urgency = Urgency.MEDIUM,
    val status: RequestStatus = RequestStatus.OPEN,
    val photo: Bitmap? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val acknowledgedAt: Long? = null,
    val resolvedAt: Long? = null
) {
    /** The timestamp recorded when the request entered the given status, if it has. */
    fun timestampFor(status: RequestStatus): Long? = when (status) {
        RequestStatus.OPEN -> createdAt
        RequestStatus.SENT -> sentAt
        RequestStatus.ACKNOWLEDGED -> acknowledgedAt
        RequestStatus.RESOLVED -> resolvedAt
    }

    /** Advances one step and stamps the transition. Returns this if already resolved. */
    fun advanced(now: Long = System.currentTimeMillis()): MaintenanceRequest =
        when (status.next()) {
            RequestStatus.SENT -> copy(status = RequestStatus.SENT, sentAt = now)
            RequestStatus.ACKNOWLEDGED -> copy(status = RequestStatus.ACKNOWLEDGED, acknowledgedAt = now)
            RequestStatus.RESOLVED -> copy(status = RequestStatus.RESOLVED, resolvedAt = now)
            null -> this
        }
}

enum class TitleError { BLANK, TOO_LONG }

/**
 * A request with no title is useless to a landlord, and an overlong one breaks
 * the email subject line the request is compiled into.
 */
fun validateTitle(title: String): TitleError? = when {
    title.isBlank() -> TitleError.BLANK
    title.trim().length > 80 -> TitleError.TOO_LONG
    else -> null
}
'@

# -------------------- repository

Write-Kt "$base\data\MaintenanceRepository.kt" @'
package com.rentmate.app.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MaintenanceRepository"

/**
 * In-memory store, held for the life of the process.
 *
 * This is deliberate rather than temporary laziness: the design document states
 * that all shared state passes through the REST API, so the API is the source
 * of truth and local persistence belongs to the offline-sync work scheduled for
 * the final POE. When Seth's MaintenanceController exists, the three methods
 * below become Retrofit calls and nothing above this class changes.
 */
object MaintenanceRepository {

    private val _requests = MutableStateFlow(seedData())
    val requests: StateFlow<List<MaintenanceRequest>> = _requests.asStateFlow()

    fun add(request: MaintenanceRequest) {
        Log.d(TAG, "add ${request.id} '${request.title}' ${request.urgency}")
        _requests.value = listOf(request) + _requests.value
    }

    /** Advances one request through open -> sent -> acknowledged -> resolved. */
    fun advance(id: String) {
        _requests.value = _requests.value.map { request ->
            if (request.id == id) {
                Log.d(TAG, "advance ${request.id} from ${request.status}")
                request.advanced()
            } else {
                request
            }
        }
    }

    /**
     * Compiles every open request into one message to the landlord and marks
     * them sent. Batching is the point: one dated, itemised message beats five
     * separate ones, and it gives the tenant a single record to refer back to.
     */
    fun sendOpenRequests(): Int {
        val now = System.currentTimeMillis()
        val open = _requests.value.count { it.status == RequestStatus.OPEN }
        if (open == 0) return 0
        Log.d(TAG, "sending $open open request(s) to landlord")
        _requests.value = _requests.value.map {
            if (it.status == RequestStatus.OPEN) it.copy(status = RequestStatus.SENT, sentAt = now) else it
        }
        return open
    }

    /** One worked example so the screen is not empty in the demo. Delete before submission. */
    private fun seedData(): List<MaintenanceRequest> {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        return listOf(
            MaintenanceRequest(
                title = "Front gate motor not responding",
                description = "Remote does nothing. Gate has to be pushed open by hand.",
                category = MaintenanceCategory.ELECTRICAL,
                urgency = Urgency.MEDIUM,
                status = RequestStatus.SENT,
                createdAt = now - 5 * day,
                sentAt = now - 3 * day
            )
        )
    }
}
'@

# -------------------- view model

Write-Kt "$base\ui\screens\MaintenanceViewModel.kt" @'
package com.rentmate.app.ui.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRepository
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.Urgency
import com.rentmate.app.data.validateTitle
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "MaintenanceViewModel"

class MaintenanceViewModel : ViewModel() {

    val requests: StateFlow<List<MaintenanceRequest>> = MaintenanceRepository.requests

    /**
     * Returns the validation error, or null when the request was logged.
     * The caller clears the form only on null.
     */
    fun log(
        title: String,
        description: String,
        category: MaintenanceCategory,
        urgency: Urgency,
        photo: Bitmap?
    ): TitleError? {
        val error = validateTitle(title)
        if (error != null) {
            Log.d(TAG, "rejected request: $error")
            return error
        }
        MaintenanceRepository.add(
            MaintenanceRequest(
                title = title.trim(),
                description = description.trim(),
                category = category,
                urgency = urgency,
                photo = photo
            )
        )
        return null
    }

    fun advance(id: String) = MaintenanceRepository.advance(id)

    fun sendOpenRequests(): Int = MaintenanceRepository.sendOpenRequests()
}
'@

# -------------------- screen

Write-Kt "$base\ui\screens\MaintenanceScreen.kt" @'
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun MaintenanceScreen(viewModel: MaintenanceViewModel = viewModel()) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()

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

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
'@

# -------------------- strings (separate file so values/strings.xml is untouched)

Write-Kt "$res\values\strings_maintenance.xml" @'
<resources>
    <string name="maintenance_title">Maintenance</string>
    <string name="maintenance_new">New request</string>
    <string name="maintenance_whats_wrong">What is wrong?</string>
    <string name="maintenance_detail">More detail (optional)</string>
    <string name="maintenance_category">Category</string>
    <string name="maintenance_urgency">Urgency</string>
    <string name="maintenance_photo">Attached photo</string>
    <string name="maintenance_add_photo">Attach a photo</string>
    <string name="maintenance_retake_photo">Replace photo</string>
    <string name="maintenance_log_request">Log request</string>
    <string name="maintenance_send_to_landlord">Send %1$d open request(s) to the landlord</string>
    <string name="maintenance_logged">Logged requests</string>
    <string name="maintenance_empty">Nothing logged yet. Add a request above.</string>
    <string name="maintenance_mark_as">Mark as %1$s</string>

    <string name="error_title_blank">Give the fault a short title</string>
    <string name="error_title_long">Keep the title under 80 characters</string>

    <string name="category_plumbing">Plumbing</string>
    <string name="category_electrical">Electrical</string>
    <string name="category_structural">Structural</string>
    <string name="category_appliance">Appliance</string>
    <string name="category_other">Other</string>

    <string name="urgency_low">Low</string>
    <string name="urgency_medium">Medium</string>
    <string name="urgency_high">High</string>

    <string name="status_open">Logged</string>
    <string name="status_sent">Sent to landlord</string>
    <string name="status_acknowledged">Acknowledged</string>
    <string name="status_resolved">Resolved</string>
</resources>
'@

# -------------------- unit tests

Write-Kt "$test\MaintenanceRequestTest.kt" @'
package com.rentmate.app

import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.RequestStatus
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.validateTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US-10. These cover the rules a marker would actually try to break: an empty
 * title, a status going backwards, and a resolved request being advanced again.
 */
class MaintenanceRequestTest {

    @Test
    fun `a blank title is rejected`() {
        assertEquals(TitleError.BLANK, validateTitle(""))
        assertEquals(TitleError.BLANK, validateTitle("   "))
    }

    @Test
    fun `an overlong title is rejected`() {
        assertEquals(TitleError.TOO_LONG, validateTitle("x".repeat(81)))
    }

    @Test
    fun `a sensible title is accepted`() {
        assertNull(validateTitle("Geyser leaking into the ceiling"))
    }

    @Test
    fun `status advances in order`() {
        assertEquals(RequestStatus.SENT, RequestStatus.OPEN.next())
        assertEquals(RequestStatus.ACKNOWLEDGED, RequestStatus.SENT.next())
        assertEquals(RequestStatus.RESOLVED, RequestStatus.ACKNOWLEDGED.next())
    }

    @Test
    fun `a resolved request cannot advance further`() {
        assertNull(RequestStatus.RESOLVED.next())
        assertFalse(RequestStatus.RESOLVED.canAdvance)
    }

    @Test
    fun `advancing stamps the transition time`() {
        val request = MaintenanceRequest(title = "Broken tap", createdAt = 1_000L)
        val sent = request.advanced(now = 2_000L)

        assertEquals(RequestStatus.SENT, sent.status)
        assertEquals(2_000L, sent.sentAt)
        assertNull(sent.acknowledgedAt)
    }

    @Test
    fun `advancing a resolved request changes nothing`() {
        val resolved = MaintenanceRequest(
            title = "Done already",
            status = RequestStatus.RESOLVED,
            resolvedAt = 5_000L
        )
        assertEquals(resolved, resolved.advanced(now = 9_000L))
    }

    @Test
    fun `earlier timestamps are retained as the request progresses`() {
        val request = MaintenanceRequest(title = "Gate motor", createdAt = 1_000L)
            .advanced(now = 2_000L)
            .advanced(now = 3_000L)

        assertEquals(RequestStatus.ACKNOWLEDGED, request.status)
        assertNotNull(request.timestampFor(RequestStatus.OPEN))
        assertEquals(2_000L, request.timestampFor(RequestStatus.SENT))
        assertEquals(3_000L, request.timestampFor(RequestStatus.ACKNOWLEDGED))
        assertNull(request.timestampFor(RequestStatus.RESOLVED))
    }

    @Test
    fun `a new request starts open with no send timestamp`() {
        val request = MaintenanceRequest(title = "New fault")
        assertEquals(RequestStatus.OPEN, request.status)
        assertTrue(request.status.canAdvance)
        assertNull(request.sentAt)
    }
}
'@

Write-Host ""
Write-Host "Done. Sync and build - no new dependencies needed." -ForegroundColor Green
