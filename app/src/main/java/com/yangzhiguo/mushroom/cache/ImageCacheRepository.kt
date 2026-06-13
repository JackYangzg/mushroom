package com.yangzhiguo.mushroom.cache

import android.content.Context
import android.util.Log
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.scraper.ApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片缓存仓库。
 *
 * v10:把 [specimenId] 全部改名 [mushroomId],语义是 iflora 业务 id 而非 Room PK。
 * 缓存目录命名仍用业务 id 作为命名空间(`specimen_images_v2/{mushroomId}/`),保证
 * resync 时 PK 变了但业务 id 不变,缓存文件继续命中。
 */
@Singleton
class ImageCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiClient,
    private val dao: SpeciesDao,
) {
    private val tag = "ImageCache"
    private val inflight = mutableMapOf<Int, Mutex>()

    suspend fun getOrFetchThumbnail(
        mushroomId: Int,
        remoteUrl: String?,
        scientificName: String?,
        sourceUrl: String?,
    ): File? = withSpeciesLock(mushroomId) {
        cachedFiles(mushroomId).firstOrNull()?.let { return@withSpeciesLock it }
        val urls = resolveRemoteUrls(mushroomId, remoteUrl, scientificName, sourceUrl)
        urls.forEachIndexed { index, url ->
            download(mushroomId, url, index)?.let { file ->
                dao.updateImageUrl(mushroomId, url)
                dao.updateImagePath(mushroomId, relativePath(file))
                return@withSpeciesLock file
            }
        }
        null
    }

    suspend fun getOrFetchAll(
        mushroomId: Int,
        remoteUrl: String?,
        scientificName: String?,
        sourceUrl: String?,
    ): List<File> = withSpeciesLock(mushroomId) {
        val cached = cachedFiles(mushroomId)
        if (completionMarker(mushroomId).exists() && cached.isNotEmpty()) {
            return@withSpeciesLock cached
        }

        val urls = resolveRemoteUrls(mushroomId, remoteUrl, scientificName, sourceUrl)
        if (urls.isEmpty()) return@withSpeciesLock cached

        val downloaded = urls.mapIndexedNotNull { index, url ->
            val target = targetFile(mushroomId, url, index)
            val file = when {
                target.exists() && target.length() > 0 -> target
                else -> download(mushroomId, url, index)
            }
            file?.let { url to it }
        }
        if (downloaded.size == urls.size && downloaded.isNotEmpty()) {
            completionMarker(mushroomId).apply {
                parentFile?.mkdirs()
                writeText(urls.joinToString("\n"))
            }
        } else {
            completionMarker(mushroomId).delete()
        }
        if (downloaded.isNotEmpty()) {
            val (successfulUrl, firstFile) = downloaded.first()
            dao.updateImageUrl(mushroomId, successfulUrl)
            dao.updateImagePath(mushroomId, relativePath(firstFile))
        }
        val files = downloaded.map { it.second }
        files.ifEmpty { cachedFiles(mushroomId) }
    }

    /** Compatibility for older single-image callers. */
    suspend fun getOrFetch(
        mushroomId: Int,
        remoteUrl: String?,
        scientificName: String? = null,
        sourceUrl: String? = null,
    ): File? = getOrFetchThumbnail(mushroomId, remoteUrl, scientificName, sourceUrl)

    private suspend fun resolveRemoteUrls(
        mushroomId: Int,
        remoteUrl: String?,
        scientificName: String?,
        sourceUrl: String?,
    ): List<String> {
        val entity = dao.findByMushroomId(mushroomId)
        // 优先:同步时已经写入 entity.images 的多图数组(离线可用)。
        val persisted = decodePersistedImages(entity)
        // 兜底:单图 entity.imageUrl + 调用方临时传入的 remoteUrl。
        val singleHints = listOfNotNull(entity?.imageUrl, remoteUrl).filter { it.isNotBlank() }
        val localKnown = (persisted + singleHints)
            .map { ApiClient.normalizeImageUrl(it) }
            .distinct()

        // 只在本地完全没有线索时,或本地只有单图时才上网补图——避免每次详情页都打一次 API。
        val shouldDiscover = !scientificName.isNullOrBlank() && localKnown.size < MIN_LOCAL_BEFORE_NETWORK
        val discovered = if (shouldDiscover) {
            runCatching { api.findImageUrls(scientificName!!, sourceUrl) }
                .onFailure { Log.w(tag, "image lookup failed for $scientificName: ${it.message}") }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // 本地的排前面(缓存路径稳定),网络新发现的接后面用于补全。
        return (localKnown + discovered.map { ApiClient.normalizeImageUrl(it) }).distinct()
    }

    private fun decodePersistedImages(entity: SpeciesEntity?): List<String> {
        val raw = entity?.images?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val url = arr.optString(i).takeIf { it.isNotBlank() } ?: continue
                    add(url)
                }
            }
        }.onFailure {
            Log.w(tag, "failed to decode SpeciesEntity.images for mushroom=${entity.mushroomId}: ${it.message}")
        }.getOrDefault(emptyList())
    }

    private suspend fun download(mushroomId: Int, url: String, index: Int): File? {
        val target = targetFile(mushroomId, url, index)
        return try {
            api.downloadBytes(url, target)
            if (target.length() > 0) {
                Log.i(tag, "cached image ${target.name} (${target.length()} bytes)")
                target
            } else {
                target.delete()
                null
            }
        } catch (t: Throwable) {
            target.delete()
            Log.w(tag, "image download failed for mushroom $mushroomId: ${t.message}")
            null
        }
    }

    private fun cachedFiles(mushroomId: Int): List<File> =
        cacheDir(mushroomId)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .mapNotNull { file ->
                if (isValidCachedImage(file)) file else {
                    file.delete()
                    null
                }
            }
            .sortedBy { it.name }

    private fun isValidCachedImage(file: File): Boolean {
        if (file.length() <= 0) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(32)
                val count = input.read(header)
                ApiClient.isSupportedImage(if (count > 0) header.copyOf(count) else byteArrayOf())
            }
        }.getOrDefault(false)
    }

    private fun targetFile(mushroomId: Int, url: String, index: Int): File {
        val extension = url.substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "jpg")
            .lowercase()
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?: "jpg"
        return File(cacheDir(mushroomId), "%03d_%08x.%s".format(index, url.hashCode(), extension))
    }

    private fun cacheDir(mushroomId: Int): File =
        File(context.filesDir, "$CACHE_NAMESPACE/$mushroomId").apply { mkdirs() }

    private fun completionMarker(mushroomId: Int): File =
        File(cacheDir(mushroomId), ".complete")

    private fun relativePath(file: File): String =
        file.relativeTo(context.filesDir).path

    private suspend fun <T> withSpeciesLock(mushroomId: Int, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            val mutex = synchronized(inflight) {
                inflight.getOrPut(mushroomId) { Mutex() }
            }
            mutex.withLock { block() }
        }

    companion object {
        /**
         * v1/v2 缓存可能存在 PK 漂移造成的目录,但目录命名用了业务 id,resync
         * 时业务 id 不变 → 文件继续命中。命名空间保留 `specimen_images_v2`
         * 是为兼容已下载的旧版本缓存文件(避免重新下载)。
         */
        private const val CACHE_NAMESPACE = "specimen_images_v2"

        /**
         * 本地已知 URL 至少有这个数量时跳过网络回源。设成 2 而不是 1,是因为
         * `entity.imageUrl` 这种"主图"为了显示缩略图通常一定有;只有 1 张时
         * 我们仍然希望尝试通过详情 API 拿到剩余几张。
         */
        private const val MIN_LOCAL_BEFORE_NETWORK = 2
    }
}