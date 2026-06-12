package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.data.local.DnaBarcodeEntity
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.local.SpeciesImageEntity
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

class ScraperToRoomMapperTest {

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
    fun toScrawSource_mapsSpecimenSourceToSpeciesSpecimen() {
        assertEquals(
            SpeciesEntity.SCRAW_SOURCE_SPECIES_SPECIMEN,
            ScraperToRoomMapper.toScrawSource(DataSource.SPECIMEN),
        )
    }

    @Test
    fun toScrawSource_mapsGeneralDirectoryToGeneralDirectory() {
        assertEquals(
            SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY,
            ScraperToRoomMapper.toScrawSource(DataSource.GENERAL_DIRECTORY),
        )
    }

    @Test
    fun toEntityFromRecord_storesScrawSourceFromSourceEnum() {
        val specimen = Specimen(id = 11L, speciesLatin = "Amanita muscaria")
        val record = ScrapedRecord(
            specimen = specimen,
            source = DataSource.SPECIMEN,
            sourceUrl = "https://fungi.iflora.cn/#/specimenDetail/11",
        )

        val entity = ScraperToRoomMapper.toEntity(specimen, record, now = 1_700_000_000L)
        assertEquals(SpeciesEntity.SCRAW_SOURCE_SPECIES_SPECIMEN, entity.scrawSource)
        assertEquals(record.sourceUrl, entity.sourceUrl)
    }

    @Test
    fun toEntityFromRecord_startsSourceTypesWithSourceEnumName() {
        val specimen = Specimen(id = 11L, edibleFungus = "是")
        val record = ScrapedRecord(
            specimen = specimen,
            source = DataSource.GENERAL_DIRECTORY,
            sourceUrl = "https://fungi.iflora.cn/#/speciesDetail/11",
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

    // ── 新:extractImages 保留元数据 ──────────────────────────────────────

    @Test
    fun extractImages_preservesUfIdAndFilename() {
        val specimen = specimenWithImages(
            id = 31L,
            sysFileListJson = "[]",
            kibSpeciesPicturesJson = """
                [
                  {"uf_id":"5496357","uf_name":"GLG-FXP855 (2).JPG","uf_src":"http://cloudfile.biotracks.cn/a.jpg!bio","uf_size":"7917005","ident":""}
                ]
            """.trimIndent(),
        )

        val images = ScraperToRoomMapper.extractImages(specimen, speciesId = 31)

        assertEquals(1, images.size)
        assertEquals("5496357", images[0].ufId)
        assertEquals("GLG-FXP855 (2).JPG", images[0].ufName)
        assertEquals(7917005L, images[0].ufSize)
        assertEquals("http://cloudfile.biotracks.cn/a.jpg!bio", images[0].ufSrc)
        assertEquals(SpeciesImageEntity.SOURCE_KIB_PICTURES, images[0].source)
        assertEquals(0, images[0].sortOrder)
    }

    @Test
    fun extractImages_keepsSysFileListBeforeKibPictures_andAssignsSequentialOrder() {
        val specimen = specimenWithImages(
            id = 31L,
            sysFileListJson = """
                [
                  {"id":"2064183905124171777","fileName":"sys1.jpg","url":"/admin/sys-file/local/sys1.jpg"},
                  {"id":"2064183905124171778","fileName":"sys2.jpg","url":"/admin/sys-file/local/sys2.jpg"}
                ]
            """.trimIndent(),
            kibSpeciesPicturesJson = """
                [
                  {"uf_id":"5496357","uf_name":"kib1.jpg","uf_src":"/admin/sys-file/local/kib1.jpg"}
                ]
            """.trimIndent(),
        )
        val images = ScraperToRoomMapper.extractImages(specimen, speciesId = 31)

        assertEquals(3, images.size)
        assertEquals(SpeciesImageEntity.SOURCE_SYS_FILE, images[0].source)
        assertEquals(SpeciesImageEntity.SOURCE_SYS_FILE, images[1].source)
        assertEquals(SpeciesImageEntity.SOURCE_KIB_PICTURES, images[2].source)
        assertEquals(listOf(0, 1, 2), images.map { it.sortOrder })
    }

    // ── 新:extractBarcodes 7 基因位点 ───────────────────────────────────

    @Test
    fun extractBarcodes_emitsAllSevenGenes_withCorrectAccessions() {
        val specimen = Specimen(
            id = 1925L,
            itsGenbank = "MZ123456",
            itsGenbankUrl = "https://www.ncbi.nlm.nih.gov/nuccore/MZ123456",
            nrlsuGenbank = "MZ123457",
            nrlsuGenbankUrl = null,
            tef1Genbank = "数据暂未公开",
            tef1GenbankUrl = null,
            rpb1Genbank = null,
            rpb1GenbankUrl = null,
            rpb2Genbank = null,
            rpb2GenbankUrl = null,
            ssu = "数据暂未公开",
            tub2 = "MZ999999",
        )
        val rows = ScraperToRoomMapper.extractBarcodes(specimen, speciesId = 31, specimenId = 1925L)

        // 7 个基因位点中,只有 5 个有值(ITS/nrLSU/TEF1/SSU/tub2);RPB1/RPB2 全空被过滤
        assertEquals(5, rows.size)
        val byGene = rows.associateBy { it.gene }

        assertEquals("MZ123456", byGene[DnaBarcodeEntity.GENE_ITS]?.accession)
        assertTrue(byGene[DnaBarcodeEntity.GENE_ITS]!!.isPublic)

        assertEquals("MZ123457", byGene[DnaBarcodeEntity.GENE_NRLSU]?.accession)

        assertEquals("数据暂未公开", byGene[DnaBarcodeEntity.GENE_TEF1]?.accession)
        assertFalse(byGene[DnaBarcodeEntity.GENE_TEF1]!!.isPublic)

        assertNull(byGene[DnaBarcodeEntity.GENE_SSU]?.url)
        assertFalse(byGene[DnaBarcodeEntity.GENE_SSU]!!.isPublic)

        assertEquals("MZ999999", byGene[DnaBarcodeEntity.GENE_TUB2]?.accession)
        assertTrue(byGene[DnaBarcodeEntity.GENE_TUB2]!!.isPublic)

        // RPB1/RPB2 因 accession+url 全空被丢弃
        assertNull(byGene[DnaBarcodeEntity.GENE_RPB1])
        assertNull(byGene[DnaBarcodeEntity.GENE_RPB2])
    }

    @Test
    fun extractBarcodes_skipsGenesThatAreMissing() {
        val specimen = Specimen(id = 1L, itsGenbank = "MZ1")
        val rows = ScraperToRoomMapper.extractBarcodes(specimen, speciesId = 1, specimenId = 1L)
        // 只有 ITS 出现,其他 6 个基因因为 accession+url 全空被丢弃
        assertEquals(1, rows.size)
        assertEquals(DnaBarcodeEntity.GENE_ITS, rows[0].gene)
    }

    // ── 新:mapSpecimen 行政编码解码 ─────────────────────────────────────

    @Test
    fun mapSpecimen_resolvesProvinceAndCityFromRegionLookup() {
        val specimen = Specimen(
            id = 5L,
            gatherNum = "GLG-FXP855",
            collectionNum = "HKAS 136387",
            collectProvince = "530000",
            collectCity = "530500",
            collectDistrict = "530523",
            collectVillage = "滥澡堂",
            latitude = "24.74",
            longitude = "98.86",
            altitude = "2100",
        )
        val lookup = ScraperToRoomMapper.RegionLookup { code ->
            when (code) {
                "530000" -> "云南省"
                "530500" -> "保山市"
                "530523" -> "龙陵县"
                else -> null
            }
        }
        val row = ScraperToRoomMapper.mapSpecimen(specimen, speciesId = 31, sourceUrl = "x", regionLookup = lookup)

        assertEquals(31, row.speciesId)
        assertEquals("云南省", row.collectProvinceZh)
        assertEquals("保山市", row.collectCityZh)
        assertEquals("龙陵县", row.collectDistrictZh)
        assertEquals("24.74", row.latitude)
        assertEquals("98.86", row.longitude)
    }

    @Test
    fun mapSpecimen_fallsBackToNullWhenLookupCannotDecode() {
        val specimen = Specimen(id = 9L, collectProvince = "999999")
        val lookup = ScraperToRoomMapper.RegionLookup { null }
        val row = ScraperToRoomMapper.mapSpecimen(specimen, speciesId = 1, sourceUrl = null, regionLookup = lookup)
        assertEquals("999999", row.collectProvince)
        assertNull(row.collectProvinceZh)
    }

    // ── 新:toBatch 5 表组装 ────────────────────────────────────────────

    @Test
    fun toBatch_emitsAllFiveTables() {
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
            itsGenbank = "MZ001",
        )
        val record = ScrapedRecord(
            specimen = specimen,
            source = DataSource.GENERAL_DIRECTORY,
            sourceUrl = "https://fungi.iflora.cn/#/speciesDetail/31/Lactarius%20gracilis",
        )
        val extraSpec = Specimen(id = 1925L, speciesLatin = "Lactarius gracilis", itsGenbank = "MZ002")
        val points = listOf(
            ScraperToRoomMapper.DistributionPoint(lng = 98.15, lat = 25.22, count = 1),
            ScraperToRoomMapper.DistributionPoint(lng = 98.35, lat = 25.32, count = 1),
        )

        val batch = ScraperToRoomMapper.toBatch(
            record = record,
            extraSpecimens = listOf(extraSpec),
            extraDistributionPoints = points,
            now = 1_700_000_000L,
        )

        assertEquals(1, batch.species.size)
        assertEquals("Lactarius gracilis", batch.species[0].scientificName)
        assertEquals(UseType.EDIBLE, batch.species[0].useType)

        assertEquals(2, batch.specimens.size)
        assertEquals(setOf(31L, 1925L), batch.specimens.map { it.id }.toSet())
        assertTrue(batch.specimens.all { it.speciesId == 31 })

        assertEquals(2, batch.images.size)
        assertEquals(setOf("5496357", "5496358"), batch.images.map { it.ufId }.toSet())

        // DNA: 主 specimen (31) 有 1 个基因 (ITS), 额外 (1925) 有 1 个基因 (ITS)
        assertEquals(2, batch.barcodes.size)
        assertTrue(batch.barcodes.all { it.speciesId == 31 })
        assertEquals(setOf(31L, 1925L), batch.barcodes.map { it.specimenId }.toSet())

        assertEquals(2, batch.distributionPoints.size)
        assertTrue(batch.distributionPoints.all { it.speciesId == 31 })
    }

    @Test
    fun toBatch_skipsExtraSpecimensWithSameId() {
        val specimen = Specimen(id = 31L, speciesLatin = "X")
        val record = ScrapedRecord(
            specimen = specimen,
            source = DataSource.GENERAL_DIRECTORY,
            sourceUrl = "x",
        )
        val batch = ScraperToRoomMapper.toBatch(
            record = record,
            extraSpecimens = listOf(specimen, specimen),
            now = 1L,
        )
        // 主 specimen 自身 + 0 个额外 (被去重)
        assertEquals(1, batch.specimens.size)
        // DNA 同理,主 specimen accession 全部 null
        assertEquals(0, batch.barcodes.size)
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
