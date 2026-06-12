package com.yangzhiguo.mushroom.recognition

import java.util.LinkedHashMap

/**
 * 远端详情 LRU 缓存（设计文档 §4.3）。
 *
 *  - 容量：50 条
 *  - TTL：7 天（604_800_000 ms）
 *  - 淘汰策略：LRU（最近最少访问的最先淘汰）
 *  - 线程安全：所有写操作加锁；读走同步块外的 LinkedHashMap（带 accessOrder=true）
 *
 * 不引入 androidx.collection.LruCache 是为了避免额外的 androidx 依赖和便于
 * 纯 JVM 单元测试（[com.yangzhiguo.mushroom.recognition.DetailLruCacheTest]）。
 */
class DetailLruCache(
    private val maxEntries: Int = 50,
    private val ttlMs: Long = 7L * 24 * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val map = object : LinkedHashMap<String, CachedDetail>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDetail>): Boolean {
            // 引用外层 DetailLruCache 的 maxEntries 属性
            return size > this@DetailLruCache.maxEntries
        }
    }

    @Synchronized
    fun get(key: String): CachedDetail? {
        val entry = map[key] ?: return null
        if (clock() - entry.fetchedAt > ttlMs) {
            map.remove(key)
            return null
        }
        return entry
    }

    @Synchronized
    fun put(key: String, value: CachedDetail) {
        map[key] = value
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun evictExpired(): Int {
        val now = clock()
        val it = map.entries.iterator()
        var removed = 0
        while (it.hasNext()) {
            if (now - it.next().value.fetchedAt > ttlMs) {
                it.remove()
                removed++
            }
        }
        return removed
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

/**
 * 远端详情缓存条目。`mushroom` 是 fetch 时的快照（避免索引升级后回看陈旧字段），
 * `rawJson` 是 iflora WebView 抓回的原始 JSON（本期 MVP 暂未使用，预留）。
 */
data class CachedDetail(
    val mushroom: LocalMushroom,
    val fetchedAt: Long,
    val rawJson: String? = null,
)
