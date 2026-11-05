package com.rentmate.app.auth

import android.util.Log
import com.rentmate.app.data.CurrentHousehold
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

@Serializable
private data class HouseholdMembershipRow(val household_id: String)

class AuthRepository @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : SessionRepository {
    suspend fun signIn(): Result<SignInResult> = runCatching {
        val idToken = googleAuthManager.signIn()
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }

        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Signed in but no user id was returned")

        SignInResult(hasHousehold = loadHousehold(userId))
    }

    /**
     * RM-0002: waits for Supabase to load the session saved on the device, then
     * picks the first screen. A saved session skips Login; the household is
     * looked up again because [CurrentHousehold] only lives in memory.
     */
    override suspend fun restoreSession(): StartDestination {
        supabase.auth.awaitInitialization()
        if (supabase.auth.currentSessionOrNull() == null) {
            Log.d(TAG, "no saved session")
            return StartDestination.LOGIN
        }
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true).id }.getOrNull()
            // Signed in but the user can't be fetched (e.g. offline): still skip Login.
            ?: return StartDestination.DASHBOARD

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
        memberships.firstOrNull()?.let { currentHousehold.set(it.household_id) }
        return memberships.isNotEmpty()
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }
}
