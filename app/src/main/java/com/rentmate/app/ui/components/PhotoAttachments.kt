package com.rentmate.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rentmate.app.data.attachments.PhotoLink
import androidx.core.content.FileProvider
import com.rentmate.app.R
import com.rentmate.app.ui.screens.PendingPhoto
import com.rentmate.app.ui.screens.PhotoStatus
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import java.io.File
import java.util.UUID

/**
 * RM-0028: "Choose photo" (Android Photo Picker, no storage permission) and
 * "Take photo" (camera app writing to a FileProvider file, no CAMERA
 * permission), then one [PhotoUploadRow] per photo with progress and Retry.
 *
 * Stateless: the photos and their upload state come from
 * [com.rentmate.app.ui.screens.PhotoAttachmentsViewModel].
 */
@Composable
fun PhotoAttachmentPicker(
    photos: List<PendingPhoto>,
    onPicked: (uri: String) -> Unit,
    onRetry: (id: String) -> Unit,
    onRemove: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    maxPhotos: Int = 5
) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri.toString())
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) onPicked(uri)
        cameraUri = null
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        photos.forEach { photo ->
            PhotoUploadRow(photo = photo, onRetry = { onRetry(photo.id) }, onRemove = { onRemove(photo.id) })
        }
        if (photos.size < maxPhotos) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.photo_choose), modifier = Modifier.padStart())
                }
                OutlinedButton(
                    onClick = {
                        val uri = newCameraUri(context)
                        cameraUri = uri.toString()
                        takePhoto.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(stringResource(R.string.photo_take), modifier = Modifier.padStart())
                }
            }
        }
    }
}

/** One photo: preview, upload progress, and Retry when the upload failed. */
@Composable
fun PhotoUploadRow(photo: PendingPhoto, onRetry: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val jpeg = photo.jpeg
    val preview = remember(jpeg) {
        jpeg?.let {
            runCatching {
                BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = 8 })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.thumbnail)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = stringResource(R.string.photo_preview_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Sizes.thumbnail)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when (val status = photo.status) {
                PhotoStatus.Compressing -> {
                    Text(stringResource(R.string.photo_status_preparing), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is PhotoStatus.Uploading -> {
                    val percent = (status.fraction * 100).toInt()
                    Text(stringResource(R.string.photo_status_uploading, percent), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
                }
                is PhotoStatus.Uploaded -> {
                    val kb = ((jpeg?.size ?: 0) + 512) / 1024
                    Text(stringResource(R.string.photo_status_uploaded, kb), style = MaterialTheme.typography.bodySmall)
                }
                is PhotoStatus.Failed -> {
                    Text(
                        stringResource(status.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.photo_remove))
        }
    }
}

/**
 * RM-0029: a row of saved photos for a maintenance request or bill. Tapping one
 * opens it full size. Images load from signed URLs but cache by storage path,
 * so a new URL after each refresh does not download the photo again.
 */
@Composable
fun AttachmentThumbnails(photos: List<PhotoLink>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf<PhotoLink?>(null) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        photos.forEachIndexed { index, photo ->
            AsyncImage(
                model = photo.imageRequest(),
                contentDescription = stringResource(R.string.photo_thumbnail_description, index + 1, photos.size),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Sizes.thumbnailLarge)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { open = photo }
            )
        }
    }
    open?.let { photo ->
        Dialog(onDismissRequest = { open = null }) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TextButton(onClick = { open = null }) { Text(stringResource(R.string.close)) }
                AsyncImage(
                    model = photo.imageRequest(),
                    contentDescription = stringResource(R.string.photo_full_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = Sizes.photoPreviewMax)
                )
            }
        }
    }
}

@Composable
private fun PhotoLink.imageRequest(): ImageRequest {
    val context = LocalContext.current
    return remember(path, url) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(path)
            .diskCacheKey(path)
            .build()
    }
}

/** A fresh file in cache/camera/ shared with the camera app through the FileProvider in the manifest. */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun Modifier.padStart() = this.then(Modifier.padding(start = Spacing.sm))
