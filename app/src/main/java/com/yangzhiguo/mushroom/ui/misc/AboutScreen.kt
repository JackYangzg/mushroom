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
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
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
                Text(
                    text = """
                        **Mushroom 蘑菇识别** v1.0

                        一款辅助认识野生蘑菇的 Android App。
                        拍一张照片，AI 模型流式输出思考过程与候选学名，
                        本地索引秒级命中详情，远端 WebView 拉取完整资料。

                        **数据来源**
                        - AI 识别：MiniMax M3
                        - 学术参考：fungi.iflora.cn / mushroom.iflora.cn
                        - 蘑菇图鉴：本地种子库（assets/mushroom_index.json）

                        **特别提醒**
                        本 App 仅供学习与兴趣参考，**不可作为食用或药用依据**。
                        详见「免责声明」页。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
