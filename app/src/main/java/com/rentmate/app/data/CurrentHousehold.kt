package com.rentmate.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The household the signed-in user is currently acting in. Every screen reads it from here. */
@Singleton
class CurrentHousehold @Inject constructor() {
    private val _id = MutableStateFlow<String?>(null)
    val id: StateFlow<String?> = _id

    fun set(householdId: String) {
        _id.value = householdId
    }
}
