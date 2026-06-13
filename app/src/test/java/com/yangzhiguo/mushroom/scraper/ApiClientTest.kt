package com.yangzhiguo.mushroom.scraper

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApiClientTest {

    @Test
    fun specimenSourceUsesSpecimenPageEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"records":[{"id":42,"speciesLatin":"Amanita sp."}]}}""",
            ),
        )
        server.start()
        try {
            val client = ApiClient(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                maxRetries = 0,
            )

            val records = client.fetchPage(DataSource.SPECIMEN, page = 2, pageSize = 50)

            assertEquals(listOf(42L), records.map { it.id })
            assertEquals(
                "/admin/kibspecimen/page?current=2&size=50&type=user",
                server.takeRequest().path,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun speciesDetail_acceptsNumericCalmSeed() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"kibSpecimen":{"id":6,"speciesLatin":"Termitomyces fragilis","calmSeed":0}}}""",
            ),
        )
        server.start()
        try {
            val client = ApiClient(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                maxRetries = 0,
            )

            val detail = client.fetchSpeciesDetail(6)

            assertEquals("0", detail?.calmSeed)
            assertEquals(
                "/admin/kibHome/getSpeciesInfoBySpeciesLatin?speciesId=6",
                server.takeRequest().path,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun legacyCloudFileHttpUrlIsUpgradedToHttps() {
        assertEquals(
            "https://cloudfile.biotracks.cn/user_thumb/a.jpg!bio123456",
            ApiClient.normalizeImageUrl(
                "http://cloudfile.biotracks.cn/user_thumb/a.jpg!bio123456",
            ),
        )
    }

    @Test
    fun imageSignatureValidationRejectsHtmlErrorPages() {
        assertFalse(ApiClient.isSupportedImage("<html>not found</html>".toByteArray()))
        assertTrue(
            ApiClient.isSupportedImage(
                byteArrayOf(
                    0xFF.toByte(),
                    0xD8.toByte(),
                    0xFF.toByte(),
                    0xE0.toByte(),
                ),
            ),
        )
    }

    @Test
    fun downloadBytesRejectsNonImageResponseWithoutCachingIt() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html>expired image link</html>"),
        )
        server.start()
        val target = File.createTempFile("mushroom-invalid-image", ".jpg").apply { delete() }

        try {
            val client = ApiClient(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                maxRetries = 0,
                connectTimeoutMs = 1_000,
                readTimeoutMs = 1_000,
            )

            val result = runCatching {
                client.downloadBytes(server.url("/expired.jpg").toString(), target)
            }

            assertTrue(result.isFailure)
            assertFalse(target.exists())
        } finally {
            target.delete()
            server.shutdown()
        }
    }

    @Test
    fun findPrimaryImageUrl_queriesScientificNameAndReturnsAbsoluteUrl() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "code": 0,
                  "data": {
                    "records": [
                      {
                        "id": 9458,
                        "speciesLatin": "Amanita virosa",
                        "sysFileList": [
                          {"url": "/admin/sys-file/local/amanita.jpg"}
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        server.start()

        try {
            val baseUrl = server.url("/").toString().removeSuffix("/")
            val client = ApiClient(
                baseUrl = baseUrl,
                maxRetries = 0,
                connectTimeoutMs = 1_000,
                readTimeoutMs = 1_000,
            )

            val imageUrl = client.findPrimaryImageUrl("Amanita virosa")

            assertEquals("$baseUrl/admin/sys-file/local/amanita.jpg", imageUrl)
            assertEquals(
                "/admin/kibspecimen/page?current=1&size=10&type=user&speciesLatin=Amanita+virosa",
                server.takeRequest().path,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun findPrimaryImageUrl_usesSpecimenIdFromDatabaseSourceLinkFirst() = runBlocking {
        val server = MockWebServer()
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "code": 0,
                      "data": {
                        "kibSpecimen": {
                          "id": 9458,
                          "speciesLatin": "Amanita virosa",
                          "sysFileList": [
                            {"url": "/admin/sys-file/local/from-detail-link.jpg"},
                            {"url": "/admin/sys-file/local/second-image.jpg"}
                          ],
                          "kibSpeciesPictures": [
                            {"uf_src": "/admin/sys-file/local/from-detail-link.jpg"}
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
            ),
        )
        server.start()

        try {
            val baseUrl = server.url("/").toString().removeSuffix("/")
            val client = ApiClient(
                baseUrl = baseUrl,
                iNaturalistBaseUrl = baseUrl,
                maxRetries = 0,
                connectTimeoutMs = 1_000,
                readTimeoutMs = 1_000,
            )

            val imageUrls = client.findImageUrls(
                scientificName = "Amanita virosa",
                sourceUrl = "https://fungi.iflora.cn/#/speciesDetail/9458/Amanita%20virosa/list",
            )

            assertEquals(
                listOf(
                    "$baseUrl/admin/sys-file/local/from-detail-link.jpg",
                    "$baseUrl/admin/sys-file/local/second-image.jpg",
                ),
                imageUrls,
            )
            assertEquals(
                "/admin/kibHome/getSpeciesInfoBySpeciesLatin?speciesId=9458",
                server.takeRequest().path,
            )
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    /**
     * 回归测试:DataSource.kt 写入的 sourceUrl 同时支持 `/speciesDetail/{id}` 与
     * `/specimenDetail/{id}` 两种模式(历史 `/specimenDetail/` 链接仍可回源)。
     */
    @Test
    fun findImageUrls_routesSpecimenDetailSourceUrlToDetailApi() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "code": 0,
                  "data": {
                    "id": 33,
                    "speciesLatin": "Inocybe assimilata",
                    "sysFileList": [],
                    "kibSpeciesPictures": [
                      {"uf_src": "http://cloudfile.biotracks.cn/a.jpg"},
                      {"uf_src": "http://cloudfile.biotracks.cn/b.jpg"},
                      {"uf_src": "http://cloudfile.biotracks.cn/c.jpg"},
                      {"uf_src": "http://cloudfile.biotracks.cn/d.jpg"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        server.start()

        try {
            val baseUrl = server.url("/").toString().removeSuffix("/")
            val client = ApiClient(
                baseUrl = baseUrl,
                iNaturalistBaseUrl = baseUrl,
                maxRetries = 0,
                connectTimeoutMs = 1_000,
                readTimeoutMs = 1_000,
            )

            val imageUrls = client.findImageUrls(
                scientificName = "Inocybe assimilata",
                sourceUrl = "https://fungi.iflora.cn/#/specimenDetail/33",
            )

            assertEquals(
                listOf(
                    "https://cloudfile.biotracks.cn/a.jpg",
                    "https://cloudfile.biotracks.cn/b.jpg",
                    "https://cloudfile.biotracks.cn/c.jpg",
                    "https://cloudfile.biotracks.cn/d.jpg",
                ),
                imageUrls,
            )
            assertEquals("/admin/kibspecimen/33", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchSpecimenDetail_usesSpecimenIdFromSourceUrl() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "code": 0,
                  "data": {
                    "id": 33,
                    "speciesLatin": "Inocybe assimilata",
                    "speciesChinese": "拟丝盖伞"
                  }
                }
                """.trimIndent(),
            ),
        )
        server.start()

        try {
            val client = ApiClient(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                maxRetries = 0,
            )

            val detail = client.fetchSpecimenDetail(
                "https://fungi.iflora.cn/#/specimenDetail/33",
            )

            assertEquals(33L, detail?.id)
            assertEquals("Inocybe assimilata", detail?.speciesLatin)
            assertEquals("/admin/kibspecimen/33", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun findPrimaryImageUrl_fallsBackToINaturalistWhenIFloraHasNoImage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"records":[]}}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "results": [
                    {
                      "name": "Amanita muscaria",
                      "default_photo": {
                        "medium_url": "https://images.example/amanita-medium.jpg"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.start()

        try {
            val baseUrl = server.url("/").toString().removeSuffix("/")
            val client = ApiClient(
                baseUrl = baseUrl,
                iNaturalistBaseUrl = baseUrl,
                maxRetries = 0,
                connectTimeoutMs = 1_000,
                readTimeoutMs = 1_000,
            )

            val imageUrl = client.findPrimaryImageUrl("Amanita muscaria")

            assertEquals("https://images.example/amanita-medium.jpg", imageUrl)
            server.takeRequest()
            assertEquals(
                "/v1/taxa?q=Amanita+muscaria&rank=species&per_page=10",
                server.takeRequest().path,
            )
        } finally {
            server.shutdown()
        }
    }
}
