package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.Specimen
import org.json.JSONArray

/**
 * 把 iflora.cn 的全字段 Specimen（100 列）映射回现有 SpeciesEntity（23 列 MVP）。
 * 长描述字段截取前 200 字保留；复杂数组（lookAlike/images）留空 JSON 数组。
 */
object ScraperToRoomMapper {

    fun toEntity(s: Specimen, now: Long = System.currentTimeMillis()): SpeciesEntity {
        val useType = deriveUseType(s)
        val toxicity = deriveToxicityLevel(s)
        val edibility = deriveEdibility(s, toxicity)

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
            identificationPoints = JSONArray().toString(),
            lookAlikeIds = "",
            toxicitySymptoms = "",
            images = JSONArray().toString(),
            model3dUrl = null,
            dnaBarcode = s.itsGenbank,
            sourceUrl = "",
            imageUrl = extractPrimaryImageUrl(s),
            imageLocalPath = null,   // 由 ImageCacheRepository 在用户查看页面后回填
            lastUpdated = now,
        )
    }

    /**
     * 从 iflora.cn API 返回的 sysFileList / kibSpeciesPictures 中取第一张图的 URL。
     * sysFileList 形态：[{"url":"/admin/sys-file/local/xxx.jpg", ...}, ...]
     * kibSpeciesPictures 形态：[{"uf_src":"/admin/...", ...}, ...]
     */
    fun extractPrimaryImageUrl(s: Specimen): String? {
        val sysFile = s.sysFileList
        if (sysFile != null && !sysFile.toString().isNullOrBlank() && sysFile.toString() != "null") {
            try {
                val arr = org.json.JSONArray(sysFile.toString())
                if (arr.length() > 0) {
                    val first = arr.getJSONObject(0)
                    val rel = first.optString("url").ifBlank { first.optString("uf_src") }
                    if (rel.isNotBlank()) return if (rel.startsWith("http")) rel else "https://fungi.iflora.cn$rel"
                }
            } catch (_: Throwable) {}
        }
        val kib = s.kibSpeciesPictures
        if (kib != null && !kib.toString().isNullOrBlank() && kib.toString() != "null") {
            try {
                val arr = org.json.JSONArray(kib.toString())
                if (arr.length() > 0) {
                    val first = arr.getJSONObject(0)
                    val rel = first.optString("uf_src").ifBlank { first.optString("url") }
                    if (rel.isNotBlank()) return if (rel.startsWith("http")) rel else "https://fungi.iflora.cn$rel"
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun deriveUseType(s: Specimen): UseType = when {
        s.medicinalFungus == "是" -> UseType.MEDICINAL
        s.edibleFungus == "是" -> UseType.EDIBLE
        s.toxicFungus == "是" -> UseType.POISONOUS
        else -> UseType.UNREPORTED
    }

    fun deriveToxicityLevel(s: Specimen): ToxicityLevel = when {
        s.toxicFungus == "是" -> ToxicityLevel.TOXIC
        else -> ToxicityLevel.NONE
    }

    fun deriveEdibility(s: Specimen, toxicity: ToxicityLevel): Edibility = when {
        toxicity == ToxicityLevel.TOXIC -> Edibility.INEDIBLE
        s.edibleFungus == "是" -> Edibility.EDIBLE
        else -> Edibility.UNKNOWN
    }

    private fun truncate(s: String?, max: Int = 200): String =
        if (s.isNullOrEmpty()) "" else if (s.length <= max) s else s.substring(0, max)
}