package com.yangzhiguo.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 极简 HTTP 客户端：用 JDK 自带 HttpURLConnection。
 * 包含：超时、限速、指数退避重试、UA / Referer、手动跟随 HTTP→HTTPS 重定向。
 *
 * 本爬虫**不下载图片**；仅抓取 specimen 列表 JSON，DB 中以 source_url 记录反查链接。
 */
class ApiClient(
    private val baseUrl: String = "https://fungi.iflora.cn",
    private val userAgent: String = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    private val referer: String = "https://fungi.iflora.cn/",
    private val maxRetries: Int = 4,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    private val log = LoggerFactory.getLogger(ApiClient::class.java)
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * 抓取一页 specimen 列表。`current` 为 1-indexed 页码，`size` 默认 6（API 行为）。
     */
    suspend fun fetchPage(current: Long, size: Int = 6): ApiResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/admin/kibspecimen/page?current=$current&size=$size&type=user"
        val body = getWithRetry(url)
        json.decodeFromString<ApiResponse>(body)
    }

    suspend fun fetchPage(
        source: DataSource,
        page: Int,
        pageSize: Int = 6,
    ): List<Specimen> = withContext(Dispatchers.IO) {
        when (source.kind) {
            DataSource.Kind.SPECIMEN ->
                fetchPage(page.toLong(), pageSize).data?.records.orEmpty()
            DataSource.Kind.SPECIES -> {
                val params = linkedMapOf(
                    "page" to page.toString(),
                    "pageSize" to pageSize.toString(),
                )
                val query = params.entries.joinToString("&") { (key, value) ->
                    "${encode(key)}=${encode(value)}"
                }
                val body = getWithRetry("$baseUrl/admin/kibHome/getSpeciesList?$query")
                json.decodeFromString<SpeciesApiResponse>(body)
                    .data
                    ?.specimenSpeciesList
                    .orEmpty()
            }
        }
    }

    // ── 内部 ─────────────────────────────────────────────────────────────

    private suspend fun getWithRetry(url: String): String {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= maxRetries) {
            try {
                return doGet(url) { conn ->
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            } catch (t: Throwable) {
                lastError = t
                val backoff = backoffMs(attempt)
                log.warn("GET {} failed (attempt {}/{}): {} — retry in {}ms",
                    url, attempt + 1, maxRetries + 1, t.message, backoff)
                delay(backoff)
                attempt++
            }
        }
        throw RuntimeException("GET $url failed after ${maxRetries + 1} attempts", lastError)
    }

    private fun <T> doGet(url: String, reader: (HttpURLConnection) -> T): T {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val conn = (URI(currentUrl).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                // instanceFollowRedirects 不会跨协议跟随（HTTP→HTTPS）；这里手动处理
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", "application/json, image/*,*/*;q=0.8")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
            try {
                val code = conn.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    val loc = conn.getHeaderField("Location")
                        ?: throw RuntimeException("HTTP $code without Location header")
                    redirects++
                    if (redirects > 5) throw RuntimeException("Too many redirects (>$redirects)")
                    currentUrl = URI.create(loc).toString()
                    log.debug("redirect {} -> {}", code, currentUrl)
                    continue
                }
                if (code !in 200..299) {
                    val errMsg = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }
                        .getOrNull()?.take(300) ?: "(no body)"
                    throw RuntimeException("HTTP $code: $errMsg")
                }
                return reader(conn)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun backoffMs(attempt: Int): Long {
        // 指数退避：500ms, 1s, 2s, 4s，加 ±25% 抖动
        val base = 500L * (1L shl attempt.coerceAtMost(6))
        val jitter = (base * 0.25 * (Math.random() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(200L)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
