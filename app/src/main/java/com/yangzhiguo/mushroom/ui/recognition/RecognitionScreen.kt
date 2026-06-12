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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yangzhiguo.mushroom.recognition.Candidate
import com.yangzhiguo.mushroom.recognition.Edibility
import com.yangzhiguo.mushroom.recognition.LocalMushroom
import com.yangzhiguo.mushroom.recognition.RecognitionState
import com.yangzhiguo.mushroom.recognition.RecognitionStore

/**
 * 识别流程主屏（设计文档 §2.2 + §6.2 识别页）。
 *
 * 布局：
 *  - 顶部：用户拍的照片缩略图
 *  - 中部：ThinkingPanel（默认展开，可折叠）
 *  - 底部：根据状态显示 loading / 候选 list / 错误 + 重试 / 详情入口
 */
@Composable
fun RecognitionScreen(
    photoUri: String,
    onOpen3D: (scientificName: String) -> Unit,
    onRetake: () -> Unit,
    viewModel: RecognitionStore = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedMushroom by viewModel.selectedMushroom.collectAsStateWithLifecycle()
    val selectedImageUrl by viewModel.selectedImageUrl.collectAsStateWithLifecycle()
    val fallbackName by viewModel.fallbackName.collectAsStateWithLifecycle()

    // 第一次进入时启动识别
    LaunchedEffect(photoUri) {
        val dataUrl = photoUriToDataUrl(context, photoUri)
        viewModel.startRecognition(imageDataUrl = dataUrl, photoUri = photoUri)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            PhotoThumbnail(uri = photoUri)
            when (val s = state) {
                is RecognitionState.Idle, is RecognitionState.Canceled -> {
                    IdleOrCanceledBody(onRetake = onRetake)
                }
                is RecognitionState.Uploading -> {
                    StatusLine(text = "正在上传图片…")
                }
                is RecognitionState.Recognizing -> {
                    ThinkingPanel(
                        thinking = s.thinkingSoFar,
                        expanded = s.isThinkingExpanded,
                        onToggle = viewModel::toggleThinkingPanel,
                    )
                }
                is RecognitionState.Recognized -> {
                    CandidatesList(
                        thinking = s.result.thinking,
                        candidates = s.result.candidates,
                    )
                }
                is RecognitionState.LocalHit, is RecognitionState.Rendered -> {
                    HitDetailBody(
                        mushroom = selectedMushroom,
                        imageUrl = selectedImageUrl,
                        onOpen3D = onOpen3D,
                        onRetake = onRetake,
                    )
                }
                is RecognitionState.LocalMiss -> {
                    MissBody(
                        fallbackName = fallbackName,
                        onOpenRemote = onOpen3D,
                        onRetake = {
                            viewModel.reset()
                            onRetake()
                        },
                    )
                }
                is RecognitionState.Error -> {
                    ErrorBody(
                        message = s.message,
                        retryable = s.retryable,
                        onRetry = {
                            val dataUrl = photoUriToDataUrl(context, photoUri)
                            viewModel.startRecognition(imageDataUrl = dataUrl, photoUri = photoUri)
                        },
                        onRetake = {
                            viewModel.reset()
                            onRetake()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(uri: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Uri.parse(uri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ThinkingPanel(
    thinking: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "正在分析蘑菇特征…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (thinking.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "折叠思考过程" else "展开思考过程",
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF7F3E8))  // 设计文档 §6.1 思考区米色
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (thinking.isEmpty()) "等待模型响应…" else thinking,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF3B3B3B),
                )
            }
        }
    }
}

@Composable
private fun CandidatesList(thinking: String, candidates: List<Candidate>) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "识别完成（${candidates.size} 个候选）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(candidates, key = { it.scientificName }) { c ->
                CandidateRow(candidate = c)
                Divider()
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: Candidate) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = candidate.scientificName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!candidate.commonName.isNullOrBlank()) {
                Text(
                    text = candidate.commonName!!,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            candidate.confidence?.let { conf ->
                Text(
                    text = "置信度 ${(conf * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            candidate.reason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HitDetailBody(
    mushroom: LocalMushroom?,
    imageUrl: String?,
    onOpen3D: (String) -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SafetyBanner(edibility = mushroom?.edibility)
        Spacer(modifier = Modifier.height(12.dp))

        // 顶部图片（从 Room 数据库异步拉取）
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = mushroom?.commonName ?: mushroom?.scientificName ?: "未知",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = mushroom?.scientificName ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 4 类分类标签
        mushroom?.edibility?.toDisplayCategory()?.let { cat ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "分类：${cat.zh}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = mushroom?.shortDesc ?: "暂无简介",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("重新拍照")
            }
            if (mushroom != null && mushroom.has3D) {
                Button(
                    onClick = { onOpen3D(mushroom.scientificName) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("查看 3D 模型")
                }
            }
        }
    }
}

@Composable
private fun MissBody(
    fallbackName: String,
    onOpenRemote: (String) -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂未收录该蘑菇", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "本地索引未匹配到 $fallbackName 。可在 iflora 上人工确认。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onOpenRemote(fallbackName) },
            enabled = fallbackName.isNotBlank(),
        ) {
            Text("在 iflora 3D 平台打开")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onRetake) {
            Text("重新拍照")
        }
    }
}

@Composable
private fun ErrorBody(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⚠", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        if (retryable) {
            Button(onClick = onRetry) { Text("重试") }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRetake) { Text("重新拍照") }
    }
}

@Composable
private fun IdleOrCanceledBody(onRetake: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("准备就绪", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetake) { Text("开始拍照") }
    }
}

@Composable
private fun StatusLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.size(12.dp))
        Text(text)
    }
}

@Composable
private fun SafetyBanner(edibility: Edibility?) {
    val isDanger = edibility?.isDangerous == true
    val containerColor = if (isDanger) Color(0xFFC0392B) else Color(0xFF2E5E3A)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            text = "⚠️ 识别结果仅供参考，切勿仅凭此判断食用安全性",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * 把 photo file URI 转成 MiniMax API 需要的 data URL。
 * 设计文档 §3.1：1080p / JPEG quality 0.85（MVP 暂未压缩，后续迭代）。
 */
private fun photoUriToDataUrl(context: Context, photoUri: String): String {
    val bytes = runCatching {
        context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { it.readBytes() }
    }.getOrNull() ?: return ""
    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}
