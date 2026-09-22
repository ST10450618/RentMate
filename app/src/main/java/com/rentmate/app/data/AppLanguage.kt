package com.rentmate.app.data

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.annotation.StringRes
import com.rentmate.app.R

private const val TAG = "AppLanguage"

/**
 * The languages RentMate ships (US-4). Two of the three are official South
 * African languages, which is the module requirement.
 *
 * The tag is a BCP-47 language tag and must match the resource folder suffix:
 * "af" -> values-af, "xh" -> values-xh, English is the default in values/.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    ENGLISH("en", R.string.lang_english),
    AFRIKAANS("af", R.string.lang_afrikaans),
    XHOSA("xh", R.string.lang_xhosa);

    companion object {
        /** Falls back to English for a null or unrecognised tag. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

/**
 * Applies the chosen language at runtime.
 *
 * Android 13 (API 33) and above exposes per-app languages through LocaleManager,
 * so the choice survives restarts and appears in system settings. Below 33 the
 * preference is still stored, but applying it needs AppCompat's
 * setApplicationLocales plus a manifest service entry - deferred to the final
 * POE, where multi-language support is actually assessed.
 */
fun applyLanguage(context: Context, language: AppLanguage) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
        Log.d(TAG, "applied locale ${language.tag} via LocaleManager")
    } else {
        Log.d(TAG, "stored locale ${language.tag}; runtime switch needs AppCompat on API < 33")
    }
}
