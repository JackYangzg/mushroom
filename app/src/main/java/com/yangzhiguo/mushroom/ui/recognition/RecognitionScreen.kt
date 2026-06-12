package com.yangzhiguo.mushroom.ui.recognition

import android.content.Context
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

@Composable
fun RecognitionScreen(
    photoUri: String,
    onOpen3D: (scientificName: String) -> Unit,
    onRetake: () -> Unit,
    onDone: () -> Unit = onRetake,
    viewModel: RecognitionStore = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedMushroom by viewModel.selectedMushroom.collectAsStateWithLifecycle()
    val selectedImageUrl by viewModel.selectedImageUrl.collectAsStateWithLifecycle()
    val fallbackName by viewModel.fallbackName.collectAsStateWithLifecycle()

    LaunchedEffect(photoUri) {
        viewModel.startRecognition(photoUriToDataUrl(context, photoUri), photoUri)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            RecognitionState.Idle, RecognitionState.Canceled -> CenterMessage(
                title = "已取消识别",
                body = "可以重新拍摄一张更清晰的照片。",
                action = "重新拍照",
                onAction = onRetake,
            )
            RecognitionState.Uploading -> RecognitionProgress(photoUri, "正在准备照片", "即将开始识别关键特征…", viewModel::cancel)
            is RecognitionState.Recognizing -> RecognitionProgress(
                photoUri,
                "正在识别",
                recognitionStatus(current.thinkingSoFar),
                viewModel::cancel,
            )
            is RecognitionState.Recognized -> RecognitionProgress(photoUri, "正在匹配图鉴", "已经找到候选，正在核对本地资料…", viewModel::cancel)
            is RecognitionState.LocalHit -> ResultContent(
                photoUri = photoUri,
                result = current.result,
                mushroom = selectedMushroom,
                imageUrl = selectedImageUrl,
                onRetake = onRetake,
                onDone = onDone,
                onOpen3D = onOpen3D,
            )
            is RecognitionState.LocalMiss -> ResultContent(
                photoUri = photoUri,
                result = current.result,
                mushroom = null,
                imageUrl = null,
                missingName = fallbackName,
                onRetake = onRetake,
                onDone = onDone,
                onOpen3D = onOpen3D,
            )
            RecognitionState.Rendered -> Unit
            is RecognitionState.Error -> ErrorContent(
                message = friendlyError(current.message),
                retryable = current.retryable,
                onRetry = { viewModel.startRecognition(photoUriToDataUrl(context, photoUri), photoUri) },
                onRetake = onRetake,
            )
        }
    }
}

@Composable
private fun RecognitionProgress(photoUri: String, title: String, body: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = Uri.parse(photoUri),
            contentDescription = "正在识别的蘑菇照片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
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
        Text("这通常需要几秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
    }
}

@Composable
private fun ResultContent(
    photoUri: String,
    result: RecognitionResult,
    mushroom: LocalMushroom?,
    imageUrl: String?,
    missingName: String = "",
    onRetake: () -> Unit,
    onDone: () -> Unit,
    onOpen3D: (String) -> Unit,
) {
    var reasonsExpanded by remember { mutableStateOf(false) }
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
                    model = Uri.parse(photoUri),
                    contentDescription = "你的照片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("最可能", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    mushroom?.commonName ?: top?.commonName ?: top?.scientificName ?: "暂时无法确认",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    mushroom?.scientificName ?: top?.scientificName.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    calibratedLikelihood(top),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            mushroom?.let {
                Text(it.shortDesc, style = MaterialTheme.typography.bodyLarge)
            } ?: Text(
                text = "本地图鉴暂未收录 ${missingName.ifBlank { top?.scientificName.orEmpty() }}，请结合专业资料人工确认。",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (result.candidates.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("其他可能", style = MaterialTheme.typography.titleMedium)
                    result.candidates.drop(1).forEachIndexed { index, candidate ->
                        CandidateLine(index + 2, candidate)
                    }
                }
            }

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
private fun CandidateLine(number: Int, candidate: Candidate) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(candidate.commonName ?: candidate.scientificName, style = MaterialTheme.typography.bodyLarge)
            if (!candidate.commonName.isNullOrBlank()) {
                Text(
                    candidate.scientificName,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

private fun recognitionStatus(thinking: String): String = when {
    thinking.length < 80 -> "正在查看菌盖、菌褶和菌柄…"
    thinking.length < 240 -> "已经看到部分关键特征，正在匹配候选…"
    else -> "候选很接近，正在继续核对…"
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

private fun photoUriToDataUrl(context: Context, photoUri: String): String {
    val bytes = runCatching {
        context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { it.readBytes() }
    }.getOrNull() ?: return ""
    return "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}
