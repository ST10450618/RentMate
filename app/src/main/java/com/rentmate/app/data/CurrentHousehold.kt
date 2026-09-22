package com.rentmate.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The household the signed-in user is currently acting in. Set once after
 * sign-in (from the first of GET /api/households/mine) or after creating/
 * joining one on S2. A household switcher on the Dashboard is future work;
 * every screen that needs a household id reads it from here.
 */
@Singleton
class CurrentHousehold @Inject constructor() {
    private val _id = MutableStateFlow<String?>(null)
    val id: StateFlow<String?> = _id

    fun set(householdId: String) {
        _id.value = householdId
    }
}
