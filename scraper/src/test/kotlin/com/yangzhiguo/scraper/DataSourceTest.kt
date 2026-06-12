package com.yangzhiguo.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DataSourceTest {

    @Test
    fun onlyRequestedSourcesArePresent() {
        assertEquals(
            setOf(DataSource.SPECIMEN, DataSource.GENERAL_DIRECTORY),
            DataSource.entries.toSet(),
        )
    }

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
    fun specimenAndSpeciesDetailLinksRemainDistinct() {
        val specimen = Specimen(id = 5, speciesLatin = "Coltricia crassa")

        assertNotEquals(
            DataSource.SPECIMEN.detailUrl(specimen),
            DataSource.GENERAL_DIRECTORY.detailUrl(specimen),
        )
    }

    @Test
    fun sameLinkIsDuplicateRegardlessOfSourceOrName() {
        val specimen = Specimen(id = 5, speciesLatin = "Coltricia crassa")
        val canonical = DataSource.GENERAL_DIRECTORY.detailUrl(specimen)

        assertEquals(
            1,
            deduplicateRecords(
                listOf(
                    ScrapedRecord(specimen.copy(speciesLatin = "Different name"), DataSource.SPECIMEN, canonical),
                    ScrapedRecord(specimen, DataSource.GENERAL_DIRECTORY, canonical),
                ),
            ).size,
        )
    }
}
