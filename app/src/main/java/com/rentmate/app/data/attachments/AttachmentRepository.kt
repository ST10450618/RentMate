package com.rentmate.app.data.attachments

import com.rentmate.app.data.CurrentHousehold
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The private Storage bucket from migration 0011. */
const val ATTACHMENTS_BUCKET = "attachments"

/** The second folder of an object path; the bucket policy only accepts these. */
enum class AttachmentKind(val folder: String) {
    MAINTENANCE("maintenance"),
    BILL("bill")
}

/** `<household_id>/<kind>/<uuid>.jpg`, the layout the storage.objects policies check. */
fun attachmentPath(householdId: String, kind: AttachmentKind, id: String = UUID.randomUUID().toString()): String =
    "$householdId/${kind.folder}/$id.jpg"

/** What an upload flow emits: progress from 0 to 1, then the stored path. */
sealed interface UploadProgress {
    data class Sending(val fraction: Float) : UploadProgress
    data class Done(val path: String) : UploadProgress
}

/** Bytes sent as a fraction of the total, clamped to 0..1; 0 while the total is unknown. */
fun uploadFraction(sent: Long, total: Long): Float =
    if (total <= 0) 0f else (sent.toFloat() / total).coerceIn(0f, 1f)

/**
 * RM-0028: photo storage. Like [com.rentmate.app.data.MaintenanceRepository],
 * every call talks to the network and may throw (IOException offline,
 * RestException when Storage refuses, e.g. 403 for a non-member). Callers catch
 * and rethrow CancellationException. An interface so tests can use a fake.
 */
interface AttachmentRepository {
    /** Uploads [jpeg] for the current household. The flow throws on failure. */
    fun upload(kind: AttachmentKind, jpeg: ByteArray): Flow<UploadProgress>

    /** Removes an uploaded photo, e.g. when the user takes it off the form. */
    suspend fun delete(path: String)
}

/** Supabase Storage-backed [AttachmentRepository]. */
@Singleton
class SupabaseAttachmentRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : AttachmentRepository {

    private val bucket get() = supabase.storage.from(ATTACHMENTS_BUCKET)

    override fun upload(kind: AttachmentKind, jpeg: ByteArray): Flow<UploadProgress> = flow {
        val householdId = currentHousehold.id.first() ?: throw IllegalStateException("No household selected")
        val path = attachmentPath(householdId, kind)
        val statuses = bucket.uploadAsFlow(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        emitAll(
            statuses.map { status ->
                when (status) {
                    is UploadStatus.Progress ->
                        UploadProgress.Sending(uploadFraction(status.totalBytesSend, status.contentLength))
                    is UploadStatus.Success -> UploadProgress.Done(path)
                }
            }
        )
    }

    override suspend fun delete(path: String) {
        bucket.delete(path)
    }
}
