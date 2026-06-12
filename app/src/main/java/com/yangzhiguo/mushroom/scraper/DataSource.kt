package com.yangzhiguo.mushroom.scraper

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class DataSource(
    val kind: Kind,
) {
    SPECIMEN(Kind.SPECIMEN),
    GENERAL_DIRECTORY(Kind.SPECIES),
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
