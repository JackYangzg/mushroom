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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.yangzhiguo.mushroom.ui.components.PrimaryButton

@Composable
fun PhotoPreviewScreen(
    photoUri: Uri?,
    onRetake: () -> Unit,
    onUseThis: () -> Unit,
) {
    val context = LocalContext.current
    val fileMeta = remember(photoUri) { photoUri?.let { readFileMeta(context, it) } }
    val warning = when {
        (fileMeta?.sizeBytes ?: 0L) in 1L..50_000L -> stringResource(R.string.preview_photo_too_small)
        (fileMeta?.brightness ?: 255) < 40 -> stringResource(R.string.preview_low_light)
        else -> null
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onRetake) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.preview_retake))
                }
                Text("确认照片", style = MaterialTheme.typography.titleLarge)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(photoUri).crossfade(true).build(),
                    contentDescription = "待识别的蘑菇照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (warning != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(warning, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.preview_retake))
                }
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
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bitmap = BitmapFactory.decodeStream(input, null, options) ?: return@use 255
            val x = bitmap.width / 2
            val y = bitmap.height / 2
            val pixel = bitmap.getPixel(x, y)
            (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
        } ?: 255
    }.getOrDefault(255)
    return PhotoFileMeta(size, brightness)
}
