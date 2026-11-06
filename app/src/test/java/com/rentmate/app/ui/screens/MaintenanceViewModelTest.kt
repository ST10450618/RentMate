package com.rentmate.app.ui.screens

import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter
import com.rentmate.app.data.activity.NoOpActivityLogger
import com.rentmate.app.data.LandlordContact
import com.rentmate.app.data.MaintenanceRepository
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.Urgency
import com.rentmate.app.data.attachments.Attachment
import com.rentmate.app.data.attachments.AttachmentKind
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.EMAIL_LINK_EXPIRY
import com.rentmate.app.data.attachments.PhotoLink
import com.rentmate.app.data.attachments.UploadProgress
import com.rentmate.app.data.attachments.linksByParent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * RM-0001: being offline must show an error state on S9, never crash.
 * The fake repository throws IOException, which is what Ktor raises in airplane mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelTest {

    /** Fails every call while [offline] is true; succeeds otherwise. */
    private class FakeMaintenanceRepository(var offline: Boolean) : MaintenanceRepository {
        override val requests: StateFlow<List<MaintenanceRequest>> = MutableStateFlow(emptyList())
        var refreshCalls = 0

        private fun failIfOffline() {
            if (offline) throw IOException("Unable to resolve host")
        }

        override suspend fun refresh() {
            refreshCalls++
            failIfOffline()
        }

        override suspend fun add(request: MaintenanceRequest): MaintenanceRequest {
            failIfOffline()
            return request
        }
        override suspend fun advance(id: String) = failIfOffline()
        override suspend fun sendOpenRequests(): Int {
            failIfOffline()
            return 0
        }

        override val currentUserId: String? = "me"

        override suspend fun landlordContact(): LandlordContact? {
            failIfOffline()
            return LandlordContact(householdName = "House A")
        }

        override suspend fun saveLandlordEmail(email: String) = failIfOffline()
    }

    /** RM-0029: records links; fails while [linkFails] is true. */
    private class FakeAttachments : AttachmentRepository {
        var linkFails = false
        val linked = mutableListOf<Pair<AttachmentParent, List<String>>>()
        val stored = mutableListOf<Attachment>()

        override fun upload(kind: AttachmentKind, jpeg: ByteArray): Flow<UploadProgress> = emptyFlow()
        override suspend fun delete(path: String) = Unit
        override suspend fun link(parent: AttachmentParent, paths: List<String>) {
            if (linkFails) throw IOException("Unable to resolve host")
            linked += parent to paths
            stored += paths.map { Attachment(it, parent) }
        }
        override suspend fun list(): List<Attachment> = stored.toList()
        override suspend fun signedUrls(paths: List<String>, expiresIn: Duration) =
            paths.associateWith { "https://signed/$it?e=${expiresIn.inWholeSeconds}" }
    }

    private val feedback = FeedbackCenter()

    private fun viewModel(repository: FakeMaintenanceRepository, attachments: AttachmentRepository = FakeAttachments()) =
        MaintenanceViewModel(repository, attachments, NoOpActivityLogger, feedback)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading offline shows the load error instead of crashing`() {
        val viewModel = viewModel(FakeMaintenanceRepository(offline = true))

        assertEquals(MaintenanceError.LOAD.messageRes, viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `logging a request offline shows the save error`() {
        val viewModel = viewModel(FakeMaintenanceRepository(offline = true))

        val titleError = viewModel.log("Leaking tap", "", MaintenanceCategory.PLUMBING, Urgency.HIGH)

        assertNull(titleError)
        // RM-0052: a failed tap is a snackbar. The error card still shows the
        // offline load error from init, not the save error.
        assertEquals(MaintenanceError.LOAD.messageRes, viewModel.state.value.error)
        assertEquals(listOf(MaintenanceError.SAVE.messageRes), feedback.drain().map { it.messageRes })
    }

    /** RM-0059: a status change confirms with a snackbar naming the new status. */
    @Test
    fun `advancing online confirms the new status`() {
        val viewModel = viewModel(FakeMaintenanceRepository(offline = false))
        feedback.drain()

        viewModel.advance("any-id", "Sent")

        val message = feedback.drain().single()
        assertEquals(R.string.house_status_changed, message.messageRes)
        assertEquals(listOf<Any>("Sent"), message.formatArgs)
    }

    @Test
    fun `advancing and sending offline show their own errors`() {
        val viewModel = viewModel(FakeMaintenanceRepository(offline = true))

        viewModel.advance("any-id")
        assertEquals(listOf(MaintenanceError.UPDATE.messageRes), feedback.drain().map { it.messageRes })

        viewModel.sendOpenRequests()
        assertEquals(listOf(MaintenanceError.SEND.messageRes), feedback.drain().map { it.messageRes })
    }

    @Test
    fun `retry after reconnecting clears the error`() {
        val repository = FakeMaintenanceRepository(offline = true)
        val viewModel = viewModel(repository)
        assertEquals(MaintenanceError.LOAD.messageRes, viewModel.state.value.error)

        repository.offline = false
        viewModel.refresh()

        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun `dismissing hides the error`() {
        val viewModel = viewModel(FakeMaintenanceRepository(offline = true))

        viewModel.dismissError()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `logging a request links its uploaded photos and shows thumbnails`() {
        val attachments = FakeAttachments()
        val viewModel = viewModel(FakeMaintenanceRepository(offline = false), attachments)

        viewModel.log("Leaking tap", "", MaintenanceCategory.PLUMBING, Urgency.HIGH, listOf("h/maintenance/a.jpg"))

        val requestId = attachments.linked.single().first.id
        assertEquals(listOf("h/maintenance/a.jpg"), attachments.linked.single().second)
        assertEquals(listOf("h/maintenance/a.jpg"), viewModel.state.value.photos[requestId]?.map { it.path })
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a failed photo link shows its own error and retry links again`() {
        val attachments = FakeAttachments().apply { linkFails = true }
        val viewModel = viewModel(FakeMaintenanceRepository(offline = false), attachments)

        viewModel.log("Leaking tap", "", MaintenanceCategory.PLUMBING, Urgency.HIGH, listOf("h/maintenance/a.jpg"))
        assertEquals(MaintenanceError.LINK_PHOTOS.messageRes, viewModel.state.value.error)

        attachments.linkFails = false
        viewModel.refresh()

        assertEquals(1, attachments.linked.size)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `email links are signed for seven days and only cover the requested ids`() {
        val attachments = FakeAttachments().apply {
            stored += Attachment("h/maintenance/a.jpg", AttachmentParent.Maintenance("r1"))
            stored += Attachment("h/maintenance/b.jpg", AttachmentParent.Maintenance("r2"))
            stored += Attachment("h/bill/c.jpg", AttachmentParent.Bill("r1"))
        }
        val viewModel = viewModel(FakeMaintenanceRepository(offline = false), attachments)

        var links: Map<String, List<String>>? = null
        viewModel.photoLinksForEmail(listOf("r1")) { links = it }

        assertEquals(
            mapOf("r1" to listOf("https://signed/h/maintenance/a.jpg?e=${EMAIL_LINK_EXPIRY.inWholeSeconds}")),
            links
        )
        assertEquals(604_800L, EMAIL_LINK_EXPIRY.inWholeSeconds)
    }

    @Test
    fun `photos are grouped by parent and photos without a url are dropped`() {
        val list = listOf(
            Attachment("a", AttachmentParent.Maintenance("r1")),
            Attachment("b", AttachmentParent.Maintenance("r1")),
            Attachment("c", AttachmentParent.Bill("b1"))
        )
        val grouped = list.linksByParent(mapOf("a" to "ua", "c" to "uc"))

        assertEquals(listOf(PhotoLink("a", "ua")), grouped[AttachmentParent.Maintenance("r1")])
        assertEquals(listOf(PhotoLink("c", "uc")), grouped[AttachmentParent.Bill("b1")])
    }

    @Test
    fun `the email still opens when photo links cannot be made`() {
        val attachments = object : AttachmentRepository by FakeAttachments() {
            override suspend fun list(): List<Attachment> = throw IllegalStateException("bad response")
        }
        val viewModel = viewModel(FakeMaintenanceRepository(offline = false), attachments)

        var links: Map<String, List<String>>? = null
        viewModel.photoLinksForEmail(listOf("r1")) { links = it }

        assertEquals(emptyMap<String, List<String>>(), links)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `photo loading failures do not hide the requests`() {
        val attachments = object : AttachmentRepository by FakeAttachments() {
            override suspend fun list(): List<Attachment> = throw IOException("attachments table missing")
        }
        val viewModel = viewModel(FakeMaintenanceRepository(offline = false), attachments)

        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }
}
