package com.yangzhiguo.mushroom.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DetailLruCacheTest {

    private fun makeEntry(name: String, fetchedAt: Long): CachedDetail {
        val m = LocalMushroom(
            scientificName = name,
            shortDesc = "desc-$name",
            detailRemotePath = "/x/$name",
        )
        return CachedDetail(mushroom = m, fetchedAt = fetchedAt)
    }

    @Test
    fun `put then get returns same entry`() {
        val cache = DetailLruCache(maxEntries = 3, ttlMs = 60_000, clock = { 1000L })
        cache.put("a", makeEntry("a", fetchedAt = 1000L))
        val got = cache.get("a")
        assertNotNull(got)
        assertEquals("a", got!!.mushroom.scientificName)
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = DetailLruCache(3, 60_000, { 0L })
        assertNull(cache.get("missing"))
    }

    @Test
    fun `get returns null when ttl exceeded`() {
        var now = 1000L
        val cache = DetailLruCache(maxEntries = 3, ttlMs = 60_000, clock = { now })
        cache.put("a", makeEntry("a", fetchedAt = 1000L))
        // 不到 60s：命中
        now = 60_999L
        assertNotNull(cache.get("a"))
        // 60s 之后：过期
        now = 61_001L
        assertNull(cache.get("a"))
    }

    @Test
    fun `lru evicts least recently used when over capacity`() {
        var now = 0L
        val cache = DetailLruCache(maxEntries = 3, ttlMs = 60_000, clock = { now })

        cache.put("a", makeEntry("a", 0L))
        now = 1
        cache.put("b", makeEntry("b", 1L))
        now = 2
        cache.put("c", makeEntry("c", 2L))
        // 触发 LRU 重排：访问 a 与 b，c 成为最久未用
        now = 3
        cache.get("a")
        now = 4
        cache.get("b")
        now = 5
        // 插入第 4 条 → 应淘汰 c
        cache.put("d", makeEntry("d", 5L))
        assertNull("c 应该是 LRU 受害者", cache.get("c"))
        assertNotNull(cache.get("a"))
        assertNotNull(cache.get("b"))
        assertNotNull(cache.get("d"))
    }

    @Test
    fun `size reflects current entry count`() {
        val cache = DetailLruCache(maxEntries = 5, ttlMs = 60_000, { 0L })
        assertEquals(0, cache.size())
        cache.put("a", makeEntry("a", 0L))
        assertEquals(1, cache.size())
        cache.put("b", makeEntry("b", 0L))
        assertEquals(2, cache.size())
    }

    @Test
    fun `evictExpired removes only expired entries`() {
        var now = 0L
        val cache = DetailLruCache(maxEntries = 10, ttlMs = 100, clock = { now })
        cache.put("fresh", makeEntry("fresh", fetchedAt = 0L))     // age 0
        cache.put("stale1", makeEntry("stale1", fetchedAt = -200L)) // age 200 (expired)
        cache.put("stale2", makeEntry("stale2", fetchedAt = -150L)) // age 150 (expired)
        now = 50L
        val removed = cache.evictExpired()
        assertEquals(2, removed)
        assertEquals(1, cache.size())
        assertNotNull(cache.get("fresh"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = DetailLruCache(3, 60_000, { 0L })
        cache.put("a", makeEntry("a", 0L))
        cache.put("b", makeEntry("b", 0L))
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
    }
}
