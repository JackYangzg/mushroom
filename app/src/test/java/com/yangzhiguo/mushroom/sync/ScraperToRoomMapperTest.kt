package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.domain.model.Edibility
import com.yangzhiguo.mushroom.domain.model.ToxicityLevel
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.Specimen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperToRoomMapperTest {

    @Test
    fun medicinalDescriptionCountsAsMedicinalRecord() {
        val specimen = Specimen(
            medicinalFungus = "Antitumor; antioxidant",
            edibleFungus = "0",
            toxicFungus = "0",
        )

        assertEquals(UseType.MEDICINAL, ScraperToRoomMapper.deriveUseType(specimen))
    }

    @Test
    fun edibleAndToxicRecordsRemainIndependentlyClassifiable() {
        val specimen = Specimen(
            edibleFungus = "是",
            toxicFungus = "是",
        )
        val toxicity = ScraperToRoomMapper.deriveToxicityLevel(specimen)

        assertEquals(ToxicityLevel.TOXIC, toxicity)
        assertEquals(Edibility.EDIBLE, ScraperToRoomMapper.deriveEdibility(specimen, toxicity))
        assertEquals(
            listOf(
                UseType.EDIBLE.name,
                UseType.POISONOUS.name,
                UseType.CAUTION.name,
            ),
            ScraperToRoomMapper.deriveRecordTags(specimen),
        )
        assertEquals(UseType.CAUTION, ScraperToRoomMapper.deriveUseType(specimen))
    }

    @Test
    fun edibleMedicinalAndToxicRecordsKeepAllDimensions() {
        val specimen = Specimen(
            edibleFungus = "是",
            medicinalFungus = "Antitumor",
            toxicFungus = "是",
        )

        assertEquals(
            listOf(
                UseType.EDIBLE.name,
                UseType.MEDICINAL.name,
                UseType.POISONOUS.name,
                UseType.CAUTION.name,
            ),
            ScraperToRoomMapper.deriveRecordTags(specimen),
        )
    }

    @Test
    fun explicitConditionalRecordIsCaution() {
        val specimen = Specimen(conditionallyFungus = "是")

        assertTrue(ScraperToRoomMapper.hasCautionRecord(specimen))
        assertEquals(listOf(UseType.CAUTION.name), ScraperToRoomMapper.deriveRecordTags(specimen))
    }

    @Test
    fun zeroAndEmptyValuesDoNotCountAsRecords() {
        assertFalse(ScraperToRoomMapper.hasRecord(null))
        assertFalse(ScraperToRoomMapper.hasRecord(""))
        assertFalse(ScraperToRoomMapper.hasRecord("0"))
        assertFalse(ScraperToRoomMapper.hasRecord("否"))
        assertTrue(ScraperToRoomMapper.hasRecord("是"))
        assertTrue(ScraperToRoomMapper.hasRecord("Antibacteria"))
    }
}
