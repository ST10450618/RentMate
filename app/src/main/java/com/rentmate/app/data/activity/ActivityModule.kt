package com.rentmate.app.data.activity

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the [ActivityRepository] the app uses. The feed reads from Firestore (RM-0032); [InMemoryActivityRepository] stays for tests. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityModule {

    /** The feed screen reads events from Firestore. */
    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: FirestoreActivityRepository): ActivityRepository

    /** Repositories write events to Firestore. */
    @Binds
    @Singleton
    abstract fun bindActivityLogger(impl: FirestoreActivityLogger): ActivityLogger
}
