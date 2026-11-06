package com.rentmate.app.data.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** RM-0018: binds the sync scheduler to WorkManager. RM-0019: binds the network and status sources. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    /** The app schedules uploads through WorkManager. */
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    /** The app follows the network through ConnectivityManager. */
    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: AndroidConnectivityMonitor): ConnectivityMonitor

    /** The UI reads sync state from the one [SyncStatusTracker]. */
    @Binds
    abstract fun bindSyncStatusSource(impl: SyncStatusTracker): SyncStatusSource
}
