package com.yangzhiguo.mushroom.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

/**
 * 极简 HTTP 客户端。基于 JDK HttpURLConnection。
 * 特性：超时、指数退避重试、UA/Referer、跨协议 HTTP→HTTPS 重定向跟随。
 */
class ApiClient(
    private val baseUrl: String = "https://fungi.iflora.cn",
    private val maxRetries: Int = 3,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    private val tag = "ApiClient"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun fetchPage(current: Long, size: Int = 6): ApiResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/admin/kibspecimen/page?current=$current&size=$size&type=user"
        val body = getWithRetry(url)
        json.decodeFromString<ApiResponse>(body)
    }

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
                Log.w(tag, "GET $url failed (attempt ${attempt + 1}/${maxRetries + 1}): ${t.message} — retry in ${backoff}ms")
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
                instanceFollowRedirects = false  // 手动跟随跨协议
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Referer", "$baseUrl/")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
            try {
                val code = conn.responseCode
                if (code in 301..303 || code == 307 || code == 308) {
                    val loc = conn.getHeaderField("Location")
                        ?: throw RuntimeException("HTTP $code without Location header")
                    redirects++
                    if (redirects > 5) throw RuntimeException("Too many redirects")
                    currentUrl = URI.create(loc).toString()
                    continue
                }
                if (code !in 200..299) throw RuntimeException("HTTP $code")
                return reader(conn)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val base = 500L * (1L shl attempt.coerceAtMost(5))
        val jitter = (base * 0.25 * (Math.random() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(200L)
    }
    /**
     * 下载远端图片字节流，写入 [dest]。失败抛异常。
     * 内部自动跟随 HTTP→HTTPS 重定向。
     */
    suspend fun downloadBytes(remoteUrl: String, dest: java.io.File): Long = withContext(Dispatchers.IO) {
        val fullUrl = if (remoteUrl.startsWith("http")) remoteUrl else baseUrl + remoteUrl
        val bytes = doGet(fullUrl) { conn -> conn.inputStream.use { it.readBytes() } }
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        bytes.size.toLong()
    }

}
