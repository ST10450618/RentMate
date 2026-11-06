package com.rentmate.app.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.rentmate.app.R
import com.rentmate.app.data.LandlordContact
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRepository
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.Urgency
import com.rentmate.app.data.validateTitle
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.EMAIL_LINK_EXPIRY
import com.rentmate.app.data.attachments.PhotoLink
import com.rentmate.app.data.attachments.THUMBNAIL_URL_EXPIRY
import com.rentmate.app.data.attachments.loadLinks
import kotlinx.coroutines.CancellationException
import com.rentmate.app.ui.feedback.FeedbackCenter
import com.rentmate.app.ui.feedback.UserMessage
import com.rentmate.app.ui.feedback.actionErrorRes
import com.rentmate.app.ui.error.ErrorMapper
import com.rentmate.app.ui.launchSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.rentmate.app.ui.validation.Limits
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val TAG = "MaintenanceViewModel"

/** Which maintenance action failed; each maps to a friendly, translatable message. */
enum class MaintenanceError(@StringRes val messageRes: Int) {
    LOAD(R.string.maintenance_error_load),
    SAVE(R.string.maintenance_error_save),
    UPDATE(R.string.maintenance_error_update),
    SEND(R.string.maintenance_error_send),
    NO_EMAIL_APP(R.string.maintenance_error_no_email_app),
    LINK_PHOTOS(R.string.photo_error_link)
}

/**
 * Loading and error state for S9. The requests themselves come from [MaintenanceViewModel.requests].
 * [error] is a string resource: the action-specific message when offline, otherwise the
 * [ErrorMapper] message (e.g. not allowed, session expired).
 */
data class MaintenanceUiState(
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    /** RM-0009: who the "Send to landlord" email goes to; null until loaded. */
    val landlord: LandlordContact? = null,
    val currentUserId: String? = null,
    /** RM-0029: thumbnails per request ID, with short-lived signed URLs. */
    val photos: Map<String, List<PhotoLink>> = emptyMap()
)

/**
 * S9 - Maintenance (US-10). Every repository call goes through [launchSafely],
 * so being offline shows an error card instead of crashing the app.
 */
@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val repository: MaintenanceRepository,
    private val attachments: AttachmentRepository,
    private val activity: ActivityLogger,
    private val feedback: FeedbackCenter
) : ViewModel() {

    /** A saved request whose photos still need linking, retried by [refresh]. */
    private var pendingLink: Pair<String, List<String>>? = null

    val requests: StateFlow<List<MaintenanceRequest>> = repository.requests

    private val _state = MutableStateFlow(MaintenanceUiState())
    val state: StateFlow<MaintenanceUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        Log.d(TAG, "refresh")
        _state.update { it.copy(loading = true, error = null) }
        launchSafely(TAG, onError = { fail(it, MaintenanceError.LOAD) }) {
            pendingLink?.let { (requestId, paths) -> linkPhotos(requestId, paths) }
            repository.refresh()
            val landlord = repository.landlordContact()
            _state.update { it.copy(loading = false, landlord = landlord, currentUserId = repository.currentUserId) }
            loadPhotos()
        }
    }

    /**
     * Photos are extra: if they fail to load the requests still show, without
     * thumbnails. Cancellation is still passed on.
     */
    private suspend fun loadPhotos() {
        try {
            val links = attachments.loadLinks(THUMBNAIL_URL_EXPIRY)
            _state.update { s ->
                s.copy(photos = links.entries
                    .filter { it.key is AttachmentParent.Maintenance }
                    .associate { it.key.id to it.value })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not load maintenance photos", e)
        }
    }

    private suspend fun linkPhotos(requestId: String, paths: List<String>) {
        pendingLink = requestId to paths
        try {
            attachments.link(AttachmentParent.Maintenance(requestId), paths)
            pendingLink = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not link photos to $requestId", e)
            _state.update { it.copy(error = MaintenanceError.LINK_PHOTOS.messageRes) }
            throw e
        }
    }

    /**
     * RM-0029: signed links (valid 7 days) to each request's photos, for the
     * landlord email. Calls [onReady] with request ID -> links. If the links
     * cannot be made, the email still goes out, without them.
     */
    fun photoLinksForEmail(requestIds: Collection<String>, onReady: (Map<String, List<String>>) -> Unit) {
        Log.d(TAG, "photoLinksForEmail")
        launchSafely(TAG, onError = { fail(it, MaintenanceError.SEND) }) {
            val ids = requestIds.toSet()
            val links = try {
                attachments.loadLinks(EMAIL_LINK_EXPIRY)
                    .filterKeys { it is AttachmentParent.Maintenance && it.id in ids }
                    .entries.associate { (parent, photos) -> parent.id to photos.map { it.url } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sending without photo links", e)
                emptyMap()
            }
            onReady(links)
        }
    }

    /** RM-0009: saves the landlord's address on the household, then runs [then] (opening the email). */
    fun saveLandlordEmail(email: String, then: () -> Unit) {
        Log.d(TAG, "saveLandlordEmail")
        launchSafely(TAG, onError = { fail(it, MaintenanceError.SEND) }) {
            repository.saveLandlordEmail(email)
            _state.update { s -> s.copy(landlord = s.landlord?.copy(landlordEmail = email.trim())) }
            then()
        }
    }

    /** Called when no app on the phone can send email. */
    fun noEmailApp() {
        Log.w(TAG, "no app can handle mailto:")
        feedback.show(MaintenanceError.NO_EMAIL_APP.messageRes)
    }

    fun log(
        title: String,
        description: String,
        category: MaintenanceCategory,
        urgency: Urgency,
        photoPaths: List<String> = emptyList()
    ): TitleError? {
        val error = validateTitle(title)
        if (error != null) return error

        Log.d(TAG, "log request")
        launchSafely(TAG, onError = { fail(it, MaintenanceError.SAVE) }) {
            val created = repository.add(
                MaintenanceRequest(
                    title = title.trim(),
                    description = description.trim().take(Limits.DESCRIPTION_MAX),
                    category = category,
                    urgency = urgency
                )
            )
            if (created != null) activity.maintenanceLogged(created.title, created.id)
            if (created != null && photoPaths.isNotEmpty()) {
                try {
                    linkPhotos(created.id, photoPaths)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launchSafely  // LINK_PHOTOS is already showing; Retry links again.
                }
                loadPhotos()
            }
        }
        return null
    }

    /**
     * Moves a request one step on. RM-0059: when [statusLabel] (the new
     * status, already translated) is given, a snackbar confirms the change.
     */
    fun advance(id: String, statusLabel: String? = null) {
        Log.d(TAG, "advance $id")
        launchSafely(TAG, onError = { fail(it, MaintenanceError.UPDATE) }) {
            repository.advance(id)
            if (statusLabel != null) feedback.show(UserMessage(R.string.house_status_changed, listOf(statusLabel)))
            repository.requests.value.firstOrNull { it.id == id }?.let {
                activity.maintenanceStatus(it.title, it.status.name.lowercase().replaceFirstChar { c -> c.uppercase() }, id)
            }
        }
    }

    fun sendOpenRequests() {
        Log.d(TAG, "sendOpenRequests")
        launchSafely(TAG, onError = { fail(it, MaintenanceError.SEND) }) { repository.sendOpenRequests() }
    }

    /** Hides the error card. */
    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** [launchSafely] has already logged [cause]; this only picks the message. */
    private fun fail(cause: Throwable, action: MaintenanceError) {
        if (action == MaintenanceError.LOAD && pendingLink != null) {
            _state.update { it.copy(loading = false, error = MaintenanceError.LINK_PHOTOS.messageRes) }
            return
        }
        if (action == MaintenanceError.LOAD) {
            // A screen that cannot load keeps the error card with its Retry button.
            val message = actionErrorRes(ErrorMapper.classify(cause), action.messageRes)
            _state.update { it.copy(loading = false, error = message) }
        } else {
            // RM-0052: a failed tap (save, update, send) is a snackbar.
            _state.update { it.copy(loading = false) }
            feedback.failure(cause, action.messageRes)
        }
    }
}
