package com.rentmate.app.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds [SessionRepository] to the Supabase-backed [AuthRepository]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun bindSessionRepository(impl: AuthRepository): SessionRepository
}
