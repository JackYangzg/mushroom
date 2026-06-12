package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.Specimen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperToRoomMapperTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parseStringArray(jsonText: String): List<String> {
        val array = json.parseToJsonElement(jsonText) as JsonArray
        return array.map { (it as JsonPrimitive).content }
    }

    @Test
    fun medicinalDescriptionCountsAsMedicinalRecord() {
        val specimen = Specimen(
            medicinalFungus = "Antitumor; antioxidant",
            edibleFungus = "0",
            toxicFungus = "0",
        )

        assertEquals(UseType.MEDICINAL, ScraperToRoomMapper.deriveUseType(specimen))
    }

    @Test
    fun edibleAndToxicRecordsRemainIndependentlyClassifiable() {
        val specimen = Specimen(
            edibleFungus = "是",
            toxicFungus = "是",
        )
        val toxicity = ScraperToRoomMapper.deriveToxicityLevel(specimen)

        assertEquals(ToxicityLevel.TOXIC, toxicity)
        assertEquals(Edibility.EDIBLE, ScraperToRoomMapper.deriveEdibility(specimen, toxicity))
        assertEquals(
            listOf(
                UseType.EDIBLE.name,
                UseType.POISONOUS.name,
                UseType.CAUTION.name,
            ),
            ScraperToRoomMapper.deriveRecordTags(specimen),
        )
        assertEquals(UseType.CAUTION, ScraperToRoomMapper.deriveUseType(specimen))
    }

    @Test
    fun edibleMedicinalAndToxicRecordsKeepAllDimensions() {
        val specimen = Specimen(
            edibleFungus = "是",
            medicinalFungus = "Antitumor",
            toxicFungus = "是",
        )

        assertEquals(
            listOf(
                UseType.EDIBLE.name,
                UseType.MEDICINAL.name,
                UseType.POISONOUS.name,
                UseType.CAUTION.name,
            ),
            ScraperToRoomMapper.deriveRecordTags(specimen),
        )
    }

    @Test
    fun explicitConditionalRecordIsCaution() {
        val specimen = Specimen(conditionallyFungus = "是")

        assertTrue(ScraperToRoomMapper.hasCautionRecord(specimen))
        assertEquals(listOf(UseType.CAUTION.name), ScraperToRoomMapper.deriveRecordTags(specimen))
    }

    @Test
    fun zeroAndEmptyValuesDoNotCountAsRecords() {
        assertFalse(ScraperToRoomMapper.hasRecord(null))
        assertFalse(ScraperToRoomMapper.hasRecord(""))
        assertFalse(ScraperToRoomMapper.hasRecord("0"))
        assertFalse(ScraperToRoomMapper.hasRecord("否"))
        assertTrue(ScraperToRoomMapper.hasRecord("是"))
        assertTrue(ScraperToRoomMapper.hasRecord("Antibacteria"))
    }

    // ── 图片提取（回归测试：以前只能提取首张，新实现必须吐出全部） ───────────────

    /**
     * 模拟 specimen 33（page 820 老数据）：sysFileList 为空数组，
     * kibSpeciesPictures 是 4 张完整外链 URL。
     */
    @Test
    fun extractAllImageUrls_returnsAllFourUrlsForLegacySpecimen33() {
        val specimen = specimenWithImages(
            id = 33L,
            sysFileListJson = "[]",
            kibSpeciesPicturesJson = """
                [
                  {"uf_id":"5496361","uf_name":"GLG-FXP859 (2).JPG","uf_src":"http://cloudfile.biotracks.cn/user_thumb/a.jpg!bio"},
                  {"uf_id":"5496362","uf_name":"GLG-FXP859 (3).JPG","uf_src":"http://cloudfile.biotracks.cn/user_thumb/b.jpg!bio"},
                  {"uf_id":"5496363","uf_name":"GLG-FXP859 (4).JPG","uf_src":"http://cloudfile.biotracks.cn/user_thumb/c.jpg!bio"},
                  {"uf_id":"5496364","uf_name":"GLG-FXP859 (5).JPG","uf_src":"http://cloudfile.biotracks.cn/user_thumb/d.jpg!bio"}
                ]
            """.trimIndent(),
        )

        val urls = ScraperToRoomMapper.extractAllImageUrls(specimen)

        assertEquals(
            listOf(
                "http://cloudfile.biotracks.cn/user_thumb/a.jpg!bio",
                "http://cloudfile.biotracks.cn/user_thumb/b.jpg!bio",
                "http://cloudfile.biotracks.cn/user_thumb/c.jpg!bio",
                "http://cloudfile.biotracks.cn/user_thumb/d.jpg!bio",
            ),
            urls,
        )
    }

    /**
     * 模拟新数据（page 1）：sysFileList 与 kibSpeciesPictures 同时有内容，
     * 且通常指向同一张图（相对路径）。去重后应只产出一张。
     */
    @Test
    fun extractAllImageUrls_dedupesWhenSysFileAndKibPicturesPointToSameAsset() {
        val specimen = specimenWithImages(
            id = 9586L,
            sysFileListJson = """[{"url":"/admin/sys-file/local/abc.jpg","name":"abc.jpg"}]""",
            kibSpeciesPicturesJson = """[{"uf_src":"/admin/sys-file/local/abc.jpg","uf_name":"abc.jpg"}]""",
        )

        val urls = ScraperToRoomMapper.extractAllImageUrls(specimen)

        assertEquals(listOf("https://fungi.iflora.cn/admin/sys-file/local/abc.jpg"), urls)
    }

    /**
     * 新数据多图场景：sysFileList[*].url 三张 + kibSpeciesPictures[*].uf_src 一张
     * 唯一新增。sysFileList 排在前；最终四张按顺序输出。
     */
    @Test
    fun extractAllImageUrls_keepsSysFileListBeforeKibPicturesAndMergesNewOnes() {
        val specimen = specimenWithImages(
            id = 9588L,
            sysFileListJson = """
                [
                  {"url":"/admin/sys-file/local/sys1.jpg"},
                  {"url":"/admin/sys-file/local/sys2.jpg"},
                  {"url":"/admin/sys-file/local/sys3.jpg"}
                ]
            """.trimIndent(),
            kibSpeciesPicturesJson = """
                [
                  {"uf_src":"/admin/sys-file/local/sys1.jpg"},
                  {"uf_src":"/admin/sys-file/local/kib-only.jpg"}
                ]
            """.trimIndent(),
        )

        val urls = ScraperToRoomMapper.extractAllImageUrls(specimen)

        assertEquals(
            listOf(
                "https://fungi.iflora.cn/admin/sys-file/local/sys1.jpg",
                "https://fungi.iflora.cn/admin/sys-file/local/sys2.jpg",
                "https://fungi.iflora.cn/admin/sys-file/local/sys3.jpg",
                "https://fungi.iflora.cn/admin/sys-file/local/kib-only.jpg",
            ),
            urls,
        )
    }

    @Test
    fun extractAllImageUrls_returnsEmptyWhenBothListsMissing() {
        val specimen = Specimen(id = 42L)
        assertTrue(ScraperToRoomMapper.extractAllImageUrls(specimen).isEmpty())
        assertNull(ScraperToRoomMapper.extractPrimaryImageUrl(specimen))
    }

    @Test
    fun extractAllImageUrls_returnsEmptyWhenBothListsAreEmptyArrays() {
        val specimen = specimenWithImages(
            id = 7L,
            sysFileListJson = "[]",
            kibSpeciesPicturesJson = "[]",
        )
        assertTrue(ScraperToRoomMapper.extractAllImageUrls(specimen).isEmpty())
    }

    @Test
    fun extractAllImageUrls_skipsItemsWithBlankUrlsButKeepsValidNeighbors() {
        val specimen = specimenWithImages(
            id = 8L,
            sysFileListJson = """
                [
                  {"url":""},
                  {"url":"   "},
                  {"url":"/admin/sys-file/local/keep.jpg"}
                ]
            """.trimIndent(),
            kibSpeciesPicturesJson = "[]",
        )

        assertEquals(
            listOf("https://fungi.iflora.cn/admin/sys-file/local/keep.jpg"),
            ScraperToRoomMapper.extractAllImageUrls(specimen),
        )
    }

    @Test
    fun extractAllImageUrls_fallsBackToUfSrcWhenUrlIsMissingFromSysFileList() {
        val specimen = specimenWithImages(
            id = 9L,
            // sysFileList 的主键是 "url"，但某些条目可能只塞了 "uf_src"
            sysFileListJson = """[{"uf_src":"/admin/sys-file/local/fallback.jpg"}]""",
            kibSpeciesPicturesJson = "[]",
        )

        assertEquals(
            listOf("https://fungi.iflora.cn/admin/sys-file/local/fallback.jpg"),
            ScraperToRoomMapper.extractAllImageUrls(specimen),
        )
    }

    @Test
    fun toEntity_writesAllImagesIntoImagesColumnAndFirstIntoImageUrl() {
        val specimen = specimenWithImages(
            id = 33L,
            sysFileListJson = "[]",
            kibSpeciesPicturesJson = """
                [
                  {"uf_src":"http://cloudfile.biotracks.cn/a.jpg"},
                  {"uf_src":"http://cloudfile.biotracks.cn/b.jpg"},
                  {"uf_src":"http://cloudfile.biotracks.cn/c.jpg"},
                  {"uf_src":"http://cloudfile.biotracks.cn/d.jpg"}
                ]
            """.trimIndent(),
        )

        val entity = ScraperToRoomMapper.toEntity(specimen, now = 1_700_000_000L)

        // imageUrl = 第一张（保留旧行为：列表缩略图直接用）
        assertEquals("http://cloudfile.biotracks.cn/a.jpg", entity.imageUrl)
        // images 列：JSON 数组，4 个 URL，顺序与 extractAllImageUrls 一致
        val parsed = parseStringArray(entity.images)
        assertEquals(4, parsed.size)
        assertEquals("http://cloudfile.biotracks.cn/a.jpg", parsed[0])
        assertEquals("http://cloudfile.biotracks.cn/d.jpg", parsed[3])
    }

    @Test
    fun toEntity_writesEmptyJsonArrayWhenSpecimenHasNoImages() {
        val specimen = Specimen(id = 99L, speciesLatin = "Lepiota nuda")
        val entity = ScraperToRoomMapper.toEntity(specimen, now = 1L)
        assertEquals("[]", entity.images)
        assertNull(entity.imageUrl)
    }

    // ── 测试工具 ─────────────────────────────────────────────────────────

    /**
     * 构造一个带图片字段的 Specimen。`*Json` 参数是真实 JSON 数组字符串片段，
     * 用 kotlinx-serialization 解析成 JsonElement 后塞进 Specimen，模拟 API 返回。
     */
    private fun specimenWithImages(
        id: Long,
        sysFileListJson: String,
        kibSpeciesPicturesJson: String,
    ): Specimen = Specimen(
        id = id,
        sysFileList = json.parseToJsonElement(sysFileListJson),
        kibSpeciesPictures = json.parseToJsonElement(kibSpeciesPicturesJson),
    )
}
