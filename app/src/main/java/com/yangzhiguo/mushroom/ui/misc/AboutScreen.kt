package com.yangzhiguo.mushroom.ui.misc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangzhiguo.mushroom.R
import com.yangzhiguo.mushroom.BuildConfig
import com.yangzhiguo.mushroom.ui.components.MarkdownText

/**
 * 关于页面(从"我的"tab 进入,带返回按钮)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    Text(
                        text = stringResource(R.string.species_back),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack,
                            ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MarkdownText(
                    markdown = """
                        # 蘑菇鉴别
                        版本 ${BuildConfig.VERSION_NAME}

                        本 App 用于辅助认识蘑菇。你可以拍照或从相册选择图片，获得 AI 生成的候选名称，并在同步后的本地图鉴中检索物种资料。

                        ## 数据来源
                        - 物种目录、有食用记录与毒性风险列表：中科院昆明植物研究所 iFlora 真菌子平台 `fungi.iflora.cn`
                        - 3D 学术参考：`mushroom.iflora.cn`
                        - 图片识别：MiniMax 模型服务

                        ## 数据更新
                        图鉴数据可在“我的 > 数据库同步”中手动更新。同步成功后立即生效，无需重启 App。

                        ## 使用边界
                        AI 和数据库记录都可能不完整或存在误差。本 App **不能确认食用安全性，也不能替代真菌学、医疗或食品安全专业意见**。
                    """.trimIndent(),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
