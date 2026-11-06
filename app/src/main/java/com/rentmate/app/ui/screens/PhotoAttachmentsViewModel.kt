package com.rentmate.app.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.R
import com.rentmate.app.data.attachments.AttachmentKind
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.PhotoCompressor
import com.rentmate.app.data.attachments.PhotoTooLargeException
import com.rentmate.app.data.attachments.PhotoUnreadableException
import com.rentmate.app.data.attachments.UploadProgress
import com.rentmate.app.ui.error.AppError
import com.rentmate.app.ui.error.ErrorMapper
import com.rentmate.app.ui.launchSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "PhotoAttachmentsVM"

/** Where one picked photo is on its way to Storage. */
sealed interface PhotoStatus {
    data object Compressing : PhotoStatus
    data class Uploading(val fraction: Float) : PhotoStatus
    data class Uploaded(val path: String) : PhotoStatus
    data class Failed(@StringRes val messageRes: Int) : PhotoStatus
}

/**
 * One photo on a form. [jpeg] is the compressed upload (null until compressed),
 * kept so Retry does not have to compress again and the row can show a preview.
 */
class PendingPhoto(
    val id: String,
    val source: String,
    val kind: AttachmentKind,
    val jpeg: ByteArray? = null,
    val status: PhotoStatus = PhotoStatus.Compressing
) {
    fun copy(jpeg: ByteArray? = this.jpeg, status: PhotoStatus = this.status) =
        PendingPhoto(id, source, kind, jpeg, status)
}

/**
 * RM-0028: picked photos for one form (a maintenance request or a bill).
 * Each photo is compressed below 1 MB, uploaded with progress, and on failure
 * keeps its compressed bytes so [retry] can try the upload again.
 */
@HiltViewModel
class PhotoAttachmentsViewModel @Inject constructor(
    private val repository: AttachmentRepository,
    private val compressor: PhotoCompressor
) : ViewModel() {

    private val _photos = MutableStateFlow<List<PendingPhoto>>(emptyList())
    val photos: StateFlow<List<PendingPhoto>> = _photos.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    /** Paths of every photo that finished uploading, in the order they were picked. */
    val uploadedPaths: List<String>
        get() = _photos.value.mapNotNull { (it.status as? PhotoStatus.Uploaded)?.path }

    /** True while any photo is still compressing or uploading. */
    val busy: Boolean
        get() = _photos.value.any { it.status is PhotoStatus.Compressing || it.status is PhotoStatus.Uploading }

    /** Adds a photo from the Photo Picker or camera and starts uploading it. */
    fun add(uri: String, kind: AttachmentKind) {
        if (_photos.value.size >= MAX_PHOTOS) return
        val photo = PendingPhoto(id = UUID.randomUUID().toString(), source = uri, kind = kind)
        _photos.update { it + photo }
        start(photo.id)
    }

    /** Tries a failed photo again; only re-compresses when compression was what failed. */
    fun retry(id: String) {
        if (_photos.value.none { it.id == id && it.status is PhotoStatus.Failed }) return
        start(id)
    }

    /** Takes a photo off the form, cancelling its upload or deleting it from Storage. */
    fun remove(id: String) {
        val photo = _photos.value.firstOrNull { it.id == id } ?: return
        jobs.remove(id)?.cancel()
        _photos.update { list -> list.filterNot { it.id == id } }
        val path = (photo.status as? PhotoStatus.Uploaded)?.path ?: return
        launchSafely(TAG) { repository.delete(path) }
    }

    /** Forgets every photo after the form was saved; the uploads stay in Storage. */
    fun clear() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _photos.value = emptyList()
    }

    private fun start(id: String) {
        jobs.remove(id)?.cancel()
        jobs[id] = viewModelScope.launch {
            try {
                val photo = _photos.value.firstOrNull { it.id == id } ?: return@launch
                val jpeg = photo.jpeg ?: run {
                    set(id) { it.copy(status = PhotoStatus.Compressing) }
                    compressor.compress(photo.source).also { bytes -> set(id) { it.copy(jpeg = bytes) } }
                }
                set(id) { it.copy(status = PhotoStatus.Uploading(0f)) }
                repository.upload(photo.kind, jpeg).collect { progress ->
                    set(id) {
                        it.copy(
                            status = when (progress) {
                                is UploadProgress.Sending -> PhotoStatus.Uploading(progress.fraction)
                                is UploadProgress.Done -> PhotoStatus.Uploaded(progress.path)
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Photo upload failed", e)
                set(id) { it.copy(status = PhotoStatus.Failed(messageFor(e))) }
            }
        }
    }

    private fun set(id: String, change: (PendingPhoto) -> PendingPhoto) {
        _photos.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    companion object {
        const val MAX_PHOTOS = 5

        @StringRes
        fun messageFor(e: Throwable): Int = when {
            e is PhotoTooLargeException -> R.string.photo_error_too_large
            e is PhotoUnreadableException -> R.string.photo_error_unreadable
            else -> when (ErrorMapper.classify(e)) {
                AppError.NO_CONNECTION, AppError.GENERIC -> R.string.photo_error_upload
                else -> ErrorMapper.classify(e).messageRes
            }
        }
    }
}
