package com.yangzhiguo.mushroom.ui.recognition

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yangzhiguo.mushroom.recognition.MushroomRepository

/**
 * 3D 查看器（设计文档 §3.5）。
 *
 *  - 入口：mushroom.iflora.cn/?search={URL_ENCODED_NAME}
 *  - 行为：全屏 WebView + 顶部关闭按钮 + "在浏览器中打开"
 *  - 降级：WebView 加载失败 → Toast + 自动隐藏（由父 Composable 处理）
 */
@Composable
fun ThreeDViewerScreen(
    scientificName: String,
    onClose: () -> Unit,
    repository: MushroomRepository,
) {
    val context = LocalContext.current
    val url = remember(scientificName) { repository.build3DUrl(scientificName) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
                Text(
                    text = "3D · $scientificName",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = "在浏览器中打开")
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                allowFileAccess = false
                                allowContentAccess = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    return false
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }
}
