package com.rentmate.app.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object LocalDatabaseModule {

    /** The singleton on-disk database. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RentMateDatabase =
        Room.databaseBuilder(context, RentMateDatabase::class.java, "rentmate.db")
            .addMigrations(MIGRATION_1_2)
            .build()

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
}
