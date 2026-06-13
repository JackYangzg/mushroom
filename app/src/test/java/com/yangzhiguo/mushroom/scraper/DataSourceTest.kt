package com.yangzhiguo.mushroom.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DataSourceTest {

    @Test
    fun dedupeUsesOnlyCanonicalLink() {
        val specimen = Specimen(id = 5, speciesLatin = "  Coltricia   crassa ")
        val canonical = DataSource.GENERAL_DIRECTORY.detailUrl(specimen)
        val records = listOf(
            ScrapedRecord(specimen, DataSource.GENERAL_DIRECTORY, canonical),
            ScrapedRecord(
                specimen.copy(speciesLatin = "coltricia crassa"),
                DataSource.GENERAL_DIRECTORY,
                canonical,
            ),
        )

        assertEquals(1, deduplicateRecords(records).size)
    }

    @Test
    fun catalogueSourceFiltersAreDistinct() {
        assertNotEquals(
            DataSource.EDIBLE_FUNGI.filter,
            DataSource.TOXIC_FUNGI.filter,
        )
        assertNotEquals(
            DataSource.GENERAL_DIRECTORY.filter,
            DataSource.EDIBLE_FUNGI.filter,
        )
    }

    @Test
    fun allFourSourcesArePresent() {
        assertEquals(
            setOf(
                DataSource.SPECIMEN,
                DataSource.GENERAL_DIRECTORY,
                DataSource.EDIBLE_FUNGI,
                DataSource.TOXIC_FUNGI,
            ),
            DataSource.entries.toSet(),
        )
    }

    @Test
    fun specimenUsesSpecimenDetailLink() {
        assertEquals(
            "https://fungi.iflora.cn/#/specimenDetail/5",
            DataSource.SPECIMEN.detailUrl(Specimen(id = 5)),
        )
    }

    @Test
    fun sameLinkFromDifferentSourcesKeepsSourceProvenance() {
        val specimen = Specimen(id = 5, speciesLatin = "Coltricia crassa")
        val canonical = DataSource.GENERAL_DIRECTORY.detailUrl(specimen)

        assertEquals(
            2,
            deduplicateRecords(
                listOf(
                    ScrapedRecord(specimen.copy(speciesLatin = "Different name"), DataSource.EDIBLE_FUNGI, canonical),
                    ScrapedRecord(specimen, DataSource.GENERAL_DIRECTORY, canonical),
                ),
            ).size,
        )
    }
}
