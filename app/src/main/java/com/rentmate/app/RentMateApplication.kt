package com.rentmate.app

import android.app.Application
import android.util.Log
import com.rentmate.app.notifications.RentMateChannel
import dagger.hilt.android.HiltAndroidApp

private const val TAG = "RentMateApplication"

/**
 * Application entry point. Hilt builds the dependency graph here, and the
 * notification channels are registered before any screen or push can need
 * them (RM-0021).
 */
@HiltAndroidApp
class RentMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        RentMateChannel.createAll(this)
    }
}
