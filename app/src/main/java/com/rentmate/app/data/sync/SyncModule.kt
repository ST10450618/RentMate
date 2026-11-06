package com.rentmate.app.data.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** RM-0018: binds the sync scheduler to WorkManager. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    /** The app schedules uploads through WorkManager. */
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
