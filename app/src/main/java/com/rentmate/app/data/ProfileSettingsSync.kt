package com.rentmate.app.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProfileSettingsSync"

/** The one `households` column the leaderboard toggle reads. */
@Serializable
private data class HouseholdHideRow(val hide_leaderboard: Boolean = false)

/**
 * RM-0005 / US-3: settings follow the user to a new device.
 *
 * Every change is already written to the caller's `profiles` row by
 * SettingsViewModel. This class does the other half: on sign-in and when Settings
 * opens it reads that row back and seeds the local DataStore, so device B shows
 * what was chosen on device A.
 *
 * Offline-first: if the read fails, the local values stay as they are.
 */
@Singleton
class ProfileSettingsSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) {
    private val repository = SettingsRepository(context)

    /** Returns true when the profile was read and the local settings were updated. */
    suspend fun pull(): Boolean = try {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.d(TAG, "pull skipped: not signed in")
            false
        } else {
            val profile = supabase.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingle<ProfileRow>()
            val toggles = profileToggles(profile) + householdLeaderboardToggle()
            val language = AppLanguage.fromTag(profile.preferred_language)

            val previousLanguage = repository.settings.first().language
            repository.seed(toggles, language)
            // RM-0026: recreates the activity in the profile's language if it changed.
            if (language != previousLanguage) applyLanguage(language)

            Log.d(TAG, "pulled ${toggles.size} toggles and language ${language.tag} from profile")
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "could not read settings from profile; keeping local values", e)
        false
    }

    /** RM-0008: leaderboard visibility is household-wide, so it comes from the household row. */
    private suspend fun householdLeaderboardToggle(): Map<SettingToggle, Boolean> {
        val householdId = currentHousehold.id.first() ?: return emptyMap()
        val row = supabase.from("households")
            .select(columns = Columns.raw("hide_leaderboard")) { filter { eq("id", householdId) } }
            .decodeSingleOrNull<HouseholdHideRow>()
            ?: return emptyMap()
        return mapOf(SettingToggle.SHOW_LEADERBOARD to !row.hide_leaderboard)
    }
}

/**
 * The per-user toggles stored on `profiles`.
 *
 * Biometric unlock is left out on purpose: it depends on what the phone in hand
 * has enrolled, so turning it on for device A must not lock device B. The
 * leaderboard toggle is household-wide and comes from `households` instead.
 */
internal fun profileToggles(profile: ProfileRow): Map<SettingToggle, Boolean> = mapOf(
    SettingToggle.NOTIFY_BILLS to profile.notify_bills,
    SettingToggle.NOTIFY_CHORES to profile.notify_chores,
    SettingToggle.NOTIFY_SHOPPING to profile.notify_shopping_list,
    SettingToggle.NOTIFY_MAINTENANCE to profile.notify_maintenance
)
