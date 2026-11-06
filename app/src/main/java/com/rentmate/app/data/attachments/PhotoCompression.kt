package com.rentmate.app.data.attachments

import kotlin.math.max
import kotlin.math.roundToInt

/** Thrown when a photo cannot be made smaller than [PhotoCompression.MAX_BYTES]. */
class PhotoTooLargeException : Exception("Photo could not be compressed below the upload limit")

/** Width and height in pixels. */
data class PixelSize(val width: Int, val height: Int)

/**
 * RM-0028: shrinks a photo until its JPEG is under [MAX_BYTES], the limit the
 * "attachments" bucket also enforces (migration 0011).
 *
 * Pure so it can be unit-tested: the caller passes [encode], which turns the
 * photo into JPEG bytes at a given size and quality. On a phone that is a
 * Bitmap scale and compress; in tests it is a fake.
 *
 * Strategy: start with the longest edge capped at [MAX_EDGE], try each of
 * [QUALITIES] from best to worst, and if none fit, shrink the edge by
 * [SCALE_STEP] and try again, down to [MIN_EDGE].
 */
object PhotoCompression {
    /** Upload limit in bytes; matches the bucket's file size limit. */
    const val MAX_BYTES = 1_000_000
    /** Longest edge of the first attempt, in pixels. */
    const val MAX_EDGE = 2048
    /** Smallest longest edge tried before giving up. */
    const val MIN_EDGE = 320
    /** Factor the edge shrinks by after every quality fails. */
    const val SCALE_STEP = 0.75
    /** JPEG qualities tried at each size, best first. */
    val QUALITIES = listOf(85, 75, 65, 55, 45)

    /** Fits [width] x [height] inside a [maxEdge] square, keeping the aspect ratio and never upscaling. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int): PixelSize {
        require(width > 0 && height > 0) { "Photo has no pixels" }
        val longest = max(width, height)
        if (longest <= maxEdge) return PixelSize(width, height)
        val scale = maxEdge.toDouble() / longest
        return PixelSize(
            max(1, (width * scale).roundToInt()),
            max(1, (height * scale).roundToInt())
        )
    }

    /**
     * Returns the first encoding smaller than [maxBytes].
     * @throws PhotoTooLargeException when even [MIN_EDGE] at the lowest quality is too big.
     */
    fun compress(
        width: Int,
        height: Int,
        maxBytes: Int = MAX_BYTES,
        maxEdge: Int = MAX_EDGE,
        encode: (size: PixelSize, quality: Int) -> ByteArray
    ): ByteArray {
        var edge = minOf(maxEdge, max(width, height))
        while (true) {
            val size = scaledSize(width, height, edge)
            for (quality in QUALITIES) {
                val bytes = encode(size, quality)
                if (bytes.size < maxBytes) return bytes
            }
            if (edge <= MIN_EDGE) throw PhotoTooLargeException()
            edge = max(MIN_EDGE, (edge * SCALE_STEP).toInt())
        }
    }
}
