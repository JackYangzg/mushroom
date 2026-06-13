package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.ScrapedRecord
import com.yangzhiguo.mushroom.scraper.Specimen

/**
 * 把同 source 抓到的 records 合并:同科学名(scraped via [normalizedName])的多条记录
 * 折叠成 1 条,`sourceTypes` 累加;优先保留 sourcePriority 最低(GENERAL_DIRECTORY 优先)。
 *
 * v10 简化:不再需要 extraSpecimens(子表删了)。
 *
 * 注意:跨 source 的合并不在这里做——按 v10 设计每个 source 单独入库,各占一行,
 * 由 `findByMushroomId(mushroom_id)` 在 UI 层做单行返回(mushroom_id 维度天然去重)。
 */
internal object CatalogRecordMerger {
    val catalogueSources = setOf(
        DataSource.GENERAL_DIRECTORY,
        DataSource.EDIBLE_FUNGI,
        DataSource.TOXIC_FUNGI,
    )

    fun merge(records: List<ScrapedRecord>): List<MergedCatalogRecord> {
        if (records.firstOrNull()?.source == DataSource.SPECIMEN) {
            return records.map { record ->
                MergedCatalogRecord(
                    base = record,
                    sourceTypes = listOf(record.source.name) +
                        ScraperToRoomMapper.deriveRecordTags(record.specimen),
                )
            }
        }
        return records
            .asSequence()
            .filter { it.source in catalogueSources }
            .groupBy { normalizedName(it.specimen) }
            .values
            .map { catalogueRecords ->
                val base = catalogueRecords.minBy { sourcePriority(it.source) }
                val sourceTypes = catalogueRecords
                    .flatMap { record ->
                        listOf(record.source.name) +
                            ScraperToRoomMapper.deriveRecordTags(record.specimen)
                    }
                    .distinct()
                MergedCatalogRecord(
                    base = base,
                    sourceTypes = sourceTypes,
                )
            }
            .sortedBy { it.base.specimen.id }
    }

    private fun normalizedName(specimen: Specimen): String =
        specimen.speciesLatin
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: "id:${specimen.id}"

    private fun sourcePriority(source: DataSource): Int = when (source) {
        DataSource.SPECIMEN -> 3
        DataSource.GENERAL_DIRECTORY -> 0
        DataSource.EDIBLE_FUNGI -> 1
        DataSource.TOXIC_FUNGI -> 2
    }
}

internal data class MergedCatalogRecord(
    val base: ScrapedRecord,
    val sourceTypes: List<String>,
)
