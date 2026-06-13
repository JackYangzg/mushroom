package com.yangzhiguo.mushroom.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 保证随包数据库能通过 App 启动时相同的 Room schema identity 校验。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackagedDatabaseTest {

    @Test
    fun packagedDatabaseOpensWithAppSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB_NAME)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .createFromAsset(AppDatabase.DB_NAME)
            .build()

        try {
            runBlocking {
                val dao = database.speciesDao()
                val count = dao.count()
                check(count > 0) { "Packaged database contains no species" }

                val catalogue = dao.observeAll().first()
                // 图鉴默认只展示 curated 三 source (general_directory / edible_fungi / toxic_fungi),
                // 不再混入 species_specimen。specimen 行仍在表中供 LLM 识别匹配 / 详情页使用。
                val allowedSources = setOf(
                    SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY,
                    SpeciesEntity.SCRAW_SOURCE_EDIBLE_FUNGI,
                    SpeciesEntity.SCRAW_SOURCE_TOXIC_FUNGI,
                )
                assertEquals(
                    "observeAll must only return curated sources",
                    allowedSources,
                    catalogue.map { it.scrawSource }.toSet(),
                )
                assertEquals(
                    "Catalog queries must return one row per mushroomId",
                    catalogue.map { it.mushroomId }.distinct().size,
                    catalogue.size,
                )
                val catalogNames = catalogue
                    .map { it.scientificName.trim().lowercase() }
                    .filter { it.isNotBlank() }
                assertEquals(
                    "Catalog must expose one authoritative row per scientific name",
                    catalogNames.distinct().size,
                    catalogNames.size,
                )
            }
        } finally {
            database.close()
            context.deleteDatabase(TEST_DB_NAME)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "packaged-mushroom-test.db"
    }
}
