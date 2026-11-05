package com.rentmate.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rentmate.app.navigation.RentMateApp
import com.rentmate.app.ui.theme.RentMateTheme
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "MainActivity"

/**
 * Single activity hosting the Compose navigation graph.
 *
 * Starts on Theme.RentMate.Starting so Android 12+ (and older versions through
 * core-splashscreen) shows the branded launch screen; installSplashScreen()
 * must run before super.onCreate() so the post-splash theme is applied.
 * Lifecycle callbacks are logged for the System Logging requirement.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()
        setContent {
            RentMateTheme {
                RentMateApp()
            }
        }
    }

    override fun onStart() { super.onStart(); Log.d(TAG, "onStart") }
    override fun onResume() { super.onResume(); Log.d(TAG, "onResume") }
    override fun onPause() { super.onPause(); Log.d(TAG, "onPause") }
    override fun onStop() { super.onStop(); Log.d(TAG, "onStop") }
    override fun onDestroy() { super.onDestroy(); Log.d(TAG, "onDestroy") }
}
