package com.rentmate.app.auth

/** The first screen to show when the app launches. */
enum class StartDestination { LOGIN, HOUSEHOLD_SETUP, DASHBOARD }

/** Restores a saved sign-in at launch. An interface so tests can fake it. */
interface SessionRepository {
    /** Loads any saved session and returns where the app should start. May throw. */
    suspend fun restoreSession(): StartDestination
}
