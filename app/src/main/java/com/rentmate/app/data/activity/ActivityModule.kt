package com.rentmate.app.data.activity

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the [ActivityRepository] the app uses. Swap the implementation here when RM-0032 lands. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityModule {

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: InMemoryActivityRepository): ActivityRepository
}
