package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.ApiClient
import com.yangzhiguo.mushroom.scraper.DataSource
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
 * Mapper: iflora.cn 全字段 Specimen → Room `mushroom_species`。
 *
 * v10 简化:
 * - 删 `extractBarcodes` / `mapSpecimen` / `DistributionPoint` / `extraSpecimens`
 *   / `extraDistributionPoints` / `firstFileMetadata` 等 specimen/DNA/distribution 子表相关代码。
 * - 主表 speciesEntity 写入 `(mushroom_id, scraw_source)` 复合主键。
 * - 图片 URL 直接写入当前来源行的 `image_url` / `images`。
 *
 * 调用方: [com.yangzhiguo.mushroom.sync.SyncRepository] 在一整个 source 抓完后调
 * [toBatch],在 `withTransaction` 内 `speciesDao.upsertAll(batch.species)`。
 */
object ScraperToRoomMapper {
    private const val IFLORA_HOST = "https://fungi.iflora.cn"
    private const val TAG = "ScraperToRoomMapper"

    private val stringListSerializer = ListSerializer(String.serializer())
    private val json = Json { encodeDefaults = true }

    /**
     * 主表行 — 与 [toBatch] 共享派生逻辑(可单独被种子数据/旧 mapper 测试复用)。
     * **不**包含图片信息,只填 species 自己的列。
     */
    fun toEntity(
        s: Specimen,
        record: ScrapedRecord? = null,
        now: Long = System.currentTimeMillis(),
    ): SpeciesEntity {
        val useType = deriveUseType(s)
        val toxicity = deriveToxicityLevel(s)
        val edibility = deriveEdibility(s, toxicity)
        val imageUrls = extractAllImageUrls(s)

        return SpeciesEntity(
            mushroomId = toMushroomId(s, record),
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

            // 图片直接归属当前 `(mushroom_id, scraw_source)` 行
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
     * 把 [record] 转成单表写入包。
     * 不再产生 specimens / barcodes / distributionPoints 子表行。
     */
    fun toBatch(
        record: ScrapedRecord,
        now: Long = System.currentTimeMillis(),
    ): SpeciesSyncBatch {
        val species = toEntity(record.specimen, record, now)
        return SpeciesSyncBatch(
            species = listOf(species),
        )
    }

    /** 由 [ScrapedRecord.source] 映射成 scraw_source 字符串(小写,匹配 DB 列。 */
    fun toScrawSource(source: DataSource): String = when (source) {
        DataSource.SPECIMEN -> SpeciesEntity.SCRAW_SOURCE_SPECIES_SPECIMEN
        else -> source.name.lowercase()
    }

    /**
     * 物种目录 ID 与标本 ID 来自不同命名空间。标本沿用负数 ID，保持与
     * 已发布数据库和缓存目录兼容。
     */
    fun toMushroomId(s: Specimen, record: ScrapedRecord?): Int {
        val rawId = s.id.toInt()
        return if (record?.source == DataSource.SPECIMEN) {
            -rawId.coerceAtLeast(1)
        } else {
            rawId
        }
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
     */
    fun extractAllImageUrls(s: Specimen): List<String> {
        val out = LinkedHashSet<String>()
        collectUrls(out, s.sysFileList, primaryKey = "url", fallbackKey = "uf_src", fieldName = "sysFileList", specimenId = s.id)
        collectUrls(out, s.kibSpeciesPictures, primaryKey = "uf_src", fallbackKey = "url", fieldName = "kibSpeciesPictures", specimenId = s.id)
        return out.toList()
    }

    fun extractPrimaryImageUrl(s: Specimen): String? = extractAllImageUrls(s).firstOrNull()

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
                android.util.Log.w(TAG, "specimen $specimenId $fieldName[$index] is not an object (got ${item::class.simpleName}); skipped")
                continue
            }
            val raw = primitiveString(obj, primaryKey) ?: primitiveString(obj, fallbackKey)
            if (raw.isNullOrBlank()) continue
            out += normalizeUrl(raw)
        }
    }

    private fun primitiveString(obj: JsonObject, key: String): String? {
        val prim = obj[key] as? JsonPrimitive ?: return null
        if (!prim.isString && prim.toString() == "null") return null
        return prim.content.takeIf { it.isNotBlank() }
    }

    private fun normalizeUrl(raw: String): String =
        ApiClient.normalizeImageUrl(raw, IFLORA_HOST)

    private fun List<String>.toJsonArrayString(): String =
        json.encodeToString(stringListSerializer, this)
}

/** 一份抓取记录对应的单表写入包。 */
data class SpeciesSyncBatch(
    val species: List<SpeciesEntity>,
)
