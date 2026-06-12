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
 * 免责声明(从"我的"tab 进入,带返回按钮)。设计文档 §8.1 / §6.3 食安免责条款。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerScreen(onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("免责声明") },
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
                Text(
                    text = """
                        本 App 的识别结果由 AI 模型自动生成，仅供学习与兴趣参考，**不可作为食用或药用依据**。

                        蘑菇种类繁多、形态相近、毒素类型复杂，AI 识别存在已知误差率。任何情况下：
                        1. 不应仅凭本 App 结果采食野生蘑菇
                        2. 误食后出现任何不适请立即就医，并保留蘑菇样本
                        3. 涉及食品安全与生命健康，请咨询当地真菌学专业人员

                        本 App 及背后模型提供者对因误信识别结果导致的任何后果不承担责任。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
