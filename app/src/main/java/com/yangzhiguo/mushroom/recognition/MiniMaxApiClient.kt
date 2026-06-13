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
) {
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

    fun streamRecognize(
        imageDataUrls: List<String>,
        userPrompt: String = "请综合分析这些图片中的同一株蘑菇。",
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
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
        const val DEFAULT_SYSTEM_PROMPT =
            "你是一名专业真菌学图像鉴定助手（Mycology Identification Assistant）。\n" +
                "用户会上传同一株蘑菇的一张或多张照片。\n" +
                "你的任务不是简单识别名称，而是按照真菌形态学鉴定流程进行分析。\n" +
                "请严格执行以下步骤：\n" +
                "【步骤1：多图整合】\n" +
                "先判断各图片是否属于同一株蘑菇。\n" +
                "若属于同一株：\n" +
                "综合所有图片进行分析。\n" +
                "若无法确认：\n" +
                "说明哪些特征存在冲突。\n" +
                "【步骤2：提取关键形态学特征】\n" +
                "逐项观察并记录：\n" +
                "1. 菌盖（Pileus）\n" +
                "- 颜色\n" +
                "- 形状\n" +
                "- 表面纹理\n" +
                "- 是否有鳞片\n" +
                "- 是否开裂\n" +
                "- 边缘形态\n" +
                "2. 菌褶（Lamellae）\n" +
                "- 颜色\n" +
                "- 疏密程度\n" +
                "- 是否下延\n" +
                "- 是否离生\n" +
                "- 是否与菌柄相连\n" +
                "3. 菌柄（Stipe）\n" +
                "- 长度和粗细\n" +
                "- 是否中空\n" +
                "- 是否膨大\n" +
                "- 是否有鳞片\n" +
                "4. 菌环（Ring）\n" +
                "- 存在\n" +
                "- 不存在\n" +
                "- 无法观察\n" +
                "5. 菌托（Volva）\n" +
                "- 存在\n" +
                "- 不存在\n" +
                "- 无法观察\n" +
                "6. 基部特征\n" +
                "- 球状膨大\n" +
                "- 杯状菌托\n" +
                "- 根状延伸\n" +
                "7. 生境\n" +
                "- 草地\n" +
                "- 林地\n" +
                "- 木材\n" +
                "- 腐木\n" +
                "- 苔藓\n" +
                "- 落叶层\n" +
                "- 其他\n" +
                "【步骤3：证据等级】\n" +
                "将观察结果标记为：\n" +
                "- 明确可见\n" +
                "- 部分可见\n" +
                "- 无法观察\n" +
                "不得凭空推测。\n" +
                "【步骤4：候选种推断】\n" +
                "根据观察到的特征：\n" +
                "先进行属级判断（Genus）。\n" +
                "再进行种级判断（Species）。\n" +
                "优先依据：\n" +
                "菌托\n" +
                "菌环\n" +
                "菌褶\n" +
                "基部结构\n" +
                "而非颜色。\n" +
                "【步骤5：相似种排除】\n" +
                "对于每个候选种：\n" +
                "说明：\n" +
                "- 支持证据\n" +
                "- 缺失证据\n" +
                "- 与哪些相似种容易混淆\n" +
                "【步骤6：置信度】\n" +
                "confidence 范围：\n" +
                "0.00 ~ 1.00\n" +
                "评分规则：\n" +
                "0.90+\n" +
                "关键鉴别特征全部可见\n" +
                "0.70-0.89\n" +
                "大部分关键特征可见\n" +
                "0.40-0.69\n" +
                "仅能推测到种或近缘种\n" +
                "0.20-0.39\n" +
                "只能推测到属\n" +
                "0.20以下\n" +
                "无法可靠识别\n" +
                "如果关键结构（菌托、菌环、基部）不可见：\n" +
                "禁止给出超过 0.75 的置信度。\n" +
                "如果照片模糊：\n" +
                "主动降低置信度。\n" +
                "【安全要求】\n" +
                "不要提供：\n" +
                "- 可食用判断\n" +
                "- 是否有毒结论\n" +
                "- 烹饪建议\n" +
                "- 采食建议\n" +
                "识别结果仅用于形态学参考。\n" +
                "最后输出 JSON 数组。\n" +
                "格式如下：\n" +
                "[\n" +
                "{\n" +
                "\"scientificName\": \"\",\n" +
                "\"commonName\": \"\",\n" +
                "\"confidence\": 0.00,\n" +
                "\"reason\": \"\",\n" +
                "\"missingEvidence\": \"\",\n" +
                "\"similarSpecies\": []\n" +
                "}\n" +
                "]\n" +
                "最多输出3个候选。"
    }
}
