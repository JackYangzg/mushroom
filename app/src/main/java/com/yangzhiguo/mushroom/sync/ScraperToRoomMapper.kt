package com.yangzhiguo.mushroom.sync

import android.util.Log
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.Specimen
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 把 iflora.cn 的全字段 Specimen（100 列）映射回现有 SpeciesEntity（23 列 MVP）。
 * 长描述字段截取前 200 字保留；多图字段（sysFileList / kibSpeciesPictures）会被合并去重
 * 后整体写入 [SpeciesEntity.images]（JSON 数组字符串），首张同时写入 [SpeciesEntity.imageUrl]
 * 供列表缩略图直接使用。
 *
 * 历史教训：原实现把图片列硬编码为 `JSONArray().toString()` 且 `imageUrl` 只取第一张，
 * 导致 specimen 33 / 4 / 5 等本应有 4–6 张图的条目最终落库只 1 张。重写时改用
 * kotlinx-serialization 直接遍历 JsonElement，避免 `JsonElement.toString() →
 * org.json.JSONArray` 的脆弱反序列化链，同时去掉静默吞异常的 `catch (_: Throwable) {}`。
 */
object ScraperToRoomMapper {
    private const val TAG = "ScraperToRoomMapper"
    private const val IFLORA_HOST = "https://fungi.iflora.cn"

    // 用 kotlinx-serialization 输出 JSON 字符串，避免 Android 的 org.json.JSONArray 在
    // 纯 JVM 单元测试里抛 "Method ... not mocked"。kotlinx Json 在 JVM/Android 行为一致。
    private val stringListSerializer = ListSerializer(String.serializer())
    private val json = Json { encodeDefaults = true }

    fun toEntity(s: Specimen, now: Long = System.currentTimeMillis()): SpeciesEntity {
        val useType = deriveUseType(s)
        val toxicity = deriveToxicityLevel(s)
        val edibility = deriveEdibility(s, toxicity)
        val imageUrls = extractAllImageUrls(s)

        return SpeciesEntity(
            id = s.id.toInt(),
            scientificName = s.speciesLatin.orEmpty(),
            chineseName = s.speciesChinese.orEmpty(),
            familyZh = s.familyChinese.orEmpty(),
            familyLa = s.familyEnglish.orEmpty(),
            genusZh = s.genusChinese.orEmpty(),
            genusLa = s.genusEnglish.orEmpty(),
            authority = s.famousPerson.orEmpty(),
            useType = useType,
            toxicityLevel = toxicity,
            edibility = edibility,
            capDescription = truncate(s.cap),
            gillDescription = truncate(s.lamella),
            stipeDescription = truncate(s.stipe),
            ringDescription = "",
            volvaDescription = "",
            sporeDescription = truncate(s.calmSeed),
            habitat = s.speciesHabitat.orEmpty(),
            season = "",
            distribution = s.distributionLocation.orEmpty(),
            altitudeRange = s.altitude.orEmpty(),
            identificationPoints = EMPTY_JSON_ARRAY,
            lookAlikeIds = "",
            toxicitySymptoms = "",
            images = imageUrls.toJsonArrayString(),
            model3dUrl = null,
            dnaBarcode = s.itsGenbank,
            sourceUrl = "",
            sourceTypes = deriveRecordTags(s).joinToString(","),
            imageUrl = imageUrls.firstOrNull(),
            imageLocalPath = null,   // 由 ImageCacheRepository 在用户查看页面后回填
            lastUpdated = now,
        )
    }

    /**
     * 提取所有可用的图片 URL。
     *
     * iflora.cn 列表 API 两种 schema：
     * - **新数据**：`sysFileList[*].url` 与 `kibSpeciesPictures[*].uf_src` 同时存在，
     *   值是相对路径如 `/admin/sys-file/local/xxx.jpg`，需补 `https://fungi.iflora.cn` 前缀。
     * - **老数据**：`sysFileList = []`，仅 `kibSpeciesPictures[*].uf_src` 有值，
     *   且通常是完整 URL `http://cloudfile.biotracks.cn/...jpg!bio123456`。
     *
     * 两类来源合并后按插入顺序去重，先出 `sysFileList`（站内文件，更稳定）再出
     * `kibSpeciesPictures`（兼容老数据）。
     */
    fun extractAllImageUrls(s: Specimen): List<String> {
        val out = LinkedHashSet<String>()
        collectInto(out, s.sysFileList, primaryKey = "url", fallbackKey = "uf_src", fieldName = "sysFileList", specimenId = s.id)
        collectInto(out, s.kibSpeciesPictures, primaryKey = "uf_src", fallbackKey = "url", fieldName = "kibSpeciesPictures", specimenId = s.id)
        return out.toList()
    }

    /** 保留旧调用点的兼容入口；新代码请优先用 [extractAllImageUrls]。 */
    fun extractPrimaryImageUrl(s: Specimen): String? = extractAllImageUrls(s).firstOrNull()

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

    internal fun hasRecord(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return normalized.isNotEmpty() &&
            normalized !in setOf("0", "否", "无", "none", "false", "null", "未知")
    }

    private fun truncate(s: String?, max: Int = 200): String =
        if (s.isNullOrEmpty()) "" else if (s.length <= max) s else s.substring(0, max)

    // ── 图片解析私有工具 ───────────────────────────────────────────────────

    private fun collectInto(
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

    private fun primitiveString(obj: JsonObject, key: String): String? {
        val prim = obj[key] as? JsonPrimitive ?: return null
        if (!prim.isString && prim.toString() == "null") return null
        return prim.content.takeIf { it.isNotBlank() }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "$IFLORA_HOST$trimmed"
        }
    }

    private fun List<String>.toJsonArrayString(): String =
        json.encodeToString(stringListSerializer, this)

    private const val EMPTY_JSON_ARRAY = "[]"
}
