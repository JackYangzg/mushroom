package com.yangzhiguo.mushroom.ui.camera

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yangzhiguo.mushroom.ui.components.PrimaryButton

@Composable
fun PhotoPreviewScreen(
    photoUris: List<Uri>,
    maxPhotos: Int,
    onBack: () -> Unit,
    onAddMore: () -> Unit,
    onRemove: (Uri) -> Unit,
    userInfo: String,
    onUserInfoChange: (String) -> Unit,
    onUsePhotos: () -> Unit,
) {
    var selected by remember(photoUris) { mutableStateOf(photoUris.firstOrNull()) }
    if (selected !in photoUris) selected = photoUris.firstOrNull()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text("已选择 ${photoUris.size} 张", style = MaterialTheme.typography.titleLarge)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                selected?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "待识别照片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(photoUris, key = { it.toString() }) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Surface(
                            onClick = { selected = uri },
                            color = Color.Transparent,
                            modifier = Modifier.matchParentSize(),
                        ) {}
                        IconButton(
                            onClick = { onRemove(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "删除照片",
                                tint = Color.White,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
                            )
                        }
                    }
                }
            }
            Text(
                "建议从正面、侧面、菌褶和菌柄等不同角度拍摄，最多 $maxPhotos 张。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = userInfo,
                onValueChange = { onUserInfoChange(it.take(MAX_USER_INFO_LENGTH)) },
                label = { Text("补充信息（选填）") },
                placeholder = { Text("例如发现地点、时间、生长环境、气味或尺寸") },
                supportingText = {
                    Text("${userInfo.length}/$MAX_USER_INFO_LENGTH")
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onAddMore,
                    enabled = photoUris.size < maxPhotos,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Text("继续添加")
                }
                PrimaryButton(
                    text = "分析 ${photoUris.size} 张照片",
                    onClick = onUsePhotos,
                    enabled = photoUris.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val MAX_USER_INFO_LENGTH = 500
