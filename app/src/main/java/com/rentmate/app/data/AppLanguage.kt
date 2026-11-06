package com.rentmate.app.data

import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rentmate.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "AppLanguage"

/**
 * The languages RentMate ships (US-4). Two of the three are official South
 * African languages, which is the module requirement.
 *
 * The tag is a BCP-47 language tag and must match the resource folder suffix:
 * "af" -> values-af, "xh" -> values-xh, English is the default in values/.
 * [labelRes] is the language's name in that language (an endonym), so a user
 * can always find their own language in the picker whatever the app shows now.
 *
 * Based on: Android Developers, Per-app language preferences,
 * https://developer.android.com/guide/topics/resources/app-languages (accessed 2026-11-06)
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    /** English, the default in values/. */
    ENGLISH("en", R.string.lang_english),
    /** Afrikaans, in values-af/. */
    AFRIKAANS("af", R.string.lang_afrikaans),
    /** isiXhosa, in values-xh/. */
    XHOSA("xh", R.string.lang_xhosa);

    companion object {
        /** Falls back to English for a null or unrecognised tag. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH

        /**
         * Maps a comma-separated locale list (as from
         * `LocaleListCompat.toLanguageTags()`) to the first language RentMate
         * ships, matching on the language subtag only, so "af-ZA" is Afrikaans.
         *
         * Returns null when the list is empty (the app follows the system
         * language) or contains nothing RentMate supports.
         */
        fun fromLocaleTags(tags: String?): AppLanguage? =
            tags.orEmpty()
                .split(',')
                .map { it.trim().substringBefore('-').substringBefore('_').lowercase() }
                .firstNotNullOfOrNull { primary -> entries.firstOrNull { it.tag == primary } }
    }
}

/** The language chosen for this app on this device, or null when it follows the system. */
fun appliedLanguage(): AppLanguage? =
    AppLanguage.fromLocaleTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

/**
 * RM-0026: switches the app to [language] right away.
 *
 * AppCompatDelegate.setApplicationLocales works on every supported API level:
 * - Android 13+ (API 33): it calls the platform LocaleManager, which stores the
 *   choice itself and shows it under the app's language in system settings.
 * - Android 8 to 12 (API 26-32): AppCompat applies it to every AppCompatActivity
 *   and, with AppLocalesMetadataHolderService autoStoreLocales in the manifest,
 *   stores it so it is applied again before the first frame after a restart.
 * Either way the running activity is recreated, so every screen picks up the new
 * strings. It must run on the main thread.
 */
suspend fun applyLanguage(language: AppLanguage) = withContext(Dispatchers.Main.immediate) {
    if (appliedLanguage() == language) return@withContext
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    Log.d(TAG, "applied locale ${language.tag}")
}

/**
 * Called once at startup. DataStore holds what the user picked (and what came
 * from their profile), the platform/AppCompat holds what is applied; this keeps
 * the two in step:
 * - nothing applied but a stored choice (e.g. the profile was pulled while the
 *   app was closed, or data restored to a new phone): apply the stored choice;
 * - a different language applied (changed in Android 13+ system settings):
 *   store it, so the Settings picker shows the language actually on screen.
 * If the user never chose a language, the app follows the phone's language.
 */
suspend fun reconcileLanguage(repository: SettingsRepository) {
    val stored = repository.storedLanguageTag.first()?.let(AppLanguage::fromTag)
    val applied = withContext(Dispatchers.Main.immediate) { appliedLanguage() }
    Log.d(TAG, "reconcile: stored=${stored?.tag} applied=${applied?.tag}")
    when {
        applied != null && applied != stored -> repository.setLanguage(applied)
        applied == null && stored != null -> applyLanguage(stored)
    }
}
