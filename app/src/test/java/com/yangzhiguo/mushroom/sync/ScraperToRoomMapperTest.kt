package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.ScrapedRecord
import com.yangzhiguo.mushroom.scraper.Specimen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v10 简化:删除了所有 specimen / DNA / distribution 相关断言。
 * toEntity / toBatch / extractAllImageUrls / deriveXxx 系列保留。
 */
class ScraperToRoomMapperTest {
    @Test
    fun specimenUsesNegativeMushroomIdNamespace() {
        val specimen = Specimen(id = 42, speciesLatin = "Amanita sp.")
        val record = ScrapedRecord(
            specimen = specimen,
            source = DataSource.SPECIMEN,
            sourceUrl = DataSource.SPECIMEN.detailUrl(specimen),
        )

        assertEquals(-42, ScraperToRoomMapper.toBatch(record).species.single().mushroomId)
    }


    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parseStringArray(jsonText: String): List<String> {
        val array = json.parseToJsonElement(jsonText) as JsonArray
        return array.map { (it as JsonPrimitive).content }
    }

    // ── 派生枚举(原覆盖) ───────────────────────────────────────────────

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

    // ── 图片 URL 提取(原覆盖) ──────────────────────────────────────────

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
                "https://cloudfile.biotracks.cn/user_thumb/a.jpg!bio",
                "https://cloudfile.biotracks.cn/user_thumb/b.jpg!bio",
                "https://cloudfile.biotracks.cn/user_thumb/c.jpg!bio",
                "https://cloudfile.biotracks.cn/user_thumb/d.jpg!bio",
            ),
            urls,
        )
    }

    @Test
    fun extractAllImageUrls_supportsThreeLegacyPicturesFromSpecimen4297() {
        val specimen = specimenWithImages(
            id = 4297L,
            sysFileListJson = "[]",
            kibSpeciesPicturesJson = """
                [
                  {"uf_src":"http://cloudfile.biotracks.cn/user_thumb/50258/2025_3_13/a.jpg!bio123456"},
                  {"uf_src":"http://cloudfile.biotracks.cn/user_thumb/50258/2025_3_13/b.jpg!bio123456"},
                  {"uf_src":"http://cloudfile.biotracks.cn/user_thumb/50258/2025_3_13/c.jpg!bio123456"}
                ]
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://cloudfile.biotracks.cn/user_thumb/50258/2025_3_13/a.jpg!bio123456",
                "https://cloudfile.biotracks.cn/user_thumb/50258/2025_3_13/b.jpg!bio123456",
                "https://cloudfile.biotracks.cn/user_thumb/50258/2025_3_13/c.jpg!bio123456",
            ),
            ScraperToRoomMapper.extractAllImageUrls(specimen),
        )
    }

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
    fun toEntity_writesEmptyJsonArrayWhenSpecimenHasNoImages() {
        val specimen = Specimen(id = 99L, speciesLatin = "Lepiota nuda")
        val entity = ScraperToRoomMapper.toEntity(specimen, now = 1L)
        assertEquals("[]", entity.images)
        assertNull(entity.imageUrl)
    }

    // ── scraw_source 映射 ──────────────────────────────────────────────

    @Test
    fun toScrawSource_mapsGeneralDirectoryToGeneralDirectory() {
        assertEquals(
            SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY,
            ScraperToRoomMapper.toScrawSource(DataSource.GENERAL_DIRECTORY),
        )
    }

    @Test
    fun toEntityFromRecord_startsSourceTypesWithSourceEnumName() {
        val specimen = Specimen(id = 11L, edibleFungus = "是")
        val record = ScrapedRecord(
            specimen = specimen,
            source = DataSource.GENERAL_DIRECTORY,
            sourceUrl = "https://fungi.iflora.cn/#/speciesDetail/11/Lactarius%20gracilis",
        )
        val entity = ScraperToRoomMapper.toEntity(specimen, record, now = 1L)
        assertTrue(entity.sourceTypes.startsWith("GENERAL_DIRECTORY"))
        assertTrue(entity.sourceTypes.contains("EDIBLE"))
    }

    // ── 新:长描述不再 truncate ──────────────────────────────────────────

    @Test
    fun toEntity_keepsLongDescriptionsInFull_no200CharTruncation() {
        val longText = "X".repeat(2000)
        val specimen = Specimen(
            id = 1L,
            speciesLatin = "Test sp.",
            speciesChinese = "测试种",
            speciesDescription = longText,
            cap = longText,
            lamella = longText,
            stipe = longText,
            capContext = longText,
            stipeContext = longText,
            odor = longText,
            calmSeed = longText,
        )

        val entity = ScraperToRoomMapper.toEntity(specimen, now = 1L)

        assertEquals(2000, entity.speciesDescription?.length)
        assertEquals(2000, entity.capDescription?.length)
        assertEquals(2000, entity.lamellaDescription?.length)
        assertEquals(2000, entity.stipeDescription?.length)
        assertEquals(2000, entity.sporeDescription?.length)
    }

    @Test
    fun toEntity_keepsAllTaxonomyLevels() {
        val specimen = Specimen(
            id = 2L,
            speciesLatin = "Lactarius gracilis",
            speciesChinese = "纤细乳菇",
            communityEnglish = "Fungi",
            communityChinese = "真菌界",
            phylumEnglish = "Basidiomycota",
            phylumChinese = "担子菌门",
            classEnglish = "Agaricomycetes",
            classChinese = "蘑菇纲",
            orderEnglish = "Russulales",
            orderChinese = "红菇目",
            suborderEnglish = "SubOrderEN",
            suborderChinese = "亚目中文",
            familyEnglish = "Russulaceae",
            familyChinese = "红菇科",
            subfamilyEnglish = "SubFamilyEN",
            subfamilyChinese = "亚科中文",
            genusEnglish = "Lactarius",
            genusChinese = "乳菇属",
            subgenusEnglish = "SubGenusEN",
            subgenusChinese = "亚属中文",
            sectionEnglish = "SectionEN",
            sectionChinese = "组中文",
        )
        val entity = ScraperToRoomMapper.toEntity(specimen, now = 1L)
        assertEquals("担子菌门", entity.phylumZh)
        assertEquals("Basidiomycota", entity.phylumLa)
        assertEquals("亚目中文", entity.suborderZh)
        assertEquals("亚科中文", entity.subfamilyZh)
        assertEquals("亚属中文", entity.subgenusZh)
        assertEquals("组中文", entity.sectionZh)
    }

    // ── 新:v10 mushroom_id 来自 API id ────────────────────────────────

    @Test
    fun toEntity_setsMushroomIdFromSpecimenId() {
        val specimen = Specimen(id = 33L, speciesLatin = "Amanita muscaria")
        val entity = ScraperToRoomMapper.toEntity(specimen, now = 1L)
        assertEquals(33, entity.mushroomId)
    }

    // ── 新:toBatch 只产 species,图片写入主表 ───────────────────────────

    @Test
    fun toBatch_emitsSpeciesAndImagesOnly() {
        val specimen = specimenWithImages(
            id = 31L,
            sysFileListJson = "[]",
            kibSpeciesPicturesJson = """
                [
                  {"uf_id":"5496357","uf_name":"k1.jpg","uf_src":"http://x/k1.jpg"},
                  {"uf_id":"5496358","uf_name":"k2.jpg","uf_src":"http://x/k2.jpg"}
                ]
            """.trimIndent(),
        ).copy(
            speciesLatin = "Lactarius gracilis",
            speciesChinese = "纤细乳菇",
            edibleFungus = "是",
        )
        val record = ScrapedRecord(
            specimen = specimen,
            source = DataSource.GENERAL_DIRECTORY,
            sourceUrl = "https://fungi.iflora.cn/#/speciesDetail/31/Lactarius%20gracilis",
        )

        val batch = ScraperToRoomMapper.toBatch(record = record, now = 1_700_000_000L)

        assertEquals(1, batch.species.size)
        assertEquals("Lactarius gracilis", batch.species[0].scientificName)
        assertEquals(UseType.EDIBLE, batch.species[0].useType)
        assertEquals(31, batch.species[0].mushroomId)

        assertEquals(
            listOf("http://x/k1.jpg", "http://x/k2.jpg"),
            ScraperToRoomMapper.extractAllImageUrls(specimen),
        )
        assertTrue(batch.species.single().images.contains("http://x/k1.jpg"))
    }

    // ── 测试工具 ───────────────────────────────────────────────────────

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
