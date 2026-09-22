package com.rentmate.app.auth

import com.rentmate.app.data.CurrentHousehold
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** Whether the signed-in user already belongs to a household, decides S1's next screen (S2 vs S3). */
data class SignInResult(val hasHousehold: Boolean)

@Serializable
private data class HouseholdMembershipRow(val household_id: String)

class AuthRepository @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) {
    suspend fun signIn(): Result<SignInResult> = runCatching {
        val idToken = googleAuthManager.signIn()
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }

        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Signed in but no user id was returned")

        val memberships = supabase.from("household_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList<HouseholdMembershipRow>()

        memberships.firstOrNull()?.let { currentHousehold.set(it.household_id) }
        SignInResult(hasHousehold = memberships.isNotEmpty())
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }
}
