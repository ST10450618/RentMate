package com.rentmate.app.ui.screens

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ProfileSettingsSync
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.data.UserSettings
import com.rentmate.app.data.applyLanguage
import com.rentmate.app.data.local.LocalCache
import com.rentmate.app.notifications.DeviceTokenRegistrar
import com.rentmate.app.ui.launchSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

/**
 * The profiles column for each toggle. The send-push Edge Function reads the
 * notify_* columns to skip users who turned a channel off, so these names must
 * match supabase/functions/send-push/push.ts (checked by NotificationToggleTest).
 */
internal val TOGGLE_COLUMN = mapOf(
    SettingToggle.NOTIFY_BILLS to "notify_bills",
    SettingToggle.NOTIFY_CHORES to "notify_chores",
    SettingToggle.NOTIFY_SHOPPING to "notify_shopping_list",
    SettingToggle.NOTIFY_MAINTENANCE to "notify_maintenance",
    SettingToggle.BIOMETRIC_UNLOCK to "biometric_enabled",
    SettingToggle.SHOW_LEADERBOARD to "show_leaderboard"
)

/**
 * RM-0052: applies a setting locally so the switch moves at once, then saves it
 * to the server. If the save fails, [revert] puts the old value back (the switch
 * flips back) and the user gets a snackbar saying why. Cancellation is not a failure.
 */
internal suspend fun saveWithRollback(
    apply: suspend () -> Unit,
    save: suspend () -> Unit,
    revert: suspend () -> Unit,
    @StringRes failedRes: Int,
    feedback: FeedbackCenter
) {
    apply()
    try {
        save()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "setting not saved: ${e.message}")
        runCatching { revert() }.onFailure { Log.e(TAG, "could not revert setting", it) }
        feedback.show(failedRes)
    }
}

/** RM-0056: what the Settings screen shows while the saved settings load. */
sealed interface SettingsLoad {
    /** The settings file has not been read yet. Shows LoadingState. */
    data object Loading : SettingsLoad

    /** The settings are on screen. */
    data object Ready : SettingsLoad

    /** The settings file could not be read. Shows ErrorState with Retry. */
    data object Failed : SettingsLoad
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val localCache: LocalCache,
    private val profileSettingsSync: ProfileSettingsSync,
    private val deviceTokenRegistrar: DeviceTokenRegistrar,
    private val feedback: FeedbackCenter
) : ViewModel() {

    private val repository = SettingsRepository(context)

    private val reloads = MutableStateFlow(0)
    private val _syncFailed = MutableStateFlow(false)

    /**
     * True when the last read of the profile failed. The screen then shows an
     * InlineError above the settings saved on this phone, with Retry.
     */
    val syncFailed: StateFlow<Boolean> = _syncFailed

    /** RM-0056: drives LoadingState and ErrorState on the Settings screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val load: StateFlow<SettingsLoad> = reloads.flatMapLatest {
        repository.settings
            .map<UserSettings, SettingsLoad> { SettingsLoad.Ready }
            .onStart { emit(SettingsLoad.Loading) }
            .catch { e ->
                Log.e(TAG, "could not read saved settings", e)
                emit(SettingsLoad.Failed)
            }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsLoad.Loading)

    init {
        pullFromProfile()
    }

    /** RM-0005: pick up changes made on another device since sign-in. */
    fun pullFromProfile() {
        Log.d(TAG, "pullFromProfile")
        launchSafely(TAG, onError = { _syncFailed.value = true }) {
            _syncFailed.value = !profileSettingsSync.pull()
        }
    }

    /** Called by the ErrorState's Retry button after the settings file failed to load. */
    fun reload() {
        Log.d(TAG, "reload")
        reloads.value++
    }

    val state: StateFlow<UserSettings> = repository.settings
        .catch { Log.e(TAG, "settings flow failed", it) }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    fun setToggle(toggle: SettingToggle, value: Boolean) {
        Log.d(TAG, "setToggle ${toggle.name} = $value")
        launchSafely(TAG) {
            saveWithRollback(
                apply = { repository.setToggle(toggle, value) },
                save = {
                    syncBoolean(TOGGLE_COLUMN.getValue(toggle), value)
                    if (toggle == SettingToggle.SHOW_LEADERBOARD) syncHouseholdLeaderboard(!value)
                },
                revert = { repository.setToggle(toggle, !value) },
                failedRes = R.string.feedback_setting_failed,
                feedback = feedback
            )
        }
    }

    fun setLanguage(language: AppLanguage) {
        Log.d(TAG, "setLanguage ${language.tag}")
        val previous = state.value.language
        launchSafely(TAG) {
            // RM-0026: save, then switch the UI straight away (this recreates the
            // activity; the ViewModel and this coroutine survive that), then write
            // it to the profile so it follows the user to other devices.
            // RM-0052: if the profile write fails, go back to the old language.
            saveWithRollback(
                apply = {
                    repository.setLanguage(language)
                    applyLanguage(language)
                },
                save = { syncString("preferred_language", language.tag) },
                revert = {
                    repository.setLanguage(previous)
                    applyLanguage(previous)
                },
                failedRes = R.string.feedback_language_failed,
                feedback = feedback
            )
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            // RM-0021: stop this device getting the account's pushes. This must run
            // while the session is still valid. Offline it gives up after 5 s and
            // sign-out goes ahead.
            withTimeoutOrNull(5_000) { deviceTokenRegistrar.unregisterCurrent() }
            runCatching { supabase.auth.signOut() }
                .onFailure {
                    Log.w(TAG, "sign-out failed: ${it.message}")
                    // The device is still signed out; tell the user the server did not hear about it.
                    feedback.show(R.string.feedback_sign_out_partial)
                }
            // RM-0016: also removes the saved household id from disk.
            currentHousehold.forget()
            // PRIVACY.md: the cached household data is removed on sign-out.
            runCatching { localCache.clear() }
                .onFailure { Log.e(TAG, "could not clear local cache on sign-out", it) }
            onSignedOut()
        }
    }

    // The sync functions below throw on failure; saveWithRollback undoes the local change.

    /** US-3: preferences follow the user to a new device via the profiles table. */
    private suspend fun syncBoolean(column: String, value: Boolean) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        supabase.from("profiles").update({ set(column, value) }) {
            filter { eq("id", userId) }
        }
    }

    /** RM-0008: leaderboard visibility is shared by the whole household. */
    private suspend fun syncHouseholdLeaderboard(hide: Boolean) {
        val householdId = currentHousehold.id.first() ?: return
        supabase.from("households").update({ set("hide_leaderboard", hide) }) {
            filter { eq("id", householdId) }
        }
    }

    private suspend fun syncString(column: String, value: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        supabase.from("profiles").update({ set(column, value) }) {
            filter { eq("id", userId) }
        }
    }
}
