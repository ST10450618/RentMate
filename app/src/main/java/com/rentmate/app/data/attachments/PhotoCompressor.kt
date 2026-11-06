package com.rentmate.app.data.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The picked file could not be opened or is not an image. Nothing was uploaded. */
class PhotoUnreadableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Turns a picked or captured photo into upload-ready JPEG bytes under
 * [PhotoCompression.MAX_BYTES]. An interface so view model tests can fake it.
 */
interface PhotoCompressor {
    /**
     * @param uri a content:// URI from the Photo Picker or the camera's FileProvider file.
     * @throws PhotoUnreadableException when the photo cannot be read.
     * @throws PhotoTooLargeException when it cannot be made small enough.
     */
    suspend fun compress(uri: String): ByteArray
}

/** Decodes with BitmapFactory, applies the EXIF rotation, then runs [PhotoCompression]. */
@Singleton
class AndroidPhotoCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) : PhotoCompressor {

    override suspend fun compress(uri: String): ByteArray = withContext(Dispatchers.Default) {
        val bitmap = try {
            decodeUpright(uri.toUri())
        } catch (e: PhotoUnreadableException) {
            throw e
        } catch (e: IOException) {
            throw PhotoUnreadableException("Cannot read $uri", e)
        } catch (e: SecurityException) {
            throw PhotoUnreadableException("No permission to read $uri", e)
        } catch (e: OutOfMemoryError) {
            throw PhotoUnreadableException("Not enough memory to decode $uri", e)
        }
        try {
            PhotoCompression.compress(bitmap.width, bitmap.height) { size, quality ->
                val scaled = if (size.width == bitmap.width && size.height == bitmap.height) bitmap
                else bitmap.scale(size.width, size.height)
                try {
                    ByteArrayOutputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        out.toByteArray()
                    }
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Decodes at roughly [PhotoCompression.MAX_EDGE] (a 12 MP photo would not fit in memory at full size). */
    private fun decodeUpright(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw PhotoUnreadableException("Cannot open $uri")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoUnreadableException("Not an image: $uri")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PhotoCompression.MAX_EDGE) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw PhotoUnreadableException("Cannot decode $uri")

        val degrees = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) return decoded

        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }
}
