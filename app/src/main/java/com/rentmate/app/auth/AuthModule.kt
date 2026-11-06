package com.rentmate.app.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module that binds [SessionRepository] to the Supabase-backed [AuthRepository].
 * [StartupViewModel] depends on the interface, so tests can pass in a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    /** Provides the app-wide [AuthRepository] wherever code injects a [SessionRepository]. */
    @Binds
    abstract fun bindSessionRepository(impl: AuthRepository): SessionRepository
}
