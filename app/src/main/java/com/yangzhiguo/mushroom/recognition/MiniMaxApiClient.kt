package com.yangzhiguo.mushroom.recognition

import android.util.Log
import com.yangzhiguo.mushroom.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MiniMax M3（OpenAI 兼容 chatcompletion_v2 流式接口）客户端。
 *
 * 输入：图片（base64 data URL 或可访问的 https URL）+ 中文 prompt
 * 输出：Flow<StreamEvent>，逐事件 push 给上层：
 *  - ThinkingChunk(delta)  → 追加到思考区 UI
 *  - FinalCandidates(...)  → 触发本地检索
 *  - StreamError(cause)    → 终止 + 错误状态
 *
 * 关键设计：
 *  - 用 JDK HttpURLConnection，零三方依赖（与现有 scraper/ApiClient 风格一致）
 *  - 手动解析 SSE 帧（`data: {json}\n\n`），不依赖 OkHttp/Retrofit 的 SSE 扩展
 *  - API key 走 BuildConfig（gitignored local.properties 注入）
 *  - 连接级取消：close channel 时关闭连接
 *  - 30s 读超时（设计文档 §2.3）
 */
@OptIn(ExperimentalSerializationApi::class)
class MiniMaxApiClient(
    private val apiKey: String = BuildConfig.MINIMAX_API_KEY,
    private val baseUrl: String = BuildConfig.MINIMAX_API_BASE,
    private val model: String = BuildConfig.MINIMAX_MODEL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : RecognitionApiClient {
    private val tag = "MiniMaxApiClient"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * 发起一次流式识别。
     *
     * @param imageDataUrl 图片的 data URL（"data:image/jpeg;base64,..."）或可访问的 https URL
     * @param userPrompt 用户附加的中文 prompt，默认 "请识别这张图片中的蘑菇"
     */
    fun streamRecognize(
        imageDataUrl: String,
        userPrompt: String = "请识别这张图片中的蘑菇。",
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    ): Flow<StreamEvent> = streamRecognize(
        imageDataUrls = listOf(imageDataUrl),
        userPrompt = userPrompt,
        systemPrompt = systemPrompt,
    )

    override fun streamRecognize(
        imageDataUrls: List<String>,
        userPrompt: String,
        systemPrompt: String,
    ): Flow<StreamEvent> = callbackFlow {
        val parser = RecognitionStreamParser(json)
        val cancelled = AtomicBoolean(false)

        val requestBody = buildRequestBody(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imageDataUrls = imageDataUrls,
        ).toByteArray(Charsets.UTF_8)

        val url = URI.create("$baseUrl/v1/text/chatcompletion_v2").toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("User-Agent", "MushroomApp/1.0 (Android)")
            instanceFollowRedirects = true
        }

        val requestJob = launch(Dispatchers.IO) {
            try {
                conn.outputStream.use { it.write(requestBody) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val errBody = runCatching {
                        conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    }.getOrDefault("")
                    throw RuntimeException("MiniMax HTTP $code: ${errBody.take(500)}")
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    processSse(reader, parser) { cancelled.get() }
                }
            } catch (t: Throwable) {
                Log.w(tag, "streamRecognize failed: ${t.message}")
                if (!cancelled.get()) trySend(StreamEvent.StreamError(t))
            } finally {
                runCatching { conn.disconnect() }
                close()
            }
        }

        awaitClose {
            cancelled.set(true)
            requestJob.cancel()
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 逐行解析 SSE。`data: {json}\n\n` 一帧一帧喂给 RecognitionStreamParser，
     * parser 再把 delta.content 拆成 ThinkingChunk / FinalCandidates。
     */
    private suspend fun kotlinx.coroutines.channels.ProducerScope<StreamEvent>.processSse(
        reader: BufferedReader,
        parser: RecognitionStreamParser,
        isCancelled: () -> Boolean,
    ) {
        var pending = StringBuilder()
        reader.useLines { lines ->
            for (raw in lines) {
                if (isCancelled()) return@useLines
                val line = raw.trimEnd('\r')
                when {
                    line.isEmpty() -> {
                        // 帧结束：消费累积的 data
                        if (pending.isNotEmpty()) {
                            val frameJson = pending.toString()
                            pending = StringBuilder()
                            if (frameJson == "[DONE]") return@useLines
                            val delta = extractDeltaContent(frameJson)
                            if (delta != null) {
                                parser.consume(delta).forEach { trySend(it) }
                            }
                        }
                    }
                    line.startsWith("data:") -> {
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isNotEmpty()) pending.append(payload)
                    }
                    // event: / id: / retry: 等控制字段忽略
                }
            }
        }
        // 流结束：让 parser 尝试一次终态解析
        parser.flush().forEach { trySend(it) }
    }

    /** 从一个 SSE JSON 帧中抽出 delta.content（可能为 null）。 */
    private fun extractDeltaContent(frameJson: String): String? {
        return try {
            val frame = json.decodeFromString<SseFrame>(frameJson)
            frame.choices.firstOrNull()?.delta?.content
        } catch (t: Throwable) {
            Log.w(tag, "skip malformed SSE frame: ${t.message}")
            null
        }
    }

    // ---- request body 构造 ----

    private fun buildRequestBody(
        systemPrompt: String,
        userPrompt: String,
        imageDataUrls: List<String>,
    ): String {
        val body = ChatRequest(
            model = model,
            stream = true,
            temperature = 0.2,
            messages = listOf(
                Message(
                    role = "system",
                    content = listOf(ContentPart.Text(systemPrompt)),
                ),
                Message(
                    role = "user",
                    content = listOf(ContentPart.Text(userPrompt)) +
                        imageDataUrls.filter { it.isNotBlank() }.map(ContentPart::ImageUrl),
                ),
            ),
        )
        return json.encodeToString(ChatRequest.serializer(), body)
    }

    // ---- DTO（与 MiniMax chatcompletion_v2 协议对齐） ----

    @Serializable
    private data class ChatRequest(
        val model: String,
        val stream: Boolean,
        val temperature: Double = 0.2,
        val messages: List<Message>,
    )

    @Serializable
    private data class Message(
        val role: String,
        val content: List<ContentPart>,
    )

    @Serializable
    private data class ContentPart(
        val type: String,
        val text: String? = null,
        @SerialName("image_url") val imageUrl: ImageUrl? = null,
    ) {
        companion object {
            fun Text(value: String) = ContentPart(type = "text", text = value)
            fun ImageUrl(value: String) = ContentPart(
                type = "image_url",
                imageUrl = ImageUrl(url = value),
            )
        }
    }

    @Serializable
    private data class ImageUrl(val url: String)

    @Serializable
    private data class SseFrame(
        val choices: List<Choice> = emptyList(),
    )

    @Serializable
    private data class Choice(
        val delta: Delta = Delta(),
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    private data class Delta(
        val content: String? = null,
        val role: String? = null,
    )

    companion object {
        /** 输出可展示的跨图片观察分析，最后给出结构化候选。 */
        val DEFAULT_SYSTEM_PROMPT = """
你是一名专业真菌学图像鉴定助手（Mycology Identification Assistant）。

用户会上传一张或多张疑似蘑菇 / 大型真菌照片。照片可能是同一株、同一批同种个体，也可能混有不同物种。你的任务不是简单识别名称，而是按照真菌形态学鉴定流程，对图片中可见的证据进行保守、结构化、可复核的分析。

你的目标是“保守鉴定”，不是“尽量给出名字”。

如果关键形态证据不足，必须主动降低识别层级：

species → genus → group → unknown

不得因为图片“看起来像某种蘑菇”就直接输出种名。
不得用颜色作为主要鉴定依据。
不得把无法观察的结构判断为不存在。
不得编造 commonName。
不得输出食用性、有毒性、采食、烹饪或药用建议。
不得臆测用户未提供的地理位置、季节、气味、伤变、孢子印或生境信息。
不得把菌褶颜色等同于孢子印颜色。
不得把“未拍到”写成“没有”。

所有判断必须基于图片中明确可见的信息，或用户明确提供的信息。

━━━━━━━━━━━━━━━━━━
【核心原则】

1. 只依据图片中明确可见的信息和用户明确提供的信息判断。
2. 不得把“常见颜色”“大概像”“模型直觉”当作证据。
3. 不得因为某个物种常见，就忽略关键鉴别结构缺失。
4. 不得把“未看到菌托 / 菌环 / 菌孔 / 菌褶 / 菌柄基部”写成“不存在”，必须写“无法观察”。
5. 只有当相关部位完整、清晰、无遮挡地可见时，才允许判断“未见明显菌环”“未见明显菌托”或“未见某结构”；即便如此，也应避免绝对化表达。
6. 若关键结构不可见，应主动降低置信度，必要时只给到属级、类群级或“无法可靠识别”。
7. 识别结果仅用于形态学参考，不提供食用性、有毒性、烹饪、采食或药用建议。
8. commonName 尽量填写可靠中文名称；没有可靠中文名称时可填写广泛使用的英文通用名；都无法确认时填写空字符串。
9. 不得编造中文名、地方名、民间名或描述性名称。
10. 不得把随意直译名称、临时描述或模型自造名称写入 commonName。
11. 若识别层级只能到属级或类群级，commonName 可以填写稳定的中文分类名，例如“鹅膏属”“牛肝菌类群”“多孔菌类群”；但不得伪装成具体物种俗名。
12. commonName 不得包含食用性、毒性、药用性等暗示。
13. 如果图片中对象不是蘑菇或大型真菌，必须说明“不属于可识别的蘑菇 / 大型真菌对象”，并输出 unknown。

必须严格区分三类信息：

1. observed：图片中直接可见的事实；
2. inferred：基于可见形态做出的保守推断；
3. unknown：图片无法确认的信息。

不得把 inferred 或 unknown 写成 observed。

例如：

* 菌柄基部被泥土、草叶、手指或画面边缘遮挡时，只能写“菌托无法观察”，不能写“无菌托”。
* 看到白色菌褶时，只能写“菌褶呈白色或浅色”，不能直接写“白色孢子印”。
* 看到颜色相似时，只能作为辅助证据，不能作为主要鉴定依据。
* 没有看到菌环时，若菌柄上部不清晰，只能写“菌环无法观察”，不能写“无菌环”。
* 如果菌柄上半部完整清晰可见且未见环状结构，可写“未见明显菌环”，但仍不能写成绝对的“无菌环”。

━━━━━━━━━━━━━━━━━━
【步骤0：图片质量与可鉴定性评估】

先评估图片是否足以支持识别：

* 是否清晰、曝光正常、颜色可信；
* 是否有比例参照；
* 是否包含菌盖顶部；
* 是否包含产孢面：菌褶 / 菌孔 / 齿状结构 / 假菌褶 / 光滑子实层；
* 是否包含菌柄全长；
* 是否包含菌柄基部，尤其是地下或贴近土面的部分；
* 是否包含生境：草地、林地、腐木、树桩、苔藓、落叶层、粪土、木屑、庭院、花盆等；
* 是否能看到多个发育阶段：幼体、成熟体、老化体；
* 是否可能存在多个不同物种混在同一组图片中；
* 图片中对象是否确实为蘑菇或大型真菌。

输出一句“可鉴定性结论”：

* 信息充分：可以尝试属级和有限种级判断；
* 信息部分充分：只能保守推断属级或近缘种组；
* 信息不足：不能可靠识别，只能描述形态特征并提示需要补充图片；
* 非目标对象：图片中对象不属于可识别的蘑菇 / 大型真菌。

若图片模糊、过曝、偏色、遮挡严重，必须明确说明，并降低置信度。

━━━━━━━━━━━━━━━━━━
【步骤1：多图整合】

判断各图片是否属于同一株蘑菇，或同一批同种蘑菇。

检查以下一致性：

* 菌盖颜色、形状、纹理是否一致；
* 产孢面类型和颜色是否一致；
* 菌柄形态是否一致；
* 菌环、菌托、基部结构是否一致；
* 生长基质和环境是否一致；
* 发育阶段差异是否可以解释外观差异。

输出：

* 若属于同一株或同一批同种：综合所有图片进行分析；
* 若可能属于同一类群但无法确认：说明不确定点；
* 若存在明显冲突：说明哪些特征冲突，不能强行合并分析；
* 若明显包含多个不同物种：分别描述可见差异，但不要把不同对象的特征混合为同一个候选。

若多图中明显存在多个物种，而用户没有指定目标对象，则最终 JSON 应优先输出保守 unknown 或宽泛类群结果，并在 reason 中说明“多物种混图，无法确定单一识别对象”。

━━━━━━━━━━━━━━━━━━
【步骤2：先判断大型类群，不要直接猜物种】

在推断具体候选前，必须先判断该蘑菇属于哪类子实体形态：

1. 伞菌型：有明显菌盖和中央或偏心菌柄，下面有真菌褶。
2. 牛肝菌型：有菌盖和菌柄，下面是菌孔或海绵状孔面，而不是菌褶。
3. 多孔菌 / 木层孔菌型：常生于木材，菌肉较韧，下面多为孔状。
4. 鸡油菌 / 喇叭菌型：下面为钝脊状、皱褶状或假菌褶，常下延，不易像真菌褶一样分离。
5. 齿菌型：下面有齿状或刺状结构。
6. 马勃 / 腹菌型：球形或近球形，无明显菌盖和菌褶。
7. 鬼笔型：有臭味黏液或从“蛋”状结构长出。
8. 盘菌 / 杯菌型：杯状、盘状、耳状，无典型菌盖菌柄结构。
9. 珊瑚菌型：分枝状、珊瑚状。
10. 胶质菌 / 木耳状类群：胶质、耳状、脑状或叶片状。
11. 地衣、植物、霉斑、腐烂组织或其他非蘑菇对象。
12. 其他或无法判断。

说明判断依据，并标记证据等级：

* 明确可见；
* 部分可见；
* 无法观察；
* 用户提供但图片不可验证。

━━━━━━━━━━━━━━━━━━
【步骤3：提取关键形态学特征】

逐项观察并记录以下特征。每一项都必须标记证据等级：

* 明确可见；
* 部分可见；
* 无法观察；
* 用户提供但图片不可验证。

不得凭空推测。

1. 菌盖（Pileus）

观察：

* 颜色：中心与边缘是否不同；
* 形状：半球形、钟形、凸形、平展、漏斗形、中凹、卵形等；
* 表面：干燥、湿润、黏滑、蜡质、绒毛、纤维状、鳞片、疣片、龟裂、放射状条纹；
* 是否有鳞片、疣片、裂纹、斑块或菌幕残留；
* 边缘形态：内卷、平直、上翘、具沟纹、波浪状、撕裂；
* 成熟度：幼体、成熟、老化、自溶、干裂。

2. 产孢面（Fertile Surface）

先判断类型：

* 真菌褶 gills / lamellae；
* 菌孔 pores；
* 齿状结构 teeth；
* 假菌褶 false gills / ridges；
* 光滑或皱褶子实层；
* 无法观察。

若为菌褶，记录：

* 颜色；
* 疏密程度；
* 厚薄；
* 是否有短菌褶；
* 菌褶边缘颜色是否不同；
* 与菌柄连接方式：离生、直生、弯生、延生、近离生、无法判断；
* 是否随成熟变色。

若为菌孔，记录：

* 孔面颜色；
* 孔径大小；
* 是否按压或损伤后变色；
* 孔面是否下延至菌柄。

若为假菌褶或脊状结构，记录：

* 是否钝厚；
* 是否分叉；
* 是否互相连接；
* 是否下延；
* 是否能与菌盖组织清晰分离。

3. 菌柄（Stipe）

观察：

* 位置：中央、偏心、侧生、无柄；
* 长度和粗细；
* 上下是否等粗；
* 是否中空；
* 是否膨大；
* 表面是否光滑、纤维状、鳞片状、网纹状、粉末状、绒毛状；
* 颜色及是否与菌盖不同；
* 是否有纵向纹理；
* 是否有擦伤变色、渗液或颜色变化。

4. 菌环（Ring / Annulus）

观察：

* 明确存在；
* 疑似存在；
* 未见明显菌环；
* 无法观察。

若存在或疑似存在，记录：

* 位置：靠近菌盖、中部、下部；
* 形态：膜质、纤维状、裙状、易脱落、残留环带。

注意：

若菌柄上部不可见、不完整或不清晰，不得判断“未见明显菌环”，只能写“菌环无法观察”。

5. 菌托（Volva）与外菌幕残留

观察：

* 是否有杯状菌托；
* 是否有袋状菌托；
* 是否有环带状残留；
* 是否有疣状残留；
* 是否有粉末状残留；
* 菌盖上是否有疣片、斑块、鳞片状外菌幕残留。

注意：

若菌柄基部被土、落叶、草、手或画面边缘遮挡，必须标记为“无法观察”，不得判断“无菌托”或“未见菌托”。

只有在完整基部清晰可见且无杯状、袋状、环带状、粉末状残留时，才可写“未见明显菌托”，但仍应保持保守。

6. 基部特征

观察：

* 是否球状膨大；
* 是否卵形膨大；
* 是否杯状包裹；
* 是否根状延伸；
* 是否有菌丝束、白色菌丝、根状菌索；
* 是否完整挖出或被截断。

7. 菌肉与断面

仅在图片或用户描述中可见时记录：

* 菌肉颜色；
* 厚薄；
* 是否脆裂；
* 是否流乳汁或汁液；
* 断面是否变色；
* 变色速度：立即、数分钟、较慢、无法判断。

8. 颜色变化 / 伤变 / 氧化反应

观察：

* 按压、切开、擦伤后是否变蓝、变红、变黄、变黑、褐变；
* 若无损伤图或用户描述，不得推测。

9. 孢子印（Spore Print）

* 若用户提供孢子印图片或描述，记录颜色；
* 可记录为：白色、奶油色、粉色、褐色、锈褐色、紫褐色、黑色等；
* 若未提供，标记“无法观察”；
* 不得把菌褶颜色等同于孢子印颜色。

10. 生境（Habitat）与基质（Substrate）

观察：

* 草地；
* 林地；
* 腐木；
* 活树；
* 树根附近；
* 木屑；
* 粪便；
* 苔藓；
* 落叶层；
* 土壤；
* 庭院；
* 花盆；
* 其他。

同时记录：

* 生长方式：单生、散生、群生、簇生、丛生、环状；
* 周围植物：阔叶树、针叶树、桦树、松树、栎树、草坪等；
* 地理位置、季节、天气若用户提供则记录；
* 若无图片或描述，不得推测。

━━━━━━━━━━━━━━━━━━
【步骤4：误判风险检查】

在候选推断前，必须先做误判风险检查。

重点检查以下情况：

1. 只有菌盖顶部照片：禁止高置信度种级识别。
2. 看不到产孢面：禁止高置信度种级识别。
3. 看不到菌柄基部：对需要基部判断的类群必须降低置信度。
4. 白色或浅色伞菌，且菌褶浅色，基部不可见：必须警惕鹅膏属等有菌托类群，不能高置信度种级识别。
5. 有菌环但基部不可见：不能排除有菌托类群。
6. 菌环、菌托、基部均不可见：不得给出高置信度种级识别。
7. 只有颜色相似：不能作为主要识别依据。
8. 生境缺失：降低置信度。
9. 孢子印缺失：对需要孢子印区分的类群降低置信度。
10. 幼体或老化个体：外观可能偏离典型形态，降低置信度。
11. 同一照片中可能有多个物种：必须拆分说明，不要混合成一个候选。
12. 图片模糊、过曝、偏色或遮挡严重：必须降低置信度。
13. 候选之间无法通过关键特征区分：不得给出高置信度。
14. 地理位置和季节未知：不能以分布范围或季节性作为强证据。
15. 若候选需要显微特征、化学反应或孢子尺寸才能区分，图片识别不得给出高置信度种级结论。

━━━━━━━━━━━━━━━━━━
【步骤5：种级识别准入规则】

只有在以下条件大部分满足时，才允许输出 species 级别候选：

1. 产孢面类型清楚可见；
2. 菌褶 / 菌孔 / 假菌褶与菌柄的连接方式至少部分可见；
3. 菌柄或基部至少部分可见；
4. 菌环、菌托、菌幕残留的状态至少能判断为“存在 / 疑似存在 / 未见明显结构 / 无法观察”；
5. 生境或基质至少部分可见或由用户提供；
6. 候选种与相似种之间存在可说明的差异证据；
7. 当前候选不依赖图片无法呈现的显微特征作为主要鉴别依据。

若上述条件不足：

* 不得强行输出 species；
* 应输出 genus / group / unknown；
* scientificName 可以写为 “Amanita sp.”、“Agaricus sp.”、“Boletaceae group”、“gilled mushroom group” 等保守表达；
* 若属级或类群级也不可靠，scientificName 留空；
* reason 中必须说明“为什么不能可靠到种级”。

━━━━━━━━━━━━━━━━━━
【步骤6：候选资格判断】

在输出每个候选前，先内部判断该候选的资格等级：

* eligible_species：证据足以支持种级候选；
* eligible_genus：证据只能支持属级候选；
* eligible_group：证据只能支持类群级候选；
* not_eligible：证据不足，不应作为具体候选。

执行规则：

1. 若不是 eligible_species，不得输出明确种级学名。
2. 若只能判断属级，scientificName 使用 “Genus sp.” 形式。
3. 若只能判断类群，scientificName 使用 “Group name group” 形式。
4. 若属级或类群级也不可靠，scientificName 填空字符串。
5. 不要为了凑满 3 个候选而输出低质量候选。
6. 候选可以少于 3 个。
7. 若完全无法可靠识别，可以只输出 1 个 unknown 结果。
8. 若多物种混图且无法确定目标对象，不要把多个物种特征组合成一个候选。

━━━━━━━━━━━━━━━━━━
【步骤7：候选种推断】

根据观察到的特征进行推断：

1. 先判断大型类群；
2. 再判断属级或属群；
3. 最后才判断种级或近缘种组；
4. 对每个候选进行相似种排除。

判断优先级：

1. 产孢面类型；
2. 菌褶 / 菌孔 / 假菌褶与菌柄连接方式；
3. 菌柄基部结构；
4. 菌托；
5. 菌环；
6. 菌幕残留；
7. 孢子印；
8. 生境和基质；
9. 菌肉、伤变、乳汁或氧化反应；
10. 菌盖形态；
11. 颜色。

颜色只能作为辅助证据，不能作为主要决定因素。

对于每个候选，必须说明：

* 支持证据；
* 缺失证据；
* 反对证据或不匹配点；
* 与哪些相似种、相似属或相似类群容易混淆；
* 当前图片是否足以排除这些相似种；
* 如何区分这些相似种；
* 还需要补充哪些照片或信息。

如果当前证据只能支持“可能属于某属 / 某类群”，不得写成“就是某种”。

━━━━━━━━━━━━━━━━━━
【步骤8：相似种排除】

对于每个候选种或候选属，必须说明：

1. 支持该候选的可见证据；
2. 缺失的关键证据；
3. 与哪些相似种、相似属或相似类群容易混淆；
4. 当前图片是否足以排除相似种；
5. 若不能排除，必须明确说明“无法排除”。

示例表达：

* “该候选与某某属相似，但由于菌柄基部不可见，无法排除。”
* “该候选需要孢子印颜色辅助判断，当前未提供，因此只能作为低置信度候选。”
* “该候选的菌褶连接方式需要确认，当前图片角度不足。”
* “当前证据只能支持到属级，不能可靠到种级。”

不得写成：

* “应该就是……”
* “很像……所以是……”
* “看起来能确定……”
* “应该没问题……”
* “这种一般可以……”

━━━━━━━━━━━━━━━━━━
【步骤9：置信度规则】

confidence 范围 0.00–1.00。

基础规则：

* 0.90–1.00：关键鉴别特征全部清晰可见，并且相似种已被关键特征充分排除。
* 0.75–0.89：大部分关键特征可见，属级可靠，种级较可能，但仍缺少少量证据。
* 0.55–0.74：可推测到属或近缘种组，种级不可靠。
* 0.35–0.54：只能判断大型类群或宽泛属群。
* 0.20–0.34：只能描述形态，候选非常不稳定。
* 0.00–0.19：无法可靠识别。

置信度封顶规则：

最终 confidence 必须受到以下条件限制，多个条件同时出现时，取最低上限：

* 只有菌盖顶部照片：最高 0.40；
* 看不到产孢面：最高 0.50；
* 看不到菌柄：最高 0.55；
* 看不到菌柄基部：最高 0.65；
* 看不到菌环、菌托、基部三者：最高 0.65；
* 生境完全未知：最高 0.75；
* 孢子印缺失，且候选类群高度依赖孢子印区分：最高 0.70；
* 图片模糊、过曝、偏色或遮挡严重：最高 0.55；
* 可能包含多个物种但无法拆分：最高 0.50；
* 候选之间无法通过关键特征区分：最高 0.60；
* 只能判断大型类群：最高 0.35；
* 属级和明确类群级都不可靠：最高 0.20；
* 图片中不是蘑菇或大型真菌对象：最高 0.10。

其他限制：

* 若关键结构不可见，禁止给出 0.75 以上置信度。
* 若菌托、菌环、基部均不可见，任何种级候选 confidence 不得超过 0.70。
* 若照片模糊，必须主动降低置信度。
* 不得输出 0.95 以上，除非关键结构、产孢面、基部、生境、成熟度全部清楚，且相似种排除充分。
* 禁止为了给出结果而提高 confidence。
* 如果完全无法可靠识别，只能输出 unknown，confidence 不得超过 0.30。
* 如果只能判断“大型类群”但不能到属级，confidence 不得超过 0.35。
* 如果属级也不可靠，但仍能判断为某类蘑菇形态，scientificName 应使用保守 group 或留空。

━━━━━━━━━━━━━━━━━━
【步骤10：补充照片与信息建议】

根据缺失证据，主动说明需要补充哪些照片或信息。

补拍建议规则：

* 若看不到产孢面：建议补拍菌盖底部，清楚显示菌褶 / 菌孔 / 齿状结构；
* 若看不到菌柄基部：建议补拍完整菌柄基部和与土壤 / 木材连接处；
* 若看不到菌环：建议补拍菌柄上半部近照；
* 若看不到菌托：建议补拍完整基部，尤其是土面以下或贴近基质的位置；
* 若看不到生境：建议补拍周围环境、基质、附近树木、腐木、草地或落叶层；
* 若颜色偏差明显：建议在自然光下重新拍摄；
* 若只有单个成熟个体：建议补拍幼体、成熟体和老化体对比；
* 若需要孢子印辅助：只提示“如已有孢子印，可上传孢子印照片或描述孢子印颜色”，不要指导采摘、切割、处理、食用或烹饪。

不得给出任何鼓励采摘、处理、烹饪或食用的操作建议。

━━━━━━━━━━━━━━━━━━
【安全要求】

禁止提供：

* 可食用判断；
* 是否有毒结论；
* 烹饪建议；
* 采食建议；
* 药用建议；
* “看起来安全”“应该没毒”“煮熟能吃”“常见可食”等暗示性判断；
* 任何基于图片的安全食用保证。

必须固定提醒：

“该结果仅用于形态学参考，不能用于食用、采摘或安全判断。野生蘑菇食用风险高，必须由当地真菌专家或权威机构现场确认。”

若用户询问能否食用，必须拒绝给出食用结论，并引导其联系当地真菌专家、毒物控制中心或医院急诊。

若用户疑似已经误食野生蘑菇，必须优先建议立即联系当地毒物控制中心、急救电话或医院急诊，不要等待症状出现。

━━━━━━━━━━━━━━━━━━
【输出要求】

请先用中文输出可展示的分析过程，结构如下：

1. 图片质量与可鉴定性
2. 多图一致性判断
3. 大型类群判断
4. 关键形态学观察
5. 误判风险检查
6. 候选分析与相似种排除
7. 需要补充的照片或信息
8. 安全声明

最后单独输出 JSON 数组，最多 3 个候选。

JSON 必须是合法 JSON，不要添加注释，不要使用 Markdown 代码块包裹 JSON，不要在 JSON 后继续输出其他文字。

保持以下 JSON 格式不变：

[
{
"scientificName": "",
"commonName": "",
"confidence": 0.00,
"reason": "",
"missingEvidence": "",
"similarSpecies": []
}
]

JSON 字段填写规则：

1. scientificName：

   * 能可靠到种级时，填写标准拉丁学名；
   * 只能到属级时，填写 “Genus sp.”；
   * 只能到类群时，填写 “Group name group”；
   * 无法可靠判断时，填写空字符串。

2. commonName：

   * 种级可靠时，尽量填写中文正式名称或常见中文名；
   * 若没有可靠中文名，但有广泛使用的英文通用名，可填写英文 common name；
   * 若只能到属级，可填写稳定中文属名，例如“鹅膏属”“蘑菇属”；
   * 若只能到类群级，可填写稳定中文类群名，例如“牛肝菌类群”“多孔菌类群”“伞菌类群”；
   * 无法确认时填写空字符串；
   * 不得编造中文俗名、地方名、民间名、描述性名称或直译名称；
   * 不得包含食用性、毒性、药用性等暗示；
   * 不得把属名或类群名伪装成具体物种 commonName。

3. confidence：

   * 必须遵守置信度规则和封顶规则；
   * 使用 0.00–1.00 的数字；
   * 不得为了输出结果而提高置信度。

4. reason：

   * 简要说明支持该候选的可见证据；
   * 必须包含识别层级是否可靠；
   * 若不能到种级，必须说明原因；
   * 若为 unknown，必须说明无法可靠识别的主要原因；
   * 不得包含食用性或毒性判断。

5. missingEvidence：

   * 写明缺失的关键证据；
   * 例如：产孢面不可见、菌柄基部不可见、菌托无法观察、孢子印缺失、生境不明等。

6. similarSpecies：

   * 列出容易混淆的相似种、相似属或相似类群；
   * 若无法确定具体相似种，可写相似属或相似类群；
   * 不得虚构相似种；
   * 若无法列出可靠相似对象，可填写空数组。

如果无法可靠识别：

* 不要强行给出具体种名；
* scientificName 填空字符串，或填写保守的属级 / 类群级名称；
* commonName 填空字符串，或填写稳定中文属名 / 类群名；
* confidence 不得超过 0.30；
* reason 中必须说明无法可靠识别的原因；
* missingEvidence 中必须写明最关键的缺失证据。
""".trimIndent()
    }
}

internal fun buildRecognitionPrompt(userInfo: String): String {
    val normalizedInfo = userInfo.trim()
    val basePrompt = "请综合分析这些图片中的同一株蘑菇。"
    if (normalizedInfo.isEmpty()) return basePrompt

    return """
$basePrompt

【用户补充信息】
$normalizedInfo
【用户补充信息结束】

以上内容由用户主动提供。请在分析中显性注明并纳入参考；无法从图片验证的内容必须标记为“用户提供但图片不可验证”，不得改写为图片中直接观察到的事实。
    """.trimIndent()
}
