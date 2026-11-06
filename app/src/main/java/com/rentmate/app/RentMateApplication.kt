package com.rentmate.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rentmate.app.notifications.RentMateChannel
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

private const val TAG = "RentMateApplication"

/**
 * Application entry point. Hilt builds the dependency graph here, and the
 * notification channels are registered before any screen or push can need
 * them (RM-0021).
 *
 * RM-0018: WorkManager is initialised on demand with [HiltWorkerFactory], so
 * the sync worker gets its dependencies from Hilt. The default initializer is
 * removed in the manifest.
 *
 * Based on: Android Developers, Custom WorkManager configuration and initialization,
 * https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration (accessed 2026-11-06)
 * Based on: Android Developers, Use Hilt with other Jetpack libraries,
 * https://developer.android.com/training/dependency-injection/hilt-jetpack (accessed 2026-11-06)
 */
@HiltAndroidApp
class RentMateApplication : Application(), Configuration.Provider {

    /** Builds workers with Hilt-injected constructors. */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** Read by WorkManager the first time it is used, because on-demand initialisation is on. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    /** Registers the notification channels once per process start. */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        RentMateChannel.createAll(this)
    }
}
