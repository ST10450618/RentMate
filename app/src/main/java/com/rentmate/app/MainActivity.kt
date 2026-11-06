package com.rentmate.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rentmate.app.auth.BiometricLockHost
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.data.reconcileLanguage
import com.rentmate.app.navigation.RentMateApp
import com.rentmate.app.ui.theme.RentMateTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"
private const val PREFS = "rentmate_launch"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notification_permission"

/**
 * Single activity hosting the Compose navigation graph.
 *
 * Starts on Theme.RentMate.Starting so Android 12+ (and older versions through
 * core-splashscreen) shows the branded launch screen; installSplashScreen()
 * must run before super.onCreate() so the post-splash theme is applied.
 * Lifecycle callbacks are logged for the System Logging requirement.
 *
 * On Android 13+ it asks once for the notification permission (RM-0021). The
 * request is made a single time per install; if the user declines, the app keeps
 * working and they can enable notifications later in system settings.
 *
 * It is an AppCompatActivity (RM-0026) so AppCompatDelegate.setApplicationLocales
 * can switch the language in-app on Android 8-12 as well as 13+.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()
        maybeAskForNotificationPermission()
        // RM-0026: re-apply the saved language if it is not the one in use. Only
        // on a fresh start, not when the activity is recreated by a language switch.
        if (savedInstanceState == null) {
            lifecycleScope.launch { reconcileLanguage(SettingsRepository(applicationContext)) }
        }
        setContent {
            RentMateTheme {
                BiometricLockHost { RentMateApp() }
            }
        }
    }

    /** Asks for POST_NOTIFICATIONS once, on Android 13+ only, if not already granted. */
    private fun maybeAskForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (granted || prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        Log.d(TAG, "Requesting POST_NOTIFICATIONS")
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStart() { super.onStart(); Log.d(TAG, "onStart") }
    override fun onResume() { super.onResume(); Log.d(TAG, "onResume") }
    override fun onPause() { super.onPause(); Log.d(TAG, "onPause") }
    override fun onStop() { super.onStop(); Log.d(TAG, "onStop") }
    override fun onDestroy() { super.onDestroy(); Log.d(TAG, "onDestroy") }
}
