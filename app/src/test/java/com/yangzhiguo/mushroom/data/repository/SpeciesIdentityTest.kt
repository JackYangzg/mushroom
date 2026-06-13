package com.yangzhiguo.mushroom.data.repository

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.scraper.Specimen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesIdentityTest {
    @Test
    fun matchingIdAndNormalizedScientificNameAreAccepted() {
        val current = species(mushroomId = 24, scientificName = "Amanita farinosa")
        val detail = Specimen(id = 24, speciesLatin = " amanita   FARINOSA ")

        assertTrue(SpeciesIdentity.matches(current, detail))
    }

    @Test
    fun mismatchedIdCannotOverwriteCurrentSpecies() {
        val current = species(mushroomId = 24, scientificName = "Amanita farinosa")
        val detail = Specimen(id = 1901, speciesLatin = "Amanita farinosa")

        assertFalse(SpeciesIdentity.matches(current, detail))
    }

    @Test
    fun mismatchedNameCannotOverwriteCurrentSpecies() {
        val current = species(mushroomId = 24, scientificName = "Amanita farinosa")
        val detail = Specimen(id = 24, speciesLatin = "Hohenbuehelia petaloides")

        assertFalse(SpeciesIdentity.matches(current, detail))
    }

    private fun species(mushroomId: Int, scientificName: String) = SpeciesEntity(
        id = 0L,
        mushroomId = mushroomId,
        scientificName = scientificName,
        lastUpdated = 1L,
    )
}