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
 */
@HiltAndroidApp
class RentMateApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        RentMateChannel.createAll(this)
    }
}
