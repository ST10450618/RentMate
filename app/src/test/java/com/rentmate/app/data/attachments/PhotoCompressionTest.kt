package com.rentmate.app.data.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RM-0028: photos are always shrunk below 1 MB before upload. */
class PhotoCompressionTest {

    /** Fake JPEG encoder: [bytesPerPixelAt100] bytes per pixel at quality 100, scaled by quality. */
    private class FakeEncoder(private val bytesPerPixelAt100: Double) {
        val calls = mutableListOf<Pair<PixelSize, Int>>()
        fun encode(size: PixelSize, quality: Int): ByteArray {
            calls += size to quality
            return ByteArray((size.width.toLong() * size.height * bytesPerPixelAt100 * quality / 100).toInt())
        }
    }

    @Test
    fun scaledSizeKeepsAspectRatioAndNeverUpscales() {
        assertEquals(PixelSize(2048, 1536), PhotoCompression.scaledSize(4032, 3024, 2048))
        assertEquals(PixelSize(1536, 2048), PhotoCompression.scaledSize(3024, 4032, 2048))
        assertEquals(PixelSize(800, 600), PhotoCompression.scaledSize(800, 600, 2048))
    }

    @Test
    fun smallPhotoIsEncodedOnceAtBestQuality() {
        val encoder = FakeEncoder(0.1)
        val bytes = PhotoCompression.compress(800, 600, encode = encoder::encode)

        assertTrue(bytes.size < PhotoCompression.MAX_BYTES)
        assertEquals(listOf(PixelSize(800, 600) to PhotoCompression.QUALITIES.first()), encoder.calls)
    }

    @Test
    fun largePhotoIsCappedAtMaxEdgeBeforeEncoding() {
        val encoder = FakeEncoder(0.1)
        PhotoCompression.compress(4032, 3024, encode = encoder::encode)

        assertEquals(PixelSize(2048, 1536), encoder.calls.first().first)
    }

    @Test
    fun lowersQualityBeforeShrinking() {
        // 2048x1536 at quality 85 is about 1.6 MB with this encoder; quality 45 fits.
        val encoder = FakeEncoder(0.6)
        val bytes = PhotoCompression.compress(4032, 3024, encode = encoder::encode)

        assertTrue("got ${bytes.size} bytes", bytes.size < PhotoCompression.MAX_BYTES)
        assertTrue(encoder.calls.size > 1)
        assertTrue(encoder.calls.all { it.first == PixelSize(2048, 1536) })
        assertEquals(PhotoCompression.QUALITIES.take(encoder.calls.size), encoder.calls.map { it.second })
    }

    @Test
    fun shrinksDimensionsWhenLowestQualityIsStillTooBig() {
        val encoder = FakeEncoder(3.0)
        val bytes = PhotoCompression.compress(4032, 3024, encode = encoder::encode)

        assertTrue(bytes.size < PhotoCompression.MAX_BYTES)
        val last = encoder.calls.last().first
        assertTrue("expected a smaller size than 2048, got $last", maxOf(last.width, last.height) < 2048)
    }

    @Test(expected = PhotoTooLargeException::class)
    fun givesUpBelowTheMinimumEdge() {
        PhotoCompression.compress(4032, 3024) { _, _ -> ByteArray(PhotoCompression.MAX_BYTES) }
    }

    @Test
    fun exactlyTheLimitIsNotAccepted() {
        var call = 0
        val bytes = PhotoCompression.compress(1000, 1000) { _, _ ->
            call++
            ByteArray(if (call == 1) PhotoCompression.MAX_BYTES else PhotoCompression.MAX_BYTES - 1)
        }
        assertEquals(PhotoCompression.MAX_BYTES - 1, bytes.size)
    }

    @Test
    fun pathAndProgressHelpers() {
        assertEquals("h1/maintenance/p1.jpg", attachmentPath("h1", AttachmentKind.MAINTENANCE, "p1"))
        assertEquals("h1/bill/p1.jpg", attachmentPath("h1", AttachmentKind.BILL, "p1"))
        assertEquals(0f, uploadFraction(10, 0))
        assertEquals(0.5f, uploadFraction(50, 100))
        assertEquals(1f, uploadFraction(150, 100))
    }
}
