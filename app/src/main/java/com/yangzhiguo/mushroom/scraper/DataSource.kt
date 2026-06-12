package com.yangzhiguo.mushroom.scraper

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 一个数据源 = 一个 iflora.cn 列表/筛选页 + 它的详情 URL 模板。
 *
 * 4 个数据源共用 `getSpeciesList` 端点,通过 [filter] 注入的 query 参数区分:
 *  - GENERAL_DIRECTORY: 不带 filter,返回全部 761 个名录种
 *  - EDIBLE_FUNGI:  filter=`edibleFungus=是`  → 食用真菌名录
 *  - TOXIC_FUNGI:   filter=`toxicFungus=是`   → 有毒真菌名录
 *  - SPECIMEN:      走 `/admin/kibspecimen/page` 端点(SPECIMEN kind)
 */
enum class DataSource(
    val kind: Kind,
    val filter: String? = null,
) {
    SPECIMEN(Kind.SPECIMEN),
    GENERAL_DIRECTORY(Kind.SPECIES, filter = null),
    EDIBLE_FUNGI(Kind.SPECIES, filter = "edibleFungus=是"),
    TOXIC_FUNGI(Kind.SPECIES, filter = "toxicFungus=是"),
    ;

    enum class Kind { SPECIMEN, SPECIES }

    fun detailUrl(specimen: Specimen): String = when (kind) {
        Kind.SPECIMEN ->
            "$BASE_URL/#/specimenDetail/${specimen.id}"
        Kind.SPECIES -> {
            val encodedName = URLEncoder.encode(
                specimen.speciesLatin.orEmpty().trim(),
                StandardCharsets.UTF_8.name(),
            ).replace("+", "%20")
            "$BASE_URL/#/speciesDetail/${specimen.id}/$encodedName/list"
        }
    }

    companion object {
        const val BASE_URL = "https://fungi.iflora.cn"
    }
}

data class ScrapedRecord(
    val specimen: Specimen,
    val source: DataSource,
    val sourceUrl: String,
) {
    val sourceType: String
        get() = source.name

    val dedupeKey: String
        get() = sourceUrl.trim()
}

internal fun deduplicateRecords(records: Iterable<ScrapedRecord>): List<ScrapedRecord> =
    records.distinctBy(ScrapedRecord::dedupeKey)
