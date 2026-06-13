package com.yangzhiguo.mushroom.scraper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperTest {
    @Test
    fun failedPageIsSkippedAndLaterPagesAreStillCollected() = runBlocking {
        val requestedPages = mutableListOf<Pair<DataSource, Int>>()
        val scraper = Scraper(
            pageLoader = { source, page, _ ->
                requestedPages += source to page
                when {
                    source != DataSource.GENERAL_DIRECTORY -> emptyList()
                    page == 1 -> listOf(Specimen(id = 1, speciesLatin = "Amanita muscaria"))
                    page == 2 -> error("temporary page failure")
                    page == 3 -> listOf(Specimen(id = 3, speciesLatin = "Amanita muscaria"))
                    else -> emptyList()
                }
            },
        )

        val result = scraper.fetchAll()

        assertEquals(2, result.size)
        assertTrue(result.all { it.source == DataSource.GENERAL_DIRECTORY })
        assertEquals(listOf(1L, 3L), result.map { it.specimen.id })
        assertTrue(DataSource.GENERAL_DIRECTORY to 3 in requestedPages)
    }

    @Test
    fun consecutiveFailuresStopOnlyTheCurrentSource() = runBlocking {
        val requestedSources = mutableListOf<DataSource>()
        val scraper = Scraper(
            pageLoader = { source, _, _ ->
                requestedSources += source
                if (source == DataSource.EDIBLE_FUNGI) {
                    error("source unavailable")
                }
                emptyList()
            },
        )

        assertTrue(scraper.fetchAll().isEmpty())
        assertTrue(DataSource.GENERAL_DIRECTORY in requestedSources)
        assertTrue(DataSource.TOXIC_FUNGI in requestedSources)
    }

    @Test
    fun defaultRequestTimeoutIsFifteenSeconds() {
        assertEquals(15_000, ApiClient.DEFAULT_TIMEOUT_MS)
    }
}