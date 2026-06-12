package com.yangzhiguo.mushroom.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.ui.components.MushroomIcon

/**
 * 首页（设计文档 §2.1 主页 + 用户要求布局）。
 *
 * 布局：
 *  - 顶部："最常见的蘑菇"（从数据库/索引取 top N）
 *  - 底部："拍照识别"大卡片（主操作）
 *  - 已移除：原"立即鉴别"（问诊流程入口）
 */
@Composable
fun HomeScreen(
    onStartAiRecognition: () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "识别",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(0.75f))
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center,
            ) {
                MushroomIcon(size = 112.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "拍下你看到的蘑菇",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "尽量包含菌盖、菌褶和菌柄",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStartAiRecognition,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("拍照识别", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onStartAiRecognition,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("从相册选择")
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "仅凭照片不能判断食用安全。遇到不确定的野生蘑菇，请不要采食。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
