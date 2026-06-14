package com.yangzhiguo.mushroom.recognition

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoApiClientTest {

    @Test
    fun streamRecognize_sendsResponsesRequestAndEmitsCandidates() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    buildString {
                        appendLine("event: response.output_text.delta")
                        appendLine(
                            """data: {"type":"response.output_text.delta","delta":"正在观察可见特征"}""",
                        )
                        appendLine()
                        appendLine("event: response.output_text.delta")
                        appendLine(
                            """data: {"type":"response.output_text.delta","delta":"[{\"scientificName\":\"Amanita test\",\"commonName\":\"测试鹅膏\",\"confidence\":0.8,\"reason\":\"白色菌盖\"}]"}""",
                        )
                        appendLine()
                        appendLine("event: response.completed")
                        appendLine("""data: {"type":"response.completed"}""")
                        appendLine()
                    },
                ),
        )
        server.start()

        try {
            val client = DoubaoApiClient(
                apiKey = "ark-test-key",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                model = "test-model",
                connectTimeoutMs = 1_000,
                readTimeoutMs = 1_000,
            )

            val events = withTimeout(3_000) {
                client.streamRecognize(
                    imageDataUrls = listOf(
                        "data:image/jpeg;base64,AA==",
                        "data:image/jpeg;base64,BB==",
                    ),
                    userPrompt = "请综合分析这些图片中的同一株蘑菇。",
                    systemPrompt = MiniMaxApiClient.DEFAULT_SYSTEM_PROMPT,
                ).toList()
            }

            val request = server.takeRequest()
            assertEquals("/api/v3/responses", request.path)
            assertEquals("Bearer ark-test-key", request.getHeader("Authorization"))
            val requestBody = request.body.readUtf8()
            assertTrue(requestBody.contains("\"model\":\"test-model\""))
            assertTrue(requestBody.contains("\"instructions\":"))
            assertEquals(2, "\"type\":\"input_image\"".toRegex().findAll(requestBody).count())
            assertTrue(requestBody.contains("\"type\":\"input_text\""))
            assertTrue(requestBody.contains("\"image_url\":\"data:image/jpeg;base64,AA==\""))
            val final = events.filterIsInstance<StreamEvent.FinalCandidates>().single()
            assertEquals("Amanita test", final.candidates.single().scientificName)
            assertTrue(events.any { it is StreamEvent.ThinkingChunk })
        } finally {
            server.shutdown()
        }
    }
}
