package com.yangzhiguo.mushroom.scraper

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class DataSource(
    val kind: Kind,
    val filters: Map<String, String> = emptyMap(),
) {
    SPECIMEN(Kind.SPECIMEN),
    EDIBLE(Kind.SPECIES, mapOf("edibleFungus" to "是")),
    GENERAL_DIRECTORY(Kind.SPECIES),
    TOXIC(Kind.SPECIES, mapOf("toxicFungus" to "是")),
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
        get() = "$sourceType|${normalizedName(specimen)}|${sourceUrl.trim().lowercase()}"
}

internal fun normalizedName(specimen: Specimen): String =
    sequenceOf(specimen.speciesLatin, specimen.speciesChinese, specimen.speciesCommon)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        ?: "id:${specimen.id}"

internal fun deduplicateRecords(records: Iterable<ScrapedRecord>): List<ScrapedRecord> =
    records.distinctBy(ScrapedRecord::dedupeKey)
