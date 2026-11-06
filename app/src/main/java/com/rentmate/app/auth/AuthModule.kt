package com.rentmate.app.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module that binds [SessionRepository] to the Supabase-backed [AuthRepository],
 * so [StartupViewModel] depends on the interface and tests can swap in a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    /** Hands out the app-wide [AuthRepository] wherever a [SessionRepository] is injected. */
    @Binds
    abstract fun bindSessionRepository(impl: AuthRepository): SessionRepository
}
