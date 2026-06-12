package com.yangzhiguo.mushroom.ui.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.ui.components.MushroomIcon
import com.yangzhiguo.mushroom.ui.components.PrimaryButton

@Composable
fun PhotoPreviewScreen(
    photoUri: Uri?,
    onRetake: () -> Unit,
    onUseThis: () -> Unit,
) {
    val context = LocalContext.current
    val fileMeta = remember(photoUri) { photoUri?.let { readFileMeta(context, it) } }
    val tooSmall = (fileMeta?.sizeBytes ?: 0L) in 1L..50_000L
    val tooDark = (fileMeta?.brightness ?: 255) < 40

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onRetake) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                }
                Text(text = stringResource(R.string.preview_retake))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(photoUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MushroomIcon(size = 96.dp, capColor = Color.White, stemColor = Color.LightGray)
                }
            }
            val hint = when {
                tooSmall -> stringResource(R.string.preview_photo_too_small)
                tooDark -> stringResource(R.string.preview_low_light)
                else -> stringResource(R.string.preview_suggestion)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("⚠", style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(
                    text = stringResource(R.string.preview_retake),
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.preview_use_this),
                    onClick = onUseThis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class PhotoFileMeta(val sizeBytes: Long, val brightness: Int)

private fun readFileMeta(context: Context, uri: Uri): PhotoFileMeta? {
    val size = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull() ?: 0L
    val brightness = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
        }
        255
    }.getOrDefault(255)
    return PhotoFileMeta(size, brightness)
}
