package com.yangzhiguo.mushroom.ui.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yangzhiguo.mushroom.recognition.Candidate
import com.yangzhiguo.mushroom.recognition.LocalMushroom
import com.yangzhiguo.mushroom.recognition.RecognitionResult
import com.yangzhiguo.mushroom.recognition.RecognitionState
import com.yangzhiguo.mushroom.recognition.RecognitionStore
import com.yangzhiguo.mushroom.ui.components.PrimaryButton
import com.yangzhiguo.mushroom.ui.components.MarkdownText

@Composable
fun RecognitionScreen(
    photoUris: List<String>,
    onOpen3D: (scientificName: String) -> Unit,
    onOpenSpecies: (speciesId: Int) -> Unit = {},
    onRetake: () -> Unit,
    onDone: () -> Unit = onRetake,
    viewModel: RecognitionStore = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedMushroom by viewModel.selectedMushroom.collectAsStateWithLifecycle()
    val selectedImageUrl by viewModel.selectedImageUrl.collectAsStateWithLifecycle()
    val fallbackName by viewModel.fallbackName.collectAsStateWithLifecycle()
    val candidateSpeciesIds by viewModel.candidateSpeciesIds.collectAsStateWithLifecycle()

    LaunchedEffect(photoUris) {
        if (photoUris.isEmpty()) return@LaunchedEffect
        val dataUrls = photoUris.mapNotNull { photoUriToDataUrl(context, it) }
        viewModel.startRecognitionIfIdle(dataUrls, photoUris)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            RecognitionState.Idle, RecognitionState.Canceled -> CenterMessage(
                title = "已取消识别",
                body = "可以重新拍摄一张更清晰的照片。",
                action = "重新拍照",
                onAction = onRetake,
            )
            RecognitionState.Uploading -> RecognitionProgress(
                photoUris,
                "正在准备 ${photoUris.size} 张照片",
                "即将开始综合分析关键特征…",
                viewModel::cancel,
            )
            is RecognitionState.Recognizing -> ThinkingProgress(
                photoUris = photoUris,
                thinking = current.thinkingSoFar,
                onCancel = viewModel::cancel,
            )
            is RecognitionState.Recognized -> RecognitionProgress(photoUris, "正在匹配图鉴", "已经找到候选，正在核对本地资料…", viewModel::cancel)
            is RecognitionState.LocalHit -> ResultContent(
                photoUris = photoUris,
                result = current.result,
                mushroom = selectedMushroom,
                imageUrl = selectedImageUrl,
                onRetake = onRetake,
                onDone = onDone,
                onOpen3D = onOpen3D,
                candidateSpeciesIds = candidateSpeciesIds,
                onOpenSpecies = onOpenSpecies,
            )
            is RecognitionState.LocalMiss -> ResultContent(
                photoUris = photoUris,
                result = current.result,
                mushroom = null,
                imageUrl = null,
                missingName = fallbackName,
                onRetake = onRetake,
                onDone = onDone,
                onOpen3D = onOpen3D,
                candidateSpeciesIds = candidateSpeciesIds,
                onOpenSpecies = onOpenSpecies,
            )
            RecognitionState.Rendered -> Unit
            is RecognitionState.Error -> ErrorContent(
                message = friendlyError(current.message),
                retryable = current.retryable,
                onRetry = {
                    val dataUrls = photoUris.mapNotNull { photoUriToDataUrl(context, it) }
                    viewModel.startRecognition(dataUrls, photoUris)
                },
                onRetake = onRetake,
            )
        }
    }
}

@Composable
private fun RecognitionProgress(photoUris: List<String>, title: String, body: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecognitionPhotoStrip(photoUris)
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("这通常需要10s ~ 30s时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
    }
}

@Composable
private fun ThinkingProgress(
    photoUris: List<String>,
    thinking: String,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        RecognitionPhotoStrip(photoUris)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text("正在分析", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            MarkdownText(
                markdown = thinking.ifBlank { "正在等待模型观察图片…" },
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
    }
}

@Composable
private fun RecognitionPhotoStrip(photoUris: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photoUris, key = { it }) { photoUri ->
            AsyncImage(
                model = Uri.parse(photoUri),
                contentDescription = "待识别照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun ResultContent(
    photoUris: List<String>,
    result: RecognitionResult,
    mushroom: LocalMushroom?,
    imageUrl: String?,
    missingName: String = "",
    onRetake: () -> Unit,
    onDone: () -> Unit,
    onOpen3D: (String) -> Unit,
    candidateSpeciesIds: Map<String, Int>,
    onOpenSpecies: (Int) -> Unit,
) {
    var reasonsExpanded by remember { mutableStateOf(false) }
    var thinkingExpanded by remember { mutableStateOf(false) }
    val top = result.candidates.firstOrNull()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("识别结果", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("完成", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onDone))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SafetyBoundary()

            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = mushroom?.commonName ?: top?.commonName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 260.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                AsyncImage(
                    model = Uri.parse(photoUris.firstOrNull().orEmpty()),
                    contentDescription = "你的照片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }

            Text("识别候选", style = MaterialTheme.typography.titleMedium)
            result.candidates.forEachIndexed { index, candidate ->
                CandidateLine(
                    number = index + 1,
                    candidate = candidate,
                    speciesId = candidateSpeciesIds[candidate.scientificName],
                    onOpenSpecies = onOpenSpecies,
                )
            }

            mushroom?.let {
                Text(it.shortDesc, style = MaterialTheme.typography.bodyLarge)
            } ?: Text(
                text = "本地图鉴暂未收录 ${missingName.ifBlank { top?.scientificName.orEmpty() }}，请结合专业资料人工确认。",
                style = MaterialTheme.typography.bodyLarge,
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reasonsExpanded = !reasonsExpanded }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("为什么这样判断", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(
                            if (reasonsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    AnimatedVisibility(reasonsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            result.candidates.mapNotNull { it.reason }.filter { it.isNotBlank() }.take(3).forEach {
                                Text("• $it", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (result.candidates.none { !it.reason.isNullOrBlank() }) {
                                Text("模型未返回足够的可观察特征，建议补拍菌褶和菌柄。", style = MaterialTheme.typography.bodyMedium)
                            }
                            Divider()
                            Text(
                                "这些依据来自照片中可见特征，不能排除外观相近的有毒种。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { thinkingExpanded = !thinkingExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("大模型分析过程", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(
                            if (thinkingExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    AnimatedVisibility(thinkingExpanded) {
                        MarkdownText(
                            markdown = result.thinking.ifBlank { "模型未返回单独的分析过程。" },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                }
            }

            if (mushroom?.has3D == true) {
                OutlinedButton(
                    onClick = { onOpen3D(mushroom.scientificName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("观察 3D 结构")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton(
            text = "重新拍照",
            onClick = onRetake,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun SafetyBoundary() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("无法确认是否可食", style = MaterialTheme.typography.titleMedium)
                Text("仅凭照片不能排除有毒近似种，请不要根据本结果采食。", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CandidateLine(
    number: Int,
    candidate: Candidate,
    speciesId: Int?,
    onOpenSpecies: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = speciesId != null) { speciesId?.let(onOpenSpecies) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$number", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(
                candidate.commonName ?: candidate.scientificName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (speciesId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (!candidate.commonName.isNullOrBlank()) {
                Text(
                    candidate.scientificName,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (speciesId != null) "已收录，点击查看详情" else calibratedLikelihood(candidate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, retryable: Boolean, onRetry: () -> Unit, onRetake: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        if (retryable) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("重新拍照") }
    }
}

@Composable
private fun CenterMessage(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = action, onClick = onAction)
    }
}

private fun calibratedLikelihood(candidate: Candidate?): String = when {
    candidate?.confidence == null -> "与照片中的可见特征较为相似"
    candidate.confidence >= 0.8 -> "与照片中的可见特征较为相似"
    candidate.confidence >= 0.55 -> "有一定可能，仍需进一步核对"
    else -> "信息不足，建议补拍更多角度"
}

private fun friendlyError(message: String): String = when {
    message.contains("key", ignoreCase = true) -> "识别服务尚未配置，请检查服务设置。"
    message.contains("network", ignoreCase = true) || message.contains("timeout", ignoreCase = true) ->
        "当前网络不可用或响应较慢，可以稍后重试。"
    else -> message.ifBlank { "暂时无法识别这张照片，请换一个角度重拍。" }
}

private fun photoUriToDataUrl(context: Context, photoUri: String): String? {
    return runCatching {
        val uri = Uri.parse(photoUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        bitmap.recycle()
        "data:image/jpeg;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}"
    }.getOrNull()
}
