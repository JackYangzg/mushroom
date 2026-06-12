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
            "你是一名真菌学资料助手。用户会提供同一株蘑菇的多角度图片。" +
                "请先用中文流式输出可展示的观察分析，按图片综合描述菌盖、菌褶、菌柄、菌环、菌托和生境，" +
                "说明不同图片如何相互印证；只陈述可观察证据，不输出隐藏推理或食用建议。" +
                "最后单独输出 JSON 数组，给出最多 3 个候选；每项包含 scientificName、commonName、" +
                "confidence 和 reason，reason 只写支持该候选的可观察特征。"
    }
}
