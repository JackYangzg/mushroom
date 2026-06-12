package com.yangzhiguo.mushroom.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 极简 HTTP 客户端。基于 JDK HttpURLConnection。
 * 特性：超时、指数退避重试、UA/Referer、跨协议 HTTP→HTTPS 重定向跟随。
 */
class ApiClient(
    private val baseUrl: String = "https://fungi.iflora.cn",
    private val iNaturalistBaseUrl: String = "https://api.inaturalist.org",
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
                source.filter?.takeIf { it.isNotBlank() }?.let { kv ->
                    val idx = kv.indexOf('=')
                    if (idx > 0) {
                        params[kv.substring(0, idx)] = kv.substring(idx + 1)
                    }
                }
                val query = params.entries.joinToString("&") { (key, value) ->
                    "${encode(key)}=${encode(value)}"
                }
                json.decodeFromString<SpeciesApiResponse>(
                    getWithRetry("$baseUrl/admin/kibHome/getSpeciesList?$query"),
                ).data?.specimenSpeciesList.orEmpty()
            }
        }
    }

    suspend fun findPrimaryImageUrl(
        scientificName: String,
        sourceUrl: String? = null,
    ): String? = findImageUrls(scientificName, sourceUrl).firstOrNull()

    suspend fun findImageUrls(
        scientificName: String,
        sourceUrl: String? = null,
    ): List<String> = withContext(Dispatchers.IO) {
        if (scientificName.isBlank()) return@withContext emptyList()
        findImagesFromSourceUrl(sourceUrl).takeIf { it.isNotEmpty() }?.let {
            return@withContext it
        }

        val encodedName = URLEncoder.encode(scientificName.trim(), StandardCharsets.UTF_8.name())
        val url = "$baseUrl/admin/kibspecimen/page" +
            "?current=1&size=10&type=user&speciesLatin=$encodedName"
        val response = json.decodeFromString<ApiResponse>(getWithRetry(url))
        val exactRecords = response.data?.records.orEmpty().filter {
            it.speciesLatin?.trim().equals(scientificName.trim(), ignoreCase = true)
        }
        val iFloraImages = exactRecords.flatMap(::imageUrls).distinct()
        if (iFloraImages.isNotEmpty()) return@withContext iFloraImages
        findINaturalistImageUrl(scientificName)?.let(::listOf).orEmpty()
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

    private fun <T> doGet(
        url: String,
        accept: String = "application/json",
        reader: (HttpURLConnection) -> T,
    ): T {
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
                setRequestProperty("Accept", accept)
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

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    /**
     * 下载远端图片字节流，写入 [dest]。失败抛异常。
     * 内部自动跟随 HTTP→HTTPS 重定向。
     */
    suspend fun downloadBytes(remoteUrl: String, dest: java.io.File): Long = withContext(Dispatchers.IO) {
        val fullUrl = normalizeImageUrl(remoteUrl, baseUrl)
        val bytes = doGet(fullUrl, accept = "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5") {
            conn -> conn.inputStream.use { it.readBytes() }
        }
        require(isSupportedImage(bytes)) {
            "Response is not a supported image (${bytes.size} bytes)"
        }
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        bytes.size.toLong()
    }

    private fun imageUrls(specimen: Specimen?): List<String> {
        if (specimen == null) return emptyList()
        val result = mutableListOf<String>()
        val candidates = listOf(specimen.sysFileList, specimen.kibSpeciesPictures)
        for (element in candidates) {
            val array = element as? JsonArray ?: continue
            for (item in array) {
                val obj = item as? JsonObject ?: continue
                val path = listOf("url", "uf_src")
                    .firstNotNullOfOrNull { key ->
                        obj[key]?.let { value ->
                            (value as? JsonPrimitive)?.content
                        }?.takeIf { it.isNotBlank() }
                }
                if (path != null) {
                    result += normalizeImageUrl(path, baseUrl)
                }
            }
        }
        return result.distinct()
    }

    private suspend fun findImagesFromSourceUrl(sourceUrl: String?): List<String> {
        if (sourceUrl.isNullOrBlank()) return emptyList()
        val specimenId = extractSpecimenId(sourceUrl) ?: return emptyList()
        val detailUrl = "$baseUrl/admin/kibspecimen/$specimenId"
        val detail = runCatching {
            json.decodeFromString<SpecimenDetailResponse>(getWithRetry(detailUrl))
        }.getOrNull()
        return imageUrls(detail?.data)
    }

    private fun extractSpecimenId(sourceUrl: String): Long? {
        // DataSource.kt 写入的 SPECIMEN URL 是 `/specimenDetail/{id}`，
        // GENERAL_DIRECTORY 是 `/speciesDetail/{id}/...`；两种都要匹配。
        // 之前缺 `specimenDetail` 这一条，导致 SPECIMEN 源的回源补图永远拿不到 id。
        val patterns = listOf(
            Regex("""/specimenDetail/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""/speciesDetail/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""/kibspecimen/(\d+)""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(sourceUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }
    }

    private suspend fun findINaturalistImageUrl(scientificName: String): String? {
        val normalized = scientificName.trim()
        val isGenusQuery = normalized.endsWith(" sp.", ignoreCase = true) ||
            normalized.endsWith(" sp", ignoreCase = true)
        val queryName = if (isGenusQuery) normalized.substringBefore(" ") else normalized
        val rank = if (isGenusQuery) "genus" else "species"
        val encodedName = URLEncoder.encode(queryName, StandardCharsets.UTF_8.name())
        val url = "$iNaturalistBaseUrl/v1/taxa?q=$encodedName&rank=$rank&per_page=10"
        val root = json.parseToJsonElement(getWithRetry(url)).jsonObject
        val results = root["results"]?.jsonArray.orEmpty()
        val exact = results.firstOrNull { item ->
            item.jsonObject["name"]?.jsonPrimitive?.content
                ?.equals(queryName, ignoreCase = true) == true
        }?.jsonObject ?: return null
        val photo = exact["default_photo"]?.jsonObject ?: return null
        return photo["medium_url"]?.jsonPrimitive?.content
            ?: photo["url"]?.jsonPrimitive?.content
    }

    companion object {
        internal fun normalizeImageUrl(raw: String, baseUrl: String = "https://fungi.iflora.cn"): String {
            val trimmed = raw.trim()
            return when {
                trimmed.startsWith(CLOUD_FILE_HTTP, ignoreCase = true) ->
                    "https://${trimmed.substringAfter("://")}"
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "${baseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
            }
        }

        internal fun isSupportedImage(bytes: ByteArray): Boolean {
            if (bytes.size < 4) return false
            return isJpeg(bytes) ||
                isPng(bytes) ||
                isGif(bytes) ||
                isWebP(bytes) ||
                isAvif(bytes) ||
                isBmp(bytes)
        }

        private fun isJpeg(bytes: ByteArray): Boolean =
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

        private fun isPng(bytes: ByteArray): Boolean =
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes.copyOfRange(1, 4).contentEquals(byteArrayOf(0x50, 0x4E, 0x47))

        private fun isGif(bytes: ByteArray): Boolean =
            bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")

        private fun isWebP(bytes: ByteArray): Boolean =
            bytes.size >= 12 &&
                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

        private fun isAvif(bytes: ByteArray): Boolean =
            bytes.size >= 12 &&
                String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" &&
                String(bytes, 8, 4, Charsets.US_ASCII) in setOf("avif", "avis")

        private fun isBmp(bytes: ByteArray): Boolean =
            bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()

        private const val CLOUD_FILE_HTTP = "http://cloudfile.biotracks.cn/"
    }
}
