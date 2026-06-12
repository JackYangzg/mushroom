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
import com.yangzhiguo.mushroom.ui.components.MarkdownText

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
                MarkdownText(
                    markdown = """
                        # 隐私与免责声明

                        ## 识别结果
                        本 App 的候选名称由 AI 自动生成，仅供科普与检索参考，**不可作为采食、药用、诊断或治疗依据**。照片无法可靠呈现气味、孢子、内部结构、生境等全部鉴定特征，外观相似种也可能具有完全不同的毒性。

                        - 不要仅凭 App 结果采食、售卖或加工野生蘑菇。
                        - 误食后如有不适，请立即联系急救或中毒咨询机构，并保留原物、剩余食物和照片。
                        - 高风险决定应交由当地真菌学、食品安全或医疗专业人员处理。

                        ## 图片与网络
                        当你主动发起识别时，所选图片会通过网络发送给第三方 AI 服务用于生成本次结果。请勿上传包含人脸、证件、住址或其他敏感信息的图片。App 不会在后台自行读取或上传相册内容。

                        ## 本地数据
                        图鉴数据库、收藏、识别历史和必要的图片缓存保存在设备本地。卸载 App 或清除应用数据会删除这些内容。数据库同步时会访问 `fungi.iflora.cn` 获取公开物种资料。

                        ## 权限
                        - 相机：仅在你拍摄蘑菇照片时使用。
                        - 相册：仅在你主动打开系统选图器时访问所选图片。
                        - 网络：用于 AI 识别、数据库同步和加载远程资料。

                        ## 责任限制
                        在法律允许的范围内，开发者和数据或模型提供方不对因错误识别、资料缺失、网络故障或用户据此采取行动造成的损失承担责任。本说明不排除法律规定不可限制的责任。
                    """.trimIndent(),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
