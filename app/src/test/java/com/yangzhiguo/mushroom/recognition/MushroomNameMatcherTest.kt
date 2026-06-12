package com.yangzhiguo.mushroom.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 单元测试：MushroomNameMatcher（设计文档 §3.3 匹配策略）
 *
 * 1. 精确匹配（case-insensitive + trim）
 * 2. 去 sp. / cf. / aff. 变异
 * 3. 前 3 候选任一命中
 * 4. 全部 miss → null
 */
class MushroomNameMatcherTest {

    private val index: Map<String, LocalMushroom> = listOf(
        LocalMushroom(
            scientificName = "Amanita muscaria",
            commonName = "毒蝇伞",
            shortDesc = "经典红白点毒蘑菇",
            detailRemotePath = "/speciesDetail/123/Amanita%20muscaria/list",
        ),
        LocalMushroom(
            scientificName = "Boletus edulis",
            commonName = "美味牛肝菌",
            shortDesc = "可食",
            detailRemotePath = "/speciesDetail/456/Boletus%20edulis/list",
        ),
        LocalMushroom(
            scientificName = "Russula virescens",
            shortDesc = "绿菇",
            detailRemotePath = "/speciesDetail/789/Russula%20virescens/list",
        ),
    ).associateBy { MushroomNameMatcher.normalize(it.scientificName) }

    // --- 1. 精确匹配 ---

    @Test
    fun `exact case-insensitive match`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(Candidate(scientificName = "amanita MUSCARIA")),
            index = index,
        )
        assertNotNull(hit)
        assertEquals("Amanita muscaria", hit!!.scientificName)
    }

    @Test
    fun `match with surrounding whitespace`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(Candidate(scientificName = "  Boletus edulis  ")),
            index = index,
        )
        assertNotNull(hit)
        assertEquals("Boletus edulis", hit!!.scientificName)
    }

    // --- 2. 去变异 ---

    @Test
    fun `match after stripping sp suffix`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(Candidate(scientificName = "Amanita sp.")),
            index = index,
        )
        // 命中策略：sp. 直接视为"未确定种属"，退化为属级（这里属名不在索引里）
        // → 应返回 null（不是误命中）
        assertNull(hit)
    }

    @Test
    fun `match after stripping cf prefix`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(Candidate(scientificName = "cf. Russula virescens")),
            index = index,
        )
        assertNotNull(hit)
        assertEquals("Russula virescens", hit!!.scientificName)
    }

    @Test
    fun `match after stripping aff prefix`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(Candidate(scientificName = "Amanita aff. muscaria")),
            index = index,
        )
        assertNotNull(hit)
        assertEquals("Amanita muscaria", hit!!.scientificName)
    }

    // --- 3. 前 3 候选任一命中 ---

    @Test
    fun `first hit in candidate list wins`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(
                Candidate(scientificName = "Unknown Species"),
                Candidate(scientificName = "Boletus edulis"),
            ),
            index = index,
        )
        assertNotNull(hit)
        assertEquals("Boletus edulis", hit!!.scientificName)
    }

    @Test
    fun `hit on second candidate when first misses`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(
                Candidate(scientificName = "Unknown One"),
                Candidate(scientificName = "Russula virescens"),
                Candidate(scientificName = "Amanita muscaria"),
            ),
            index = index,
        )
        assertNotNull(hit)
        assertEquals("Russula virescens", hit!!.scientificName)
    }

    @Test
    fun `only first three candidates are considered`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(
                Candidate(scientificName = "Miss A"),
                Candidate(scientificName = "Miss B"),
                Candidate(scientificName = "Miss C"),
                // 第四个会命中，但前 3 全 miss 应整体 miss
                Candidate(scientificName = "Amanita muscaria"),
            ),
            index = index,
        )
        assertNull(hit)
    }

    // --- 4. 全部 miss ---

    @Test
    fun `no hit returns null`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = listOf(Candidate(scientificName = "Completely Unknown Genus species")),
            index = index,
        )
        assertNull(hit)
    }

    @Test
    fun `empty candidate list returns null`() {
        val hit = MushroomNameMatcher.firstMatch(
            candidates = emptyList(),
            index = index,
        )
        assertNull(hit)
    }

    // --- 内部 normalize 测试 ---

    @Test
    fun `normalize trims and lowercases`() {
        assertEquals("amanita muscaria", MushroomNameMatcher.normalize("  Amanita MUSCARIA  "))
    }

    @Test
    fun `normalize strips cf aff sp prefixes`() {
        assertEquals("boletus edulis", MushroomNameMatcher.normalize("cf. Boletus edulis"))
        assertEquals("boletus edulis", MushroomNameMatcher.normalize("aff. Boletus edulis"))
        assertEquals("amanita", MushroomNameMatcher.normalize("Amanita sp."))
    }

    @Test
    fun `normalize removes parenthetical authors`() {
        // (L.) 在括号内被移除；Lam. 是独立缩写（命名人 Lamarck），保留为 lam.
        assertEquals("amanita muscaria lam.", MushroomNameMatcher.normalize("Amanita muscaria (L.) Lam."))
    }

    @Test
    fun `normalize removes full parenthetical expression`() {
        // 括号包裹整段备注 → 整段移除
        assertEquals("cortinarius", MushroomNameMatcher.normalize("Cortinarius (note: needs check)"))
    }
}
