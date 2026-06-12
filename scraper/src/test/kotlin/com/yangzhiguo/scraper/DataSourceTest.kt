package com.yangzhiguo.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DataSourceTest {

    @Test
    fun dedupeUsesNormalizedNameAndCanonicalLink() {
        val specimen = Specimen(id = 5, speciesLatin = "  Coltricia   crassa ")
        val canonical = DataSource.GENERAL_DIRECTORY.detailUrl(specimen)
        val records = listOf(
            ScrapedRecord(specimen, DataSource.GENERAL_DIRECTORY, canonical),
            ScrapedRecord(
                specimen.copy(speciesLatin = "coltricia crassa"),
                DataSource.GENERAL_DIRECTORY,
                canonical.uppercase(),
            ),
        )

        assertEquals(1, deduplicateRecords(records).size)
    }

    @Test
    fun specimenAndSpeciesDetailLinksRemainDistinct() {
        val specimen = Specimen(id = 5, speciesLatin = "Coltricia crassa")

        assertNotEquals(
            DataSource.SPECIMEN.detailUrl(specimen),
            DataSource.GENERAL_DIRECTORY.detailUrl(specimen),
        )
    }

    @Test
    fun sameNameAndLinkRemainDistinctAcrossSources() {
        val specimen = Specimen(id = 5, speciesLatin = "Coltricia crassa")
        val canonical = DataSource.GENERAL_DIRECTORY.detailUrl(specimen)

        assertEquals(
            2,
            deduplicateRecords(
                listOf(
                    ScrapedRecord(specimen, DataSource.EDIBLE, canonical),
                    ScrapedRecord(specimen, DataSource.GENERAL_DIRECTORY, canonical),
                ),
            ).size,
        )
    }
}
