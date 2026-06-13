package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.ScrapedRecord
import com.yangzhiguo.mushroom.scraper.Specimen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRecordMergerTest {
    @Test
    fun specimenRecordsAreNotCollapsedByScientificName() {
        val records = listOf(
            record(101, "Amanita sp.", DataSource.SPECIMEN),
            record(102, "Amanita sp.", DataSource.SPECIMEN),
        )

        val merged = CatalogRecordMerger.merge(records)

        assertEquals(listOf(101L, 102L), merged.map { it.base.specimen.id })
    }

    @Test
    fun differentScientificNamesRemainSeparate() {
        val records = listOf(
            record(id = 20, name = "Unrelated specimen", source = DataSource.GENERAL_DIRECTORY),
            record(id = 20, name = "Strobilomyces latirimosus", source = DataSource.EDIBLE_FUNGI),
        )

        val merged = CatalogRecordMerger.merge(records)

        // 不同 scientific name → 2 条 merged record
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.base.specimen.speciesLatin == "Unrelated specimen" })
        assertTrue(merged.any { it.base.specimen.speciesLatin == "Strobilomyces latirimosus" })
    }

    @Test
    fun threeCatalogueSourcesMergeAndSourceTypesAccumulate() {
        val records = listOf(
            record(6, "Termitomyces fragilis", DataSource.GENERAL_DIRECTORY),
            record(6, " termitomyces   FRAGILIS ", DataSource.EDIBLE_FUNGI, edible = "是"),
            record(6, "Termitomyces fragilis", DataSource.TOXIC_FUNGI, toxic = "是"),
        )

        val merged = CatalogRecordMerger.merge(records).single()

        assertEquals(DataSource.GENERAL_DIRECTORY, merged.base.source)
        assertTrue("GENERAL_DIRECTORY" in merged.sourceTypes)
        assertTrue("EDIBLE_FUNGI" in merged.sourceTypes)
        assertTrue("TOXIC_FUNGI" in merged.sourceTypes)
        assertTrue("EDIBLE" in merged.sourceTypes)
        assertTrue("POISONOUS" in merged.sourceTypes)
    }

    @Test
    fun edibleOrToxicOnlySpeciesRemainInCatalogue() {
        val records = listOf(
            record(1001, "Edible only", DataSource.EDIBLE_FUNGI, edible = "是"),
            record(1002, "Toxic only", DataSource.TOXIC_FUNGI, toxic = "是"),
        )

        val merged = CatalogRecordMerger.merge(records)

        assertEquals(
            setOf(DataSource.EDIBLE_FUNGI, DataSource.TOXIC_FUNGI),
            merged.map { it.base.source }.toSet(),
        )
    }

    private fun record(
        id: Long,
        name: String,
        source: DataSource,
        edible: String? = null,
        toxic: String? = null,
    ): ScrapedRecord {
        val specimen = Specimen(
            id = id,
            speciesLatin = name,
            edibleFungus = edible,
            toxicFungus = toxic,
        )
        return ScrapedRecord(specimen, source, source.detailUrl(specimen))
    }
}
