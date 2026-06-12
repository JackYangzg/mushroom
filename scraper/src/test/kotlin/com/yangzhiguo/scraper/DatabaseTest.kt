package com.yangzhiguo.scraper

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class DatabaseTest {

    @Test
    fun deduplicatesWithinSourceAndRetainsDifferentSources() {
        val dbPath = Files.createTempDirectory("mushroom-db-test").resolve("mushroom.db")
        val specimen = Specimen(id = 5, speciesLatin = "Coltricia crassa")
        val url = DataSource.GENERAL_DIRECTORY.detailUrl(specimen)

        Database(dbPath).use { db ->
            db.upsertSpecimen(specimen, DataSource.EDIBLE.name, url)
            db.upsertSpecimen(specimen, DataSource.EDIBLE.name, url)
            db.upsertSpecimen(specimen, DataSource.GENERAL_DIRECTORY.name, url)
            db.flush()

            assertEquals(2, db.countSpecimens())
        }
    }
}
