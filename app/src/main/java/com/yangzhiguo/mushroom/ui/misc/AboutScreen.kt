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

                        “蘑菇鉴别”是一款面向自然观察与科普学习的蘑菇辅助识别工具。你可以拍摄或选择图片，获取 AI 生成的候选结果，并结合本地图鉴查看物种名称、形态特征及毒性风险等公开资料。

                        ## 重要提示
                        蘑菇鉴定具有较强专业性，仅凭照片无法完整判断气味、孢子、内部结构、生境等关键特征。AI 结果和图鉴资料均可能存在错误、遗漏或更新延迟，**不得作为采食、售卖、药用、诊断或治疗依据**。请勿食用仅经本 App 识别的野生蘑菇。

                        ## 数据来源
                        - 物种目录及相关公开资料：中科院昆明植物研究所 iFlora 真菌子平台 `fungi.iflora.cn`
                        - 3D 学术参考及相关公开资料：`mushroom.iflora.cn`
                        - AI 图片识别：火山方舟豆包模型服务

                        ## 数据更新
                        你可以在“我的 > 数据库同步”中手动更新图鉴。第三方数据的著作权、商标权及其他合法权利归原权利人所有，引用不代表相关机构对本 App 的认可或背书。

                        ## 联系我们
                        意见反馈：`yzg37166@126.com`

                        侵权联系方式：如涉及侵权请联系 `yzg37166@126.com`。请在邮件中说明权利主体、权利证明、涉嫌侵权内容及具体诉求，我们将在核实后依法及时处理。

                        ## 协议说明
                        使用本 App 即表示你已阅读并理解“隐私与免责声明”。本 App 的功能、数据来源和说明可能随版本更新而调整，请以当前版本展示内容为准。
                    """.trimIndent(),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
