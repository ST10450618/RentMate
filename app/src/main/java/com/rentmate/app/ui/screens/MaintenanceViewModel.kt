package com.rentmate.app.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRepository
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.Urgency
import com.rentmate.app.data.validateTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val repository: MaintenanceRepository
) : ViewModel() {

    val requests: StateFlow<List<MaintenanceRequest>> = repository.requests

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
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

        viewModelScope.launch {
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
        viewModelScope.launch { repository.advance(id) }
    }

    fun sendOpenRequests() {
        viewModelScope.launch { repository.sendOpenRequests() }
    }
}
