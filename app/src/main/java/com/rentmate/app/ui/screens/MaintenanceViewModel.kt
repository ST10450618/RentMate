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
