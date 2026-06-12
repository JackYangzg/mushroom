package com.yangzhiguo.mushroom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangzhiguo.mushroom.sync.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 数据同步卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "蘑菇数据库",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        "数据源：fungi.iflora.cn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        "最近同步：${viewModel.lastSyncLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )

                    when (val s = state) {
                        is SyncState.Idle, is SyncState.Success, is SyncState.Error -> {
                            Button(
                                onClick = { viewModel.startSync() },
                                enabled = state !is SyncState.Running,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.CloudSync, contentDescription = null)
                                Text("  同步蘑菇数据库")
                            }
                        }
                        is SyncState.Running -> {
                            val pagesProgress = (s.page.toFloat() / (s.page + 50).coerceAtLeast(1))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(end = 12.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        "正在抓取… 已完成 ${s.page} 页 / ${s.totalSpecimens} 条",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = pagesProgress.coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (state is SyncState.Success) {
                        val ok = state as SyncState.Success
                        Text(
                            "✓ 同步完成：${ok.totalSpecimens} 条  (${ok.elapsedMs / 1000}s)",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state is SyncState.Error) {
                        Text(
                            "✗ 同步失败：${(state as SyncState.Error).message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("关于", style = MaterialTheme.typography.titleMedium)
                    Text("本数据库同步自中科院昆明植物所 iFlora 真菌子平台。",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
