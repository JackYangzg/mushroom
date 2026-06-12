package com.yangzhiguo.mushroom.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.ui.components.SpeciesRow

/**
 * 首页（设计文档 §2.1 主页 + 用户要求布局）。
 *
 * 布局：
 *  - 顶部："最常见的蘑菇"（从数据库/索引取 top N）
 *  - 底部："拍照识别"大卡片（主操作）
 *  - 已移除：原"立即鉴别"（问诊流程入口）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSpecies: (Int) -> Unit = {},
    onStartAiRecognition: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 顶部：最常见的蘑菇
            item {
                Text(
                    text = "最常见的蘑菇",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(state.recommendedSpecies, key = { it.id }) { sp ->
                SpeciesRow(species = sp, onClick = { onOpenSpecies(sp.id) })
            }

            // 底部：拍照识别（主操作）
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.large,
                    onClick = onStartAiRecognition,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Column {
                            Text(
                                text = "拍照识别",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "拍一张照片，AI 帮你看是哪种蘑菇",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}
