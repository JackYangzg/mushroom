package com.yangzhiguo.mushroom.recognition

import android.util.Log
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据访问门面（设计文档 §4.1 Data Layer）。
 *
 * 职责：
 *  1. 优先查本地索引（MushroomIndex）
 *  2. 本地未命中 → 构造远端 URL（本期 MVP：直接走 mushroom.iflora.cn 3D URL，
 *     实际 WebView 抓取在 UI 层完成，Repository 只负责 URL 与缓存）
 *  3. 远端详情经 DetailLruCache 缓存（50 条 / 7d）
 *  4. 从 Room 数据库拉取首张图片供 UI 展示（设计文档 §3.4 字段）
 *
 * 关键设计：Repository 不阻塞、不发网络请求。
 *  - 本地查索引是同步、纯内存
 *  - 远端"拉取"在 MVP 中退化为"返回 URL + 缓存最近一次"
 *  - 数据库查询走 IO 调度器，但首张图片加载在调用方协程内挂起即可
 */
@Singleton
class MushroomRepository @Inject constructor(
    private val index: MushroomIndex,
    private val cache: DetailLruCache = DetailLruCache(),
    private val speciesDao: SpeciesDao? = null,
) {
    private val tag = "MushroomRepository"
    private val imageJson = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed class LookupResult {
        /** 本地命中，直接渲染 */
        data class Hit(val mushroom: LocalMushroom) : LookupResult()
        /** 本地未命中，需走远端 */
        data class Miss(
            /** 建议优先尝试的科学名（取候选 list 第 1 个的 normalized form） */
            val fallbackName: String,
        ) : LookupResult()
    }

    /**
     * 第一站：本地查表。命中返回 Hit，否则返回 Miss（携带 fallback name 供
     * 远端 WebView 打开）。
     */
    fun lookup(candidates: List<Candidate>): LookupResult {
        val hit = index.lookup(candidates)
        return if (hit != null) LookupResult.Hit(hit)
        else LookupResult.Miss(fallbackName = candidates.firstOrNull()?.scientificName.orEmpty())
    }

    /**
     * 远端详情入口。先查 LRU 缓存，未命中返回 null（调用方应打开 WebView 拉取，
     * 拉取完成后调用 [putCachedDetail] 回写）。
     */
    fun getCachedDetail(scientificName: String): CachedDetail? =
        if (scientificName.isBlank()) null else cache.get(scientificName)

    /** 把远端拉到的详情写回缓存。 */
    fun putCachedDetail(scientificName: String, mushroom: LocalMushroom, rawJson: String? = null) {
        if (scientificName.isBlank()) return
        cache.put(
            scientificName,
            CachedDetail(
                mushroom = mushroom,
                fetchedAt = System.currentTimeMillis(),
                rawJson = rawJson,
            ),
        )
    }

    /**
     * 从 Room 数据库拉取指定学名的首张图片 URL。
     *
     * - 若 `SpeciesEntity.images` 是 JSON 数组，解析取 [0].url
     * - 找不到任何记录 / 解析失败 → 返回 null（调用方应优雅降级）
     * - 该方法是 suspend（Room 查询在 IO 线程），调用方需在协程中执行
     */
    suspend fun findFirstImage(scientificName: String): String? = withContext(Dispatchers.IO) {
        if (scientificName.isBlank() || speciesDao == null) return@withContext null
        val entity = runCatching { speciesDao.findByScientificName(scientificName) }
            .onFailure { Log.w(tag, "findByScientificName($scientificName) failed: ${it.message}") }
            .getOrNull() ?: return@withContext null
        parseFirstImageUrl(entity.images)
    }

    /** 解析 SpeciesEntity.images（JSON 字符串）取首张图的 url。 */
    private fun parseFirstImageUrl(imagesJson: String): String? {
        if (imagesJson.isBlank()) return null
        return try {
            val list = imageJson.decodeFromString<List<ImageDto>>(imagesJson)
            list.firstOrNull()?.url?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(tag, "parseFirstImageUrl failed: ${t.message}")
            null
        }
    }

    /**
     * 3D 查看器 URL（mushroom.iflora.cn，公开学术 3D 平台）。
     * 设计文档 §3.5：永远 work，是兜底入口。
     */
    fun build3DUrl(scientificName: String): String {
        val encoded = URLEncoder.encode(scientificName.trim(), Charsets.UTF_8.name())
        return "$IFLORA_3D_BASE/?search=$encoded"
    }

    /**
     * iflora 详情页 URL（fungi.iflora.cn）。
     * MVP：先用 3D URL 作为兜底；后续接入 speciesDetail/{ID} 模板需要
     * scientificName → remoteId 的映射表（远端 SPA 抓取后回填）。
     */
    fun buildDetailUrl(scientificName: String): String = build3DUrl(scientificName)

    @Serializable
    private data class ImageDto(val url: String = "")

    companion object {
        const val IFLORA_3D_BASE = "https://mushroom.iflora.cn"
        const val IFLORA_DETAIL_BASE = "https://fungi.iflora.cn"
    }
}
