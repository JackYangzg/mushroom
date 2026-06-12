package com.yangzhiguo.scraper

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class DatabaseTest {

    @Test
    fun sourceUrlIsTheOnlyUniqueKey() {
        val dbPath = Files.createTempDirectory("mushroom-db-test").resolve("mushroom.db")
        val specimen = Specimen(id = 5, speciesLatin = "Coltricia crassa")
        val url = DataSource.GENERAL_DIRECTORY.detailUrl(specimen)

        Database(dbPath).use { db ->
            db.upsertSpecimen(specimen, DataSource.SPECIMEN.name, url)
            db.upsertSpecimen(specimen.copy(speciesLatin = "Different name"), DataSource.SPECIMEN.name, url)
            db.upsertSpecimen(specimen, DataSource.GENERAL_DIRECTORY.name, url)
            db.flush()

            assertEquals(1, db.countSpecimens())
        }
    }
}
