package com.rentmate.app.ui.screens

import com.rentmate.app.R
import com.rentmate.app.data.attachments.AttachmentKind
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.PhotoCompressor
import com.rentmate.app.data.attachments.PhotoTooLargeException
import com.rentmate.app.data.attachments.UploadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** RM-0028: upload progress is shown, failures offer Retry, and Retry does not compress again. */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoAttachmentsViewModelTest {

    private class FakeCompressor : PhotoCompressor {
        var calls = 0
        var tooLarge = false
        override suspend fun compress(uri: String): ByteArray {
            calls++
            if (tooLarge) throw PhotoTooLargeException()
            return ByteArray(500_000)
        }
    }

    private class FakeAttachments : AttachmentRepository {
        var offline = false
        val uploads = mutableListOf<Pair<AttachmentKind, Int>>()
        val deleted = mutableListOf<String>()

        override fun upload(kind: AttachmentKind, jpeg: ByteArray): Flow<UploadProgress> = flow {
            uploads += kind to jpeg.size
            emit(UploadProgress.Sending(0.5f))
            if (offline) throw IOException("Unable to resolve host")
            emit(UploadProgress.Done("h1/${kind.folder}/photo${uploads.size}.jpg"))
        }

        override suspend fun delete(path: String) {
            deleted += path
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a picked photo is compressed and uploaded`() {
        val repo = FakeAttachments()
        val vm = PhotoAttachmentsViewModel(repo, FakeCompressor())

        vm.add("content://photo/1", AttachmentKind.MAINTENANCE)

        assertEquals(listOf(AttachmentKind.MAINTENANCE to 500_000), repo.uploads)
        assertEquals(PhotoStatus.Uploaded("h1/maintenance/photo1.jpg"), vm.photos.value.single().status)
        assertEquals(listOf("h1/maintenance/photo1.jpg"), vm.uploadedPaths)
        assertFalse(vm.busy)
    }

    @Test
    fun `progress is shown while the upload is running`() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = object : AttachmentRepository {
            override fun upload(kind: AttachmentKind, jpeg: ByteArray): Flow<UploadProgress> = flow {
                emit(UploadProgress.Sending(0.4f))
                gate.await()
                emit(UploadProgress.Done("h1/bill/x.jpg"))
            }
            override suspend fun delete(path: String) = Unit
        }
        val vm = PhotoAttachmentsViewModel(repo, FakeCompressor())

        vm.add("content://photo/1", AttachmentKind.BILL)
        assertEquals(PhotoStatus.Uploading(0.4f), vm.photos.value.single().status)
        assertTrue(vm.busy)

        gate.complete(Unit)
        assertEquals(PhotoStatus.Uploaded("h1/bill/x.jpg"), vm.photos.value.single().status)
    }

    @Test
    fun `an offline upload fails with retry, and retry reuses the compressed bytes`() {
        val repo = FakeAttachments().apply { offline = true }
        val compressor = FakeCompressor()
        val vm = PhotoAttachmentsViewModel(repo, compressor)

        vm.add("content://photo/1", AttachmentKind.BILL)
        assertEquals(PhotoStatus.Failed(R.string.photo_error_upload), vm.photos.value.single().status)
        assertTrue(vm.uploadedPaths.isEmpty())

        repo.offline = false
        vm.retry(vm.photos.value.single().id)

        assertEquals(1, compressor.calls)
        assertEquals(2, repo.uploads.size)
        assertTrue(vm.photos.value.single().status is PhotoStatus.Uploaded)
    }

    @Test
    fun `a photo that cannot be shrunk says so`() {
        val vm = PhotoAttachmentsViewModel(FakeAttachments(), FakeCompressor().apply { tooLarge = true })

        vm.add("content://photo/1", AttachmentKind.MAINTENANCE)

        assertEquals(PhotoStatus.Failed(R.string.photo_error_too_large), vm.photos.value.single().status)
    }

    @Test
    fun `removing an uploaded photo deletes it from storage`() {
        val repo = FakeAttachments()
        val vm = PhotoAttachmentsViewModel(repo, FakeCompressor())
        vm.add("content://photo/1", AttachmentKind.MAINTENANCE)

        vm.remove(vm.photos.value.single().id)

        assertTrue(vm.photos.value.isEmpty())
        assertEquals(listOf("h1/maintenance/photo1.jpg"), repo.deleted)
    }

    @Test
    fun `no more than the maximum photos are accepted`() {
        val vm = PhotoAttachmentsViewModel(FakeAttachments(), FakeCompressor())
        repeat(PhotoAttachmentsViewModel.MAX_PHOTOS + 2) { vm.add("content://photo/$it", AttachmentKind.MAINTENANCE) }

        assertEquals(PhotoAttachmentsViewModel.MAX_PHOTOS, vm.photos.value.size)
    }
}
