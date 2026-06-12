package com.yangzhiguo.mushroom.recognition

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地蘑菇索引（设计文档 §3.3）。
 *
 *  - 数据源：assets/mushroom_index.json（MVP 阶段内置 10-20 种常见种）
 *  - 启动时不阻塞，第一次 lookup 时 lazy 加载
 *  - 内存结构：Map<归一化 scientificName, LocalMushroom>，5000 条以内走 map 即可
 *    （> 5000 条按设计文档切到 SQLite FTS5，本期不实现）
 *
 * 索引加载失败不应 crash App——返回空 map，所有候选都会 miss，自然走远端链路。
 */
@Singleton
class MushroomIndex @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tag = "MushroomIndex"

    private val index: Map<String, LocalMushroom> by lazy { load() }

    /** 当前已加载的条目数（测试用）。 */
    fun size(): Int = index.size

    /**
     * 在大模型返回的候选里查找第一个能命中的本地条目。
     * 找不到时返回 null，调用方应转远端拉取。
     */
    fun lookup(candidates: List<Candidate>): LocalMushroom? =
        MushroomNameMatcher.firstMatch(candidates, index)

    /** 直接按归一化名查（不经过候选 list），用于历史记录 / 收藏夹等。 */
    fun findByScientificName(scientificName: String): LocalMushroom? {
        val key = MushroomNameMatcher.normalize(scientificName)
        return if (key.isEmpty()) null else index[key]
    }

    private fun load(): Map<String, LocalMushroom> {
        return try {
            val text = context.assets.open(ASSET_FILE)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            if (text.isBlank()) {
                Log.w(tag, "$ASSET_FILE is empty")
                return emptyMap()
            }
            val list = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<List<LocalMushroom>>(text)
            list.associateBy { MushroomNameMatcher.normalize(it.scientificName) }
        } catch (t: Throwable) {
            Log.e(tag, "Failed to load $ASSET_FILE", t)
            emptyMap()
        }
    }

    companion object {
        const val ASSET_FILE = "mushroom_index.json"
    }
}
