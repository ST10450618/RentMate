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

    @Binds
    @Singleton
    abstract fun bindMaintenanceRepository(impl: SupabaseMaintenanceRepository): MaintenanceRepository

    @Binds
    @Singleton
    abstract fun bindAttachmentRepository(impl: SupabaseAttachmentRepository): AttachmentRepository

    @Binds
    @Singleton
    abstract fun bindPhotoCompressor(impl: AndroidPhotoCompressor): PhotoCompressor
}
