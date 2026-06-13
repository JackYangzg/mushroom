package com.yangzhiguo.mushroom.recognition

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMaxApiClientTest {

    @Test
    fun streamRecognize_sendsRequestAndEmitsCandidates() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    buildString {
                        appendLine("""data: {"choices":[{"delta":{"content":"正在观察可见特征"}}]}""")
                        appendLine()
                        appendLine(
                            """data: {"choices":[{"delta":{"content":"[{\"scientificName\":\"Amanita test\",\"commonName\":\"测试鹅膏\",\"confidence\":0.8,\"reason\":\"白色菌盖\"}]"}}]}""",
                        )
                        appendLine()
                        appendLine("data: [DONE]")
                        appendLine()
                    },
                ),
        )
        server.start()

        try {
            val client = MiniMaxApiClient(
                apiKey = "test-key",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                model = "test-model",
                connectTimeoutMs = 1_000,
                readTimeoutMs = 1_000,
            )

            val events = withTimeout(3_000) {
                client.streamRecognize(
                    listOf(
                        "data:image/jpeg;base64,AA==",
                        "data:image/jpeg;base64,BB==",
                    ),
                ).toList()
            }

            val request = server.takeRequest()
            assertEquals("/v1/text/chatcompletion_v2", request.path)
            assertEquals("Bearer test-key", request.getHeader("Authorization"))
            val requestBody = request.body.readUtf8()
            assertTrue(requestBody.contains("data:image/jpeg;base64,AA=="))
            assertTrue(requestBody.contains("data:image/jpeg;base64,BB=="))
            assertEquals(2, "\"type\":\"image_url\"".toRegex().findAll(requestBody).count())
            assertTrue(requestBody.contains("专业真菌学图像鉴定助手"))
            assertTrue(requestBody.contains("你的目标是“保守鉴定”，不是“尽量给出名字”"))
            assertTrue(requestBody.contains("可鉴定性结论"))
            assertTrue(requestBody.contains("菌托、菌环、基部均不可见"))
            assertTrue(requestBody.contains("\\\"missingEvidence\\\""))
            assertTrue(requestBody.contains("\\\"similarSpecies\\\""))
            val final = events.filterIsInstance<StreamEvent.FinalCandidates>().single()
            assertEquals("Amanita test", final.candidates.single().scientificName)
            assertTrue(events.any { it is StreamEvent.ThinkingChunk })
        } finally {
            server.shutdown()
        }
    }
}
