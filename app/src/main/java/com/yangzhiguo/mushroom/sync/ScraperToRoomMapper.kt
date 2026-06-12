package com.yangzhiguo.mushroom.sync

import android.util.Log
import com.yangzhiguo.mushroom.data.local.DnaBarcodeEntity
import com.yangzhiguo.mushroom.data.local.DistributionPointEntity
import com.yangzhiguo.mushroom.data.local.SpecimenEntity
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.local.SpeciesImageEntity
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.ApiClient
import com.yangzhiguo.mushroom.scraper.ScrapedRecord
import com.yangzhiguo.mushroom.scraper.Specimen
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Mapper: iflora.cn 全字段 Specimen (117 列) → Room 5 张表 (1 主 + 4 子).
 *
 * 一次性把一份 [ScrapedRecord] 拆成 [SpeciesEntity] + N 条图片 + N 条 DNA + 1 条
 * specimen + N 个分布点,装进 [SpeciesSyncBatch]。**不再 truncate 任何描述文本**;
 * 整条字符串原样落库。
 *
 * 调用方: [com.yangzhiguo.mushroom.sync.SyncRepository] 在 `withTransaction`
 * 内 `speciesDao.upsertAll(batch.species)` → `specimenDao.upsertAll(batch.specimens)` →
 * `speciesImageDao.upsertAll(batch.images)` → `dnaBarcodeDao.upsertAll(batch.barcodes)` →
 * `distributionPointDao.upsertAll(batch.distributionPoints)`,5 张表一致。
 */
object ScraperToRoomMapper {
    private const val TAG = "ScraperToRoomMapper"
    private const val IFLORA_HOST = "https://fungi.iflora.cn"

    private val stringListSerializer = ListSerializer(String.serializer())
    private val json = Json { encodeDefaults = true }

    /**
     * 行政编码解析钩子。在 dev 阶段由 [com.yangzhiguo.mushroom.util.RegionCodeDecoder]
     * 提供;测试时可注入 `(String?) -> String?` 桩函数。
     */
    fun interface RegionLookup {
        fun decode(code: String?): String?
    }

    /** 默认桩:不解析,中文列全部为 null(用于纯单元测试) */
    val NOOP_REGION_LOOKUP: RegionLookup = RegionLookup { null }

    // ── 公开入口 ─────────────────────────────────────────────────────────

    /**
     * 主表行 — 与 [toBatch] 共享派生逻辑(可单独被种子数据/旧 mapper 测试复用)。
     * **不**包含图片/子表信息,只填 species 自己的列。
     */
    fun toEntity(
        s: Specimen,
        record: ScrapedRecord? = null,
        regionLookup: RegionLookup = NOOP_REGION_LOOKUP,
        now: Long = System.currentTimeMillis(),
    ): SpeciesEntity {
        val useType = deriveUseType(s)
        val toxicity = deriveToxicityLevel(s)
        val edibility = deriveEdibility(s, toxicity)
        val imageUrls = extractAllImageUrls(s)

        return SpeciesEntity(
            // 主键
            id = s.id.toInt(),
            scrawSource = record?.let { toScrawSource(it.source) } ?: SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY,
            lastUpdated = now,

            // 命名
            scientificName = s.speciesLatin.orEmpty(),
            chineseName = s.speciesChinese.orEmpty(),
            authority = s.famousPerson.orEmpty(),
            speciesLatinGenus = s.speciesLatinGenus,
            specificEpithet = s.specificEpithet,
            speciesCommon = s.speciesCommon,
            fieldIdentification = s.fieldIdentification,

            // 分类
            communityZh = s.communityChinese,
            communityLa = s.communityEnglish,
            phylumZh = s.phylumChinese,
            phylumLa = s.phylumEnglish,
            classZh = s.classChinese,
            classLa = s.classEnglish,
            orderZh = s.orderChinese,
            orderLa = s.orderEnglish,
            suborderZh = s.suborderChinese,
            suborderLa = s.suborderEnglish,
            familyZh = s.familyChinese.orEmpty(),
            familyLa = s.familyEnglish.orEmpty(),
            subfamilyZh = s.subfamilyChinese,
            subfamilyLa = s.subfamilyEnglish,
            genusZh = s.genusChinese.orEmpty(),
            genusLa = s.genusEnglish.orEmpty(),
            subgenusZh = s.subgenusChinese,
            subgenusLa = s.subgenusEnglish,
            sectionZh = s.sectionChinese,
            sectionLa = s.sectionEnglish,

            // 用途/毒性
            useType = useType,
            toxicityLevel = toxicity,
            edibility = edibility,
            edibleFungus = s.edibleFungus,
            medicinalFungus = s.medicinalFungus,
            toxicFungus = s.toxicFungus,
            conditionallyFungus = s.conditionallyFungus,
            mycorrhizalFungus = s.mycorrhizalFungus,
            saprophyticFungus = s.saprophyticFungus,
            parasiticFungus = s.parasiticFungus,
            economicUse = s.economicUse,

            // 生态
            habitat = s.speciesHabitat.orEmpty(),
            substrate = s.substrate,
            treeSpecies = s.treeSpecies,
            climateZone = s.climateZone,
            tropicalSpecies = s.tropicalSpecies,
            subtropicalSpecies = s.subtropicalSpecies,
            temperateSpecies = s.temperateSpecies,
            southwestSpecific = s.southwestSpecific,
            yunnanSpecific = s.yunnanSpecific,
            isSouthwest = s.isSouthwest,
            isXizang = s.isXizang,
            isSichuan = s.isSichuan,
            isGuizhou = s.isGuizhou,
            isGaoligong = s.isGaoligong,
            isYunnan = s.isYunnan,
            distributionLocation = s.distributionLocation,
            altitudeRange = s.altitude,

            // 描述(完整,不截断)
            speciesDescription = s.speciesDescription,
            capDescription = s.cap,
            capContext = s.capContext,
            lamellaDescription = s.lamella,
            stipeDescription = s.stipe,
            stipeContext = s.stipeContext,
            odor = s.odor,
            sporeDescription = s.calmSeed,
            ringDescription = "",
            volvaDescription = "",
            descriptionReference = s.descriptionReference,
            purposeReferences = s.purposeReferences,
            habitReferences = s.habitReferences,
            directoryReferences = s.directoryReferences,
            directoryGrade = s.directoryGrade,

            // DNA 主表冗余
            itsGenbank = s.itsGenbank,
            itsGenbankUrl = s.itsGenbankUrl,
            nrlsuGenbank = s.nrlsuGenbank,
            nrlsuGenbankUrl = s.nrlsuGenbankUrl,
            tef1Genbank = s.tef1Genbank,
            tef1GenbankUrl = s.tef1GenbankUrl,
            rpb1Genbank = s.rpb1Genbank,
            rpb1GenbankUrl = s.rpb1GenbankUrl,
            rpb2Genbank = s.rpb2Genbank,
            rpb2GenbankUrl = s.rpb2GenbankUrl,
            ssu = s.ssu,
            tub2 = s.tub2,

            // 采集元信息
            collectUser = s.collectUser,
            collectUnit = s.collectUnit,
            researchTeam = s.researchTeam,
            gatherNum = s.gatherNum,
            collectionNum = s.collectionNum,
            collectTime = s.collectTime,
            specimenHabitat = s.specimenHabitat,
            fieldNote = s.fieldNote,
            specimenDescribe = s.specimenDescribe,
            resourceType = s.resourceType,
            specimenGroup = s.specimenGroup,
            photoNum = s.photoNum,
            createTime = s.createTime,
            speciesChecker = s.speciesChecker,
            fillUser = s.fillUser,
            isApprove = s.isApprove,
            isOpen = s.isOpen,
            borrowStatus = s.borrowStatus,
            assigningUser = s.assigningUser,
            assigningId = s.assigningId,
            strainNumber = s.strainNumber,
            notes = s.notes,
            otherData = s.otherData,

            // 图片快取(由蘑菇子表聚合)
            imageUrl = imageUrls.firstOrNull(),
            imageLocalPath = null,
            images = imageUrls.toJsonArrayString(),

            // 来源 + 旧字段
            sourceUrl = record?.sourceUrl.orEmpty(),
            sourceTypes = buildSourceTypes(s, record),
            isFavorite = false,
            model3dUrl = null,
            identificationPoints = "[]",
            lookAlikeIds = "",
            toxicitySymptoms = "",
            season = "",
        )
    }

    /**
     * 把一份 [ScrapedRecord] 拆成 5 张表的写入单元。
     * 单 species 自身作为 1 条 specimen 入子表;若 `getSpeciesDnaAndLib` 拉到了
     * 额外的 specimen,使用 [extraSpecimens] 二次追加。
     */
    fun toBatch(
        record: ScrapedRecord,
        extraSpecimens: List<Specimen> = emptyList(),
        extraDistributionPoints: List<DistributionPoint> = emptyList(),
        regionLookup: RegionLookup = NOOP_REGION_LOOKUP,
        now: Long = System.currentTimeMillis(),
    ): SpeciesSyncBatch {
        val species = toEntity(record.specimen, record, regionLookup, now)
        val images = extractImages(record.specimen, species.id)
        val barcodes = mutableListOf<DnaBarcodeEntity>().apply {
            addAll(extractBarcodes(record.specimen, species.id, record.specimen.id))
        }

        val specimens = mutableListOf<SpecimenEntity>()
        specimens += mapSpecimen(record.specimen, species.id, record.sourceUrl, regionLookup)
        for (extra in extraSpecimens) {
            if (extra.id == record.specimen.id) continue
            specimens += mapSpecimen(extra, species.id, record.sourceUrl, regionLookup)
            barcodes += extractBarcodes(extra, species.id, extra.id)
        }

        val points = extraDistributionPoints.map { dp ->
            DistributionPointEntity(
                speciesId = species.id,
                lng = dp.lng,
                lat = dp.lat,
                value = dp.value,
                province = dp.province,
                count = dp.count,
            )
        }

        return SpeciesSyncBatch(
            species = listOf(species),
            specimens = specimens,
            images = images,
            barcodes = barcodes,
            distributionPoints = points,
        )
    }

    /** 由 [ScrapedRecord.source] 映射成 scraw_source 字符串 */
    fun toScrawSource(source: DataSource): String = when (source) {
        DataSource.SPECIMEN -> SpeciesEntity.SCRAW_SOURCE_SPECIES_SPECIMEN
        DataSource.GENERAL_DIRECTORY -> SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY
        DataSource.EDIBLE_FUNGI -> SCRAW_SOURCE_EDIBLE_FUNGI
        DataSource.TOXIC_FUNGI -> SCRAW_SOURCE_TOXIC_FUNGI
    }

    // ── 派生枚举(测试覆盖) ──────────────────────────────────────────────

    fun deriveUseType(s: Specimen): UseType = when {
        hasCautionRecord(s) -> UseType.CAUTION
        hasRecord(s.toxicFungus) -> UseType.POISONOUS
        hasRecord(s.edibleFungus) -> UseType.EDIBLE
        hasRecord(s.medicinalFungus) -> UseType.MEDICINAL
        else -> UseType.UNREPORTED
    }

    fun deriveRecordTags(s: Specimen): List<String> = buildList {
        if (hasRecord(s.edibleFungus)) add(UseType.EDIBLE.name)
        if (hasRecord(s.medicinalFungus)) add(UseType.MEDICINAL.name)
        if (hasRecord(s.toxicFungus)) add(UseType.POISONOUS.name)
        if (hasCautionRecord(s)) add(UseType.CAUTION.name)
    }

    fun hasCautionRecord(s: Specimen): Boolean =
        hasRecord(s.conditionallyFungus) ||
            (hasRecord(s.edibleFungus) && hasRecord(s.toxicFungus))

    fun deriveToxicityLevel(s: Specimen): ToxicityLevel = when {
        hasRecord(s.toxicFungus) -> ToxicityLevel.TOXIC
        else -> ToxicityLevel.NONE
    }

    fun deriveEdibility(s: Specimen, toxicity: ToxicityLevel): Edibility = when {
        hasRecord(s.edibleFungus) -> Edibility.EDIBLE
        toxicity == ToxicityLevel.TOXIC -> Edibility.INEDIBLE
        else -> Edibility.UNKNOWN
    }

    fun hasRecord(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return normalized.isNotEmpty() &&
            normalized !in setOf("0", "否", "无", "none", "false", "null", "未知")
    }

    // ── 图片解析(测试覆盖) ─────────────────────────────────────────────

    /**
     * 提取所有可用的图片 URL,按顺序去重。
     *
     * - 新数据:`sysFileList[*].url` 与 `kibSpeciesPictures[*].uf_src` 同时存在,值为
     *   相对路径如 `/admin/sys-file/local/xxx.jpg`,需补 `https://fungi.iflora.cn` 前缀。
     * - 老数据:`sysFileList = []`,仅 `kibSpeciesPictures[*].uf_src` 有值,且通常
     *   是完整 URL `http://cloudfile.biotracks.cn/...jpg!bio123456`。
     */
    fun extractAllImageUrls(s: Specimen): List<String> {
        val out = LinkedHashSet<String>()
        collectUrls(out, s.sysFileList, primaryKey = "url", fallbackKey = "uf_src", fieldName = "sysFileList", specimenId = s.id)
        collectUrls(out, s.kibSpeciesPictures, primaryKey = "uf_src", fallbackKey = "url", fieldName = "kibSpeciesPictures", specimenId = s.id)
        return out.toList()
    }

    fun extractPrimaryImageUrl(s: Specimen): String? = extractAllImageUrls(s).firstOrNull()

    /**
     * 把 [extractAllImageUrls] 的 URL 列表展开成 [SpeciesImageEntity] 行,带
     * `uf_id` / `uf_name` / `uf_size` / `source` 元数据。
     */
    fun extractImages(s: Specimen, speciesId: Int): List<SpeciesImageEntity> {
        val out = mutableListOf<SpeciesImageEntity>()
        var order = 0
        order = collectImages(out, s.sysFileList, primaryKey = "url", fallbackKey = "uf_src", fieldName = "sysFileList", source = SpeciesImageEntity.SOURCE_SYS_FILE, speciesId = speciesId, startOrder = order)
        order = collectImages(out, s.kibSpeciesPictures, primaryKey = "uf_src", fallbackKey = "url", fieldName = "kibSpeciesPictures", source = SpeciesImageEntity.SOURCE_KIB_PICTURES, speciesId = speciesId, startOrder = order)
        return out
    }

    // ── DNA 解析 ────────────────────────────────────────────────────────

    /**
     * 从 [Specimen] 抽出 7 个基因位点 → [DnaBarcodeEntity] 列表。
     * 文本 == "数据暂未公开" 时 `isPublic = false`;其他非空值标记公开。
     */
    fun extractBarcodes(s: Specimen, speciesId: Int, specimenId: Long): List<DnaBarcodeEntity> {
        fun row(gene: String, accession: String?, url: String?): DnaBarcodeEntity? {
            val acc = accession?.trim().orEmpty()
            if (acc.isEmpty() && url.isNullOrBlank()) return null
            val isPublic = !(acc.isEmpty() || PUBLIC_BARCODE_DENY_VALUES.contains(acc.lowercase()))
            return DnaBarcodeEntity(
                specimenId = specimenId,
                speciesId = speciesId,
                gene = gene,
                accession = acc.ifEmpty { null },
                url = url,
                filename = null,
                isPublic = isPublic,
            )
        }
        return listOfNotNull(
            row(DnaBarcodeEntity.GENE_ITS, s.itsGenbank, s.itsGenbankUrl),
            row(DnaBarcodeEntity.GENE_NRLSU, s.nrlsuGenbank, s.nrlsuGenbankUrl),
            row(DnaBarcodeEntity.GENE_TEF1, s.tef1Genbank, s.tef1GenbankUrl),
            row(DnaBarcodeEntity.GENE_RPB1, s.rpb1Genbank, s.rpb1GenbankUrl),
            row(DnaBarcodeEntity.GENE_RPB2, s.rpb2Genbank, s.rpb2GenbankUrl),
            row(DnaBarcodeEntity.GENE_SSU, s.ssu, null),
            row(DnaBarcodeEntity.GENE_TUB2, s.tub2, null),
        )
    }

    // ── Specimen 解析 ────────────────────────────────────────────────────

    fun mapSpecimen(
        s: Specimen,
        speciesId: Int,
        sourceUrl: String? = null,
        regionLookup: RegionLookup = NOOP_REGION_LOOKUP,
    ): SpecimenEntity {
        val province = s.collectProvince
        val city = s.collectCity
        val district = s.collectDistrict
        return SpecimenEntity(
            id = s.id,
            speciesId = speciesId,
            gatherNum = s.gatherNum,
            collectionNum = s.collectionNum,
            strainNumber = s.strainNumber,
            collectUser = s.collectUser,
            collectUnit = s.collectUnit,
            researchTeam = s.researchTeam,
            collectTime = s.collectTime,
            collectCountry = s.collectCountry,
            collectProvince = province,
            collectProvinceZh = regionLookup.decode(province),
            collectCity = city,
            collectCityZh = regionLookup.decode(city),
            collectDistrict = district,
            collectDistrictZh = regionLookup.decode(district),
            collectVillage = s.collectVillage,
            latitude = s.latitude,
            longitude = s.longitude,
            altitude = s.altitude,
            specimenHabitat = s.specimenHabitat,
            fieldNote = s.fieldNote,
            resourceType = s.resourceType,
            createTime = s.createTime,
            notes = s.notes,
            sourceUrl = sourceUrl,
        )
    }

    // ── 内部工具 ─────────────────────────────────────────────────────────

    private fun buildSourceTypes(s: Specimen, record: ScrapedRecord?): String {
        val tags = deriveRecordTags(s)
        return buildList {
            record?.let { add(it.source.name) }
            addAll(tags)
        }.distinct().joinToString(",")
    }

    private fun collectUrls(
        out: MutableSet<String>,
        element: JsonElement?,
        primaryKey: String,
        fallbackKey: String,
        fieldName: String,
        specimenId: Long,
    ) {
        val array = element as? JsonArray ?: return
        for ((index, item) in array.withIndex()) {
            val obj = item as? JsonObject
            if (obj == null) {
                Log.w(TAG, "specimen $specimenId $fieldName[$index] is not an object (got ${item::class.simpleName}); skipped")
                continue
            }
            val raw = primitiveString(obj, primaryKey) ?: primitiveString(obj, fallbackKey)
            if (raw.isNullOrBlank()) continue
            out += normalizeUrl(raw)
        }
    }

    /**
     * 收集图片条目,返回新的 `sortOrder`(写完几张后回传给调用方递增)。
     */
    private fun collectImages(
        out: MutableList<SpeciesImageEntity>,
        element: JsonElement?,
        primaryKey: String,
        fallbackKey: String,
        fieldName: String,
        source: String,
        speciesId: Int,
        startOrder: Int,
    ): Int {
        val array = element as? JsonArray ?: return startOrder
        var order = startOrder
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val ufId = (obj["uf_id"] as? JsonPrimitive)?.content
                ?: (obj["id"] as? JsonPrimitive)?.content
                ?: "$source-${speciesId}-${order}"
            val ufSrcRaw = primitiveString(obj, primaryKey) ?: primitiveString(obj, fallbackKey) ?: continue
            val ufName = (obj["uf_name"] as? JsonPrimitive)?.content
                ?: (obj["fileName"] as? JsonPrimitive)?.content
            val ufSize = (obj["uf_size"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: (obj["fileSize"] as? JsonPrimitive)?.content?.toLongOrNull()
            val ident = (obj["ident"] as? JsonPrimitive)?.content
            out += SpeciesImageEntity(
                speciesId = speciesId,
                ufId = ufId,
                ufName = ufName,
                ufSrc = normalizeUrl(ufSrcRaw),
                ufSize = ufSize,
                ident = ident,
                source = source,
                sortOrder = order,
                localPath = null,
            )
            order++
        }
        return order
    }

    private fun primitiveString(obj: JsonObject, key: String): String? {
        val prim = obj[key] as? JsonPrimitive ?: return null
        if (!prim.isString && prim.toString() == "null") return null
        return prim.content.takeIf { it.isNotBlank() }
    }

    private fun normalizeUrl(raw: String): String {
        return ApiClient.normalizeImageUrl(raw, IFLORA_HOST)
    }

    private fun List<String>.toJsonArrayString(): String =
        json.encodeToString(stringListSerializer, this)

    private val PUBLIC_BARCODE_DENY_VALUES = setOf("数据暂未公开", "未公开", "n/a", "na", "-", "null")

    /** 食用真菌名录 (https://fungi.iflora.cn/#/list_species/economic_fungi_list/edible_fungi_list) */
    const val SCRAW_SOURCE_EDIBLE_FUNGI = "edible_fungi"
    /** 有毒真菌名录 (https://fungi.iflora.cn/#/list_species/economic_fungi_list/toxic_fungi_list) */
    const val SCRAW_SOURCE_TOXIC_FUNGI = "toxic_fungi"

    // ── 数据类 ───────────────────────────────────────────────────────────

    /**
     * 输入:从 `getSpeciesDnaAndLib.detailsLatAndLon[*]` 解析的 1 个点。
     * 调用方在同步流程中把 1:N 个点展开为 [DistributionPointEntity]。
     */
    data class DistributionPoint(
        val lng: Double,
        val lat: Double,
        val value: String? = null,
        val province: String? = null,
        val count: Int = 1,
    )
}

/** 一份抓取记录对应的 5 表写入包 */
data class SpeciesSyncBatch(
    val species: List<SpeciesEntity>,
    val specimens: List<SpecimenEntity>,
    val images: List<SpeciesImageEntity>,
    val barcodes: List<DnaBarcodeEntity>,
    val distributionPoints: List<DistributionPointEntity>,
)
