package com.rentmate.app.data

import com.rentmate.app.data.attachments.AndroidPhotoCompressor
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.PhotoCompressor
import com.rentmate.app.data.attachments.SupabaseAttachmentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds repository interfaces to their Supabase implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /** Maintenance requests go straight to Supabase (no offline cache). */
    @Binds
    @Singleton
    abstract fun bindMaintenanceRepository(impl: SupabaseMaintenanceRepository): MaintenanceRepository

    /** RM-0028: photos in Supabase Storage. */
    @Binds
    @Singleton
    abstract fun bindAttachmentRepository(impl: SupabaseAttachmentRepository): AttachmentRepository

    /** RM-0028: Bitmap-based JPEG compression before upload. */
    @Binds
    @Singleton
    abstract fun bindPhotoCompressor(impl: AndroidPhotoCompressor): PhotoCompressor

    /** RM-0016: saved household selection. */
    @Binds
    @Singleton
    abstract fun bindHouseholdIdStore(impl: DataStoreHouseholdIdStore): HouseholdIdStore

    /** RM-0016: Supabase reads behind the Room-backed repositories. */
    @Binds
    @Singleton
    abstract fun bindBillsRemoteSource(impl: SupabaseBillsRemoteSource): BillsRemoteSource

    /** RM-0016: Supabase chore reads. */
    @Binds
    @Singleton
    abstract fun bindChoresRemoteSource(impl: SupabaseChoresRemoteSource): ChoresRemoteSource

    /** RM-0016: Supabase shopping item reads. */
    @Binds
    @Singleton
    abstract fun bindShoppingRemoteSource(impl: SupabaseShoppingRemoteSource): ShoppingRemoteSource

    /** RM-0016: bills read from Room, refreshed from Supabase. */
    @Binds
    @Singleton
    abstract fun bindBillsRepository(impl: CachedBillsRepository): BillsRepository

    /** RM-0016: chores read from Room, refreshed from Supabase. */
    @Binds
    @Singleton
    abstract fun bindChoresRepository(impl: CachedChoresRepository): ChoresRepository

    /** RM-0016: shopping items read from Room, refreshed from Supabase. */
    @Binds
    @Singleton
    abstract fun bindShoppingRepository(impl: CachedShoppingRepository): ShoppingRepository
}
