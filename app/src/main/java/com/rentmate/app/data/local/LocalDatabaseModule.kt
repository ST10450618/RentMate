package com.rentmate.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val TAG = "LocalDatabaseModule"

/** Provides the Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object LocalDatabaseModule {

    /** The singleton on-disk database. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RentMateDatabase =
        Room.databaseBuilder(context, RentMateDatabase::class.java, "rentmate.db")
            .addMigrations(*ALL_MIGRATIONS)
            .build()
            .also { Log.d(TAG, "Room database built with ${ALL_MIGRATIONS.size} migrations") }

    /** Bill DAO. */
    @Provides
    fun provideBillDao(db: RentMateDatabase): BillDao = db.billDao()

    /** Chore DAO. */
    @Provides
    fun provideChoreDao(db: RentMateDatabase): ChoreDao = db.choreDao()

    /** Shopping item DAO. */
    @Provides
    fun provideShoppingItemDao(db: RentMateDatabase): ShoppingItemDao = db.shoppingItemDao()

    /** Outbox DAO. */
    @Provides
    fun provideOutboxDao(db: RentMateDatabase): OutboxDao = db.outboxDao()

    /** Household roster DAO (RM-0017). */
    @Provides
    fun provideMemberDao(db: RentMateDatabase): MemberDao = db.memberDao()

    /** Rejected-change log DAO (RM-0018). */
    @Provides
    fun provideSyncRejectionDao(db: RentMateDatabase): SyncRejectionDao = db.syncRejectionDao()
}
