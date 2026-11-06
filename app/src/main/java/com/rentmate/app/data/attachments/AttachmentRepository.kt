package com.rentmate.app.data.attachments

import com.rentmate.app.data.CurrentHousehold
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import javax.inject.Inject
import javax.inject.Singleton

/** The private Storage bucket from migration 0011. */
const val ATTACHMENTS_BUCKET = "attachments"

/** The second folder of an object path; the bucket policy only accepts these. */
enum class AttachmentKind(val folder: String) {
    /** A photo of a maintenance fault. */
    MAINTENANCE("maintenance"),
    /** A photo of a bill or receipt. */
    BILL("bill")
}

/** `<household_id>/<kind>/<uuid>.jpg`, the layout the storage.objects policies check. */
fun attachmentPath(householdId: String, kind: AttachmentKind, id: String = UUID.randomUUID().toString()): String =
    "$householdId/${kind.folder}/$id.jpg"

/** What an upload flow emits: progress from 0 to 1, then the stored path. */
sealed interface UploadProgress {
    /** Part of the file is sent; [fraction] runs from 0 to 1. */
    data class Sending(val fraction: Float) : UploadProgress
    /** The file is stored at [path] in [ATTACHMENTS_BUCKET]. */
    data class Done(val path: String) : UploadProgress
}

/** What a photo is attached to (RM-0029); public.attachments allows exactly one. */
sealed interface AttachmentParent {
    /** Server id of the parent row. */
    val id: String
    /** The photo belongs to the maintenance request [id]. */
    data class Maintenance(override val id: String) : AttachmentParent
    /** The photo belongs to the bill [id]. */
    data class Bill(override val id: String) : AttachmentParent
}

/** One row of public.attachments. */
data class Attachment(val path: String, val parent: AttachmentParent)

/** Thumbnails on screen: short-lived, reloaded on every refresh. */
val THUMBNAIL_URL_EXPIRY: Duration = 1.hours

/** Links in the landlord email: long enough for the landlord to open them later. */
val EMAIL_LINK_EXPIRY: Duration = 7.days

/** A stored photo and a signed URL for it. Image caches key on [path], since the URL changes. */
data class PhotoLink(val path: String, val url: String)

/**
 * Groups attachments by the request or bill they belong to, keeping upload
 * order, with the signed URL from [urls]. Photos without a URL are left out.
 */
fun List<Attachment>.linksByParent(urls: Map<String, String>): Map<AttachmentParent, List<PhotoLink>> =
    mapNotNull { a -> urls[a.path]?.let { a.parent to PhotoLink(a.path, it) } }
        .groupBy({ it.first }, { it.second })

/** Loads every attachment in the household with signed URLs, grouped by parent. */
suspend fun AttachmentRepository.loadLinks(expiresIn: Duration): Map<AttachmentParent, List<PhotoLink>> {
    val attachments = list()
    if (attachments.isEmpty()) return emptyMap()
    return attachments.linksByParent(signedUrls(attachments.map { it.path }, expiresIn))
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

    /** RM-0029: records that the uploaded [paths] belong to [parent]. */
    suspend fun link(parent: AttachmentParent, paths: List<String>)

    /** RM-0029: every attachment in the current household, oldest first. */
    suspend fun list(): List<Attachment>

    /** RM-0029: a signed URL for each path (the bucket is private), keyed by path. */
    suspend fun signedUrls(paths: List<String>, expiresIn: Duration): Map<String, String>
}

/** Insert body for public.attachments; exactly one parent id is set. */
@Serializable
private data class AttachmentInsert(
    val household_id: String,
    val storage_path: String,
    val maintenance_request_id: String? = null,
    val bill_id: String? = null
)

/** The public.attachments columns [SupabaseAttachmentRepository.list] reads. */
@Serializable
private data class AttachmentRow(
    val storage_path: String,
    val maintenance_request_id: String? = null,
    val bill_id: String? = null
)

/**
 * Supabase Storage-backed [AttachmentRepository].
 *
 * Based on: Supabase Kotlin reference, Upload a file,
 * https://supabase.com/docs/reference/kotlin/storage-from-upload (accessed 2026-11-06)
 * Based on: Supabase Kotlin reference, Create signed URLs,
 * https://supabase.com/docs/reference/kotlin/storage-from-createsignedurls (accessed 2026-11-06)
 */
@Singleton
class SupabaseAttachmentRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : AttachmentRepository {

    private val bucket get() = supabase.storage.from(ATTACHMENTS_BUCKET)

    /** Streams upload progress; `upsert = false` so a path can never overwrite another photo. */
    override fun upload(kind: AttachmentKind, jpeg: ByteArray): Flow<UploadProgress> = flow {
        val householdId = currentHousehold.id.first() ?: throw IllegalStateException("No household selected")
        val path = attachmentPath(householdId, kind)
        Log.d(TAG, "upload start: ${kind.folder}, ${jpeg.size} bytes")
        val statuses = bucket.uploadAsFlow(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        emitAll(
            statuses.map { status ->
                when (status) {
                    is UploadStatus.Progress ->
                        UploadProgress.Sending(uploadFraction(status.totalBytesSend, status.contentLength))
                    is UploadStatus.Success -> UploadProgress.Done(path).also { Log.i(TAG, "upload done: ${kind.folder}") }
                }
            }
        )
    }

    /** Deletes the object from Storage. Any public.attachments row for it must be gone already. */
    override suspend fun delete(path: String) {
        Log.d(TAG, "delete photo")
        bucket.delete(path)
    }

    /** Inserts one public.attachments row per path, all in one request. */
    override suspend fun link(parent: AttachmentParent, paths: List<String>) {
        if (paths.isEmpty()) return
        Log.d(TAG, "link ${paths.size} photo(s) to ${parent::class.simpleName}")
        val householdId = currentHousehold.id.first() ?: throw IllegalStateException("No household selected")
        supabase.from(ATTACHMENTS_TABLE).insert(
            paths.map { path ->
                AttachmentInsert(
                    household_id = householdId,
                    storage_path = path,
                    maintenance_request_id = (parent as? AttachmentParent.Maintenance)?.id,
                    bill_id = (parent as? AttachmentParent.Bill)?.id
                )
            }
        )
    }

    /** Rows with neither parent id set are skipped; the table check should prevent them. */
    override suspend fun list(): List<Attachment> {
        val householdId = currentHousehold.id.first() ?: return emptyList()
        Log.d(TAG, "list attachments")
        return supabase.from(ATTACHMENTS_TABLE)
            .select(columns = Columns.raw("storage_path, maintenance_request_id, bill_id")) {
                filter { eq("household_id", householdId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<AttachmentRow>()
            .mapNotNull { row ->
                val parent = row.maintenance_request_id?.let { AttachmentParent.Maintenance(it) }
                    ?: row.bill_id?.let { AttachmentParent.Bill(it) }
                    ?: return@mapNotNull null
                Attachment(row.storage_path, parent)
            }
            .also { Log.d(TAG, "list returned ${it.size} attachment(s)") }
    }

    /**
     * One batch call. Storage answers a missing object inside the batch with
     * signedURL = null, which storage-kt 3.1.4 cannot decode, so on a failed
     * batch each path is signed on its own and paths that still fail are left out.
     */
    override suspend fun signedUrls(paths: List<String>, expiresIn: Duration): Map<String, String> {
        val unique = paths.distinct()
        if (unique.isEmpty()) return emptyMap()
        return try {
            bucket.createSignedUrls(expiresIn, unique)
                .filter { it.error == null }
                .associate { it.path to it.signedURL }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e  // Offline: the per-path calls would fail too.
        } catch (e: Exception) {
            Log.w(TAG, "Batch signing failed, signing one by one", e)
            unique.mapNotNull { path ->
                try {
                    path to bucket.createSignedUrl(path, expiresIn)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not sign $path", e)
                    null
                }
            }.toMap()
        }
    }
}

private const val ATTACHMENTS_TABLE = "attachments"
private const val TAG = "AttachmentRepository"
