package com.rentmate.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.activity.ActivityEvent
import com.rentmate.app.data.activity.ActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the Activity screen shows. */
sealed interface ActivityUiState {
    data object Loading : ActivityUiState
    data class Events(
        val events: List<ActivityEvent>,
        /** Events are sample data (stand-in repository); the screen shows a label. */
        val isSample: Boolean = false
    ) : ActivityUiState
    data class Error(val message: String?) : ActivityUiState
}

/** Live household activity feed (RM-0033): follows the current household and re-subscribes if it changes. */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    repository: ActivityRepository,
    currentHousehold: CurrentHousehold
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActivityUiState> = currentHousehold.id
        .flatMapLatest { householdId ->
            if (householdId == null) {
                flowOf<ActivityUiState>(ActivityUiState.Loading)
            } else {
                repository.observe(householdId)
                    .map<List<ActivityEvent>, ActivityUiState> { ActivityUiState.Events(it, repository.isSampleData) }
                    .onStart { emit(ActivityUiState.Loading) }
                    .catch { e ->
                        Log.w(TAG, "Activity feed failed", e)
                        emit(ActivityUiState.Error(e.message))
                    }
            }
        }
        .onEach { Log.d(TAG, "state -> ${it::class.simpleName}") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState.Loading)

    private companion object {
        const val TAG = "ActivityViewModel"
    }
}
