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
 * 火山方舟豆包 Responses API 流式客户端。
 */
@OptIn(ExperimentalSerializationApi::class)
class DoubaoApiClient(
    private val apiKey: String = BuildConfig.ARK_API_KEY,
    private val baseUrl: String = BuildConfig.ARK_API_BASE,
    private val model: String = BuildConfig.ARK_MODEL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : RecognitionApiClient {
    private val tag = "DoubaoApiClient"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

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

        val url = URI.create("${baseUrl.trimEnd('/')}/api/v3/responses").toURL()
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
                    throw RuntimeException("豆包 HTTP $code: ${errBody.take(500)}")
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
                        if (pending.isNotEmpty()) {
                            val frameJson = pending.toString()
                            pending = StringBuilder()
                            if (frameJson == "[DONE]") return@useLines
                            extractOutputTextDelta(frameJson)?.let { delta ->
                                parser.consume(delta).forEach { trySend(it) }
                            }
                        }
                    }
                    line.startsWith("data:") -> {
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isNotEmpty()) pending.append(payload)
                    }
                }
            }
        }
        parser.flush().forEach { trySend(it) }
    }

    private fun extractOutputTextDelta(frameJson: String): String? {
        return try {
            val frame = json.decodeFromString<SseFrame>(frameJson)
            if (frame.type == "response.output_text.delta") frame.delta else null
        } catch (t: Throwable) {
            Log.w(tag, "skip malformed SSE frame: ${t.message}")
            null
        }
    }

    private fun buildRequestBody(
        systemPrompt: String,
        userPrompt: String,
        imageDataUrls: List<String>,
    ): String {
        val body = ResponsesRequest(
            model = model,
            instructions = systemPrompt,
            stream = true,
            input = listOf(
                Message(
                    role = "user",
                    content = imageDataUrls.filter { it.isNotBlank() }.map(ContentPart::Image) +
                        ContentPart.Text(userPrompt),
                ),
            ),
        )
        return json.encodeToString(ResponsesRequest.serializer(), body)
    }

    @Serializable
    private data class ResponsesRequest(
        val model: String,
        val instructions: String,
        val stream: Boolean,
        val input: List<Message>,
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
        @SerialName("image_url") val imageUrl: String? = null,
    ) {
        companion object {
            fun Text(value: String) = ContentPart(type = "input_text", text = value)
            fun Image(value: String) = ContentPart(type = "input_image", imageUrl = value)
        }
    }

    @Serializable
    private data class SseFrame(
        val type: String = "",
        val delta: String? = null,
    )
}
