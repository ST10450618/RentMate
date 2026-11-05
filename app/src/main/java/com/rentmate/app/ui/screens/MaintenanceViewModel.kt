package com.rentmate.app.ui.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.rentmate.app.R
import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRepository
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.Urgency
import com.rentmate.app.data.validateTitle
import com.rentmate.app.ui.error.AppError
import com.rentmate.app.ui.error.ErrorMapper
import com.rentmate.app.ui.launchSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val TAG = "MaintenanceViewModel"

/** Which maintenance action failed; each maps to a friendly, translatable message. */
enum class MaintenanceError(@StringRes val messageRes: Int) {
    LOAD(R.string.maintenance_error_load),
    SAVE(R.string.maintenance_error_save),
    UPDATE(R.string.maintenance_error_update),
    SEND(R.string.maintenance_error_send)
}

/**
 * Loading and error state for S9. The requests themselves come from [MaintenanceViewModel.requests].
 * [error] is a string resource: the action-specific message when offline, otherwise the
 * [ErrorMapper] message (e.g. not allowed, session expired).
 */
data class MaintenanceUiState(
    val loading: Boolean = false,
    @StringRes val error: Int? = null
)

/**
 * S9 - Maintenance (US-10). Every repository call goes through [launchSafely],
 * so being offline shows an error card instead of crashing the app.
 */
@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val repository: MaintenanceRepository
) : ViewModel() {

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
            repository.refresh()
            _state.update { it.copy(loading = false) }
        }
    }

    fun log(
        title: String,
        description: String,
        category: MaintenanceCategory,
        urgency: Urgency,
        photo: Bitmap?
    ): TitleError? {
        val error = validateTitle(title)
        if (error != null) return error

        Log.d(TAG, "log request")
        launchSafely(TAG, onError = { fail(it, MaintenanceError.SAVE) }) {
            repository.add(
                MaintenanceRequest(
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    urgency = urgency,
                    photo = photo
                )
            )
        }
        return null
    }

    fun advance(id: String) {
        Log.d(TAG, "advance $id")
        launchSafely(TAG, onError = { fail(it, MaintenanceError.UPDATE) }) { repository.advance(id) }
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
        val message = when (ErrorMapper.classify(cause)) {
            AppError.NO_CONNECTION, AppError.GENERIC -> action.messageRes
            else -> ErrorMapper.classify(cause).messageRes
        }
        _state.update { it.copy(loading = false, error = message) }
    }
}
