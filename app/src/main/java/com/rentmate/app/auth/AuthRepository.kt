package com.rentmate.app.auth

import android.util.Log
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ProfileSettingsSync
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "AuthRepository"

/** Whether the signed-in user already belongs to a household, decides S1's next screen (S2 vs S3). */
data class SignInResult(val hasHousehold: Boolean)

/** One `household_members` row, reduced to the column the sign-in flow needs. */
@Serializable
private data class HouseholdMembershipRow(val household_id: String)

/**
 * Signs the user in to Supabase with a Google ID token, restores a saved session at
 * launch and picks the current household. It also pulls the user's saved settings
 * after sign-in so a new device matches the old one.
 *
 * Based on: Supabase Kotlin reference, Sign in with ID token,
 * https://supabase.com/docs/reference/kotlin/auth-signinwithidtoken (accessed 2026-11-06)
 * Based on: Supabase docs, Sign in with Google,
 * https://supabase.com/docs/guides/auth/social-sign-in/auth-google (accessed 2026-11-06)
 */
class AuthRepository @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val profileSettingsSync: ProfileSettingsSync
) : SessionRepository {
    /**
     * Runs the full interactive sign-in: Google account picker, Supabase session,
     * household lookup and settings pull. Returns a failed [Result] instead of throwing.
     */
    suspend fun signIn(): Result<SignInResult> = runCatching {
        Log.d(TAG, "signIn: requesting Google ID token")
        val idToken = googleAuthManager.signIn()
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
        Log.i(TAG, "signIn: Supabase session created")

        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Signed in but no user id was returned")

        val hasHousehold = loadHousehold(userId)
        // RM-0005: bring this account's settings onto this device. Never fails sign-in.
        profileSettingsSync.pull()
        Log.d(TAG, "signIn: done, hasHousehold=$hasHousehold")
        SignInResult(hasHousehold = hasHousehold)
    }.onFailure { Log.w(TAG, "signIn failed", it) }

    /**
     * RM-0002: waits for Supabase to load the session saved on the device, then
     * picks the first screen. A saved session skips Login; the household is
     * looked up again so a removed membership is noticed; offline, the household
     * saved by [CurrentHousehold] is used instead.
     */
    override suspend fun restoreSession(): StartDestination {
        supabase.auth.awaitInitialization()
        // RM-0016: load the saved household first, so an offline start still shows its cache.
        currentHousehold.awaitRestored()
        if (supabase.auth.currentSessionOrNull() == null) {
            Log.d(TAG, "no saved session")
            return StartDestination.LOGIN
        }
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true).id }.getOrNull()
            // Signed in but the user can't be fetched (e.g. offline): still skip Login.
            ?: return StartDestination.DASHBOARD.also { Log.w(TAG, "session found but user unavailable; going to Dashboard") }

        Log.d(TAG, "saved session found; checking household")
        return try {
            if (loadHousehold(userId)) StartDestination.DASHBOARD else StartDestination.HOUSEHOLD_SETUP
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline at launch: the user is still signed in, so go to the Dashboard,
            // which shows its own error and retry.
            Log.w(TAG, "household lookup failed on restore", e)
            StartDestination.DASHBOARD
        }
    }

    /** Selects the user's first household as current; returns whether they have one. */
    private suspend fun loadHousehold(userId: String): Boolean {
        val memberships = supabase.from("household_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList<HouseholdMembershipRow>()
        Log.d(TAG, "household lookup returned ${memberships.size} membership(s)")
        // RM-0016: keep the saved household if the user still belongs to it.
        val saved = currentHousehold.id.value
        (memberships.firstOrNull { it.household_id == saved } ?: memberships.firstOrNull())
            ?.let { currentHousehold.set(it.household_id) }
        return memberships.isNotEmpty()
    }

    /** Ends the Supabase session on this device. */
    suspend fun signOut() {
        Log.i(TAG, "signOut")
        supabase.auth.signOut()
    }
}
