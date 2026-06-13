package com.yangzhiguo.mushroom.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for `SpeciesDao.searchByName`: search must hit the entire database,
 * not just the curated three sources plus `species_specimen`.
 *
 * Pre-fix `searchByName` started with
 *   `WHERE species.scraw_source IN ('species_specimen', 'general_directory',
 *    'edible_fungi', 'toxic_fungi')`
 * which would silently drop any row whose `scraw_source` was outside that
 * allowlist (e.g. a future ingest source like `mycoflora`).
 *
 * Post-fix the outer allowlist is removed; the inner NOT EXISTS dedup is kept so
 * one mushroom still surfaces as a single authoritative row, mirroring the
 * gallery default (`observeAll`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeciesDaoSearchTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Pre-fix: `mycoflora` row is dropped by the outer
     * `scraw_source IN (...)` allowlist — even though no curated source has a
     * corresponding record, the row never reaches the dedup step.
     *
     * Post-fix: outer filter removed; with no curated counterpart the NOT EXISTS
     * dedup trivially returns TRUE, so the row surfaces.
     */
    @Test
    fun searchByName_surfacesFutureSourceWithNoCuratedCounterpart() = runBlocking {
        val now = System.currentTimeMillis()
        val futureSource = SpeciesEntity(
            mushroomId = 999, // unique — no curated record shares this id
            scrawSource = "mycoflora", // future / out-of-allowlist source
            lastUpdated = now,
            scientificName = "FutureUniqueSpecies",
            chineseName = "未来菇",
            aliasNames = emptyList(),
            sourceUrl = "https://example.com/mycoflora/999",
            sourceTypes = "",
        )
        db.speciesDao().upsertAll(listOf(futureSource))

        val hits = db.speciesDao().searchByName("未来菇").first()
        val hitSources = hits.map { it.scrawSource }.toSet()

        assertEquals(
            "future source row must surface when no curated counterpart exists",
            setOf("mycoflora"),
            hitSources,
        )
    }

    /**
     * Sanity check that the dedup is still active post-fix: a `mycoflora` row
     * with the same `mushroomId` as a curated row is shadowed by the curated
     * row, mirroring the gallery default behavior.
     */
    @Test
    fun searchByName_dedupsCuratedOverFutureSource() = runBlocking {
        val now = System.currentTimeMillis()
        val curated = SpeciesEntity(
            mushroomId = 100, // SAME id → should be deduped against curated
            scrawSource = SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY,
            lastUpdated = now,
            scientificName = "SharedMushroom",
            chineseName = "共享菇",
            aliasNames = emptyList(),
            sourceUrl = "https://example.com/curated/100",
            sourceTypes = "EDIBLE",
        )
        val futureSource = SpeciesEntity(
            mushroomId = 100,
            scrawSource = "mycoflora",
            lastUpdated = now,
            scientificName = "SharedMushroom",
            chineseName = "共享菇",
            aliasNames = emptyList(),
            sourceUrl = "https://example.com/mycoflora/100",
            sourceTypes = "",
        )
        db.speciesDao().upsertAll(listOf(curated, futureSource))

        val hits = db.speciesDao().searchByName("共享菇").first()
        val hitSources = hits.map { it.scrawSource }.toSet()

        assertEquals(
            "dedup should keep only the curated (general_directory) row",
            setOf(SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY),
            hitSources,
        )
    }

    /**
     * Pre-fix: `species_specimen` row is in the allowlist AND passes dedup
     * (the `scraw_source = 'species_specimen'` short-circuit) — it shows up.
     * This test pins the existing behavior so the dedup fix doesn't regress it.
     */
    @Test
    fun searchByName_includesSpecimenRow() = runBlocking {
        val now = System.currentTimeMillis()
        val specimen = SpeciesEntity(
            mushroomId = -1, // specimen uses negative id per ScraperToRoomMapper
            scrawSource = SpeciesEntity.SCRAW_SOURCE_SPECIES_SPECIMEN,
            lastUpdated = now,
            scientificName = "SpecimenOnly",
            chineseName = "标本独存",
            aliasNames = emptyList(),
            sourceUrl = "https://example.com/specimen/1",
            sourceTypes = "",
        )
        db.speciesDao().upsertAll(listOf(specimen))

        val hits = db.speciesDao().searchByName("标本独存").first()
        val hitSources = hits.map { it.scrawSource }.toSet()

        assertTrue(
            "specimen row must surface in search, got $hitSources",
            SpeciesEntity.SCRAW_SOURCE_SPECIES_SPECIMEN in hitSources,
        )
    }
}
