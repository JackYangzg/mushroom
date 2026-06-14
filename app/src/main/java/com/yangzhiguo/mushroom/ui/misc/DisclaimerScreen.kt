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
                title = { Text("隐私与免责声明") },
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
                        更新日期：2026年6月13日

                        本说明适用于“蘑菇鉴别”App。我们遵循合法、正当、透明和最小必要原则处理与你使用本 App 有关的信息。

                        ## 我们如何处理信息
                        - AI 识别：仅在你主动发起识别时，所选或拍摄的图片及识别请求会通过网络发送至当前配置的模型服务（默认使用火山方舟豆包），用于生成本次识别结果。第三方服务可能按照其规则处理必要的网络与服务日志。
                        - 图鉴服务：当你主动同步数据库、查看远程图片或相关资料时，App 会访问 `fungi.iflora.cn`、`mushroom.iflora.cn` 或资料所指向的图片来源；相关服务可能接收 IP 地址、请求时间、设备网络信息等常规访问日志。
                        - 本地存储：收藏、识别结果、识别历史照片、图鉴数据库及图片缓存主要保存在你的设备中。根据设备设置，部分数据可能进入操作系统提供的应用备份。
                        - 我们不要求注册账号，不读取通讯录、短信或精确位置，也不出售你的个人信息。

                        请勿上传含有人脸、证件、住址、联系方式、医疗资料或其他与蘑菇识别无关的个人信息。上传前请确认你有权使用相关图片。

                        ## 权限说明
                        - 相机：在你选择拍照时，用于拍摄待识别图片；拒绝后仍可使用系统选图功能。
                        - 照片或媒体：通过系统选图器访问你主动选择的图片，不会自行浏览或上传未选择的内容。
                        - 网络：用于 AI 识别、图鉴同步以及加载远程资料和图片。
                        - 通知：仅在系统和相关功能需要时使用；拒绝不会影响核心识别与图鉴浏览功能。

                        ## 保存、删除与权利
                        本地数据通常保存至你删除相关内容、清除应用数据或卸载 App。你可以通过系统设置撤回相机、照片或通知权限；撤回后不影响撤回前基于授权完成的处理。

                        如需咨询、更正、删除与本 App 有关的个人信息，或投诉个人信息处理问题，请联系 `yzg37166@126.com`。我们会在核实身份和请求后，在法律规定的期限内处理。第三方服务中的数据处理还受其服务规则约束。

                        ## 未成年人保护
                        本 App 不以不满十四周岁的未成年人为主要服务对象。未成年人应在父母或其他监护人指导下使用，不应擅自上传含有本人或他人个人信息的图片。

                        ## 食用安全免责声明
                        本 App 的候选名称由 AI 自动生成，仅供自然观察、科普和检索参考，**不可作为采食、售卖、加工、药用、诊断或治疗依据**。照片无法完整呈现气味、孢子、内部结构、生境及化学反应等鉴定特征，外观相似的物种也可能具有完全不同的毒性。

                        - 请勿仅凭本 App 或网络图片判断野生蘑菇可食用，也不要向他人推荐食用。
                        - “可食用”“无毒”或相似表述不等于在所有地区、个体、剂量和烹饪条件下均安全。
                        - 如已误食或出现恶心、呕吐、腹痛、腹泻、头晕、幻觉等不适，请立即停止食用并拨打 `120` 或尽快就医，同时保留蘑菇原物、剩余食物、呕吐物和照片供专业人员判断。
                        - 涉及食用安全的决定，应咨询当地真菌学、食品安全或医疗专业人员。

                        ## 内容与服务免责声明
                        图鉴、图片、AI 输出及第三方链接可能存在错误、遗漏、过时、不可用或权利标注不完整等情况。本 App 不保证识别结果或资料绝对准确、完整、及时，也不保证服务持续不中断。第三方内容和服务由相应提供方负责。

                        如发现内容涉嫌侵权，请联系 `yzg37166@126.com`，并提供权利证明、内容位置及处理诉求，我们将在核实后依法处理。

                        ## 责任边界
                        用户应结合专业意见独立判断并对自己的行为负责。在适用法律允许的范围内，开发者不对因错误识别、资料偏差、第三方服务、网络或设备故障，以及用户违反安全提示使用本 App 所造成的损失承担超出法定范围的责任。本条不排除或限制因开发者故意、重大过失，或法律规定不得排除或限制的责任。
                    """.trimIndent(),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
