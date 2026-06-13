package com.yangzhiguo.mushroom.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yangzhiguo.mushroom.data.local.AppDatabase
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AliasBackfillerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "alias_mushroom.db").delete()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "alias_mushroom.db").delete()
    }

    @Test
    fun backfill_matchesEitherNameAndDoesNotOverwriteExistingAliases() = runBlocking {
        val source = readAliasSource()
        val existingAliases = listOf("人工维护别名")
        db.speciesDao().upsertAll(
            listOf(
                species(1, scientificName = source.scientificName, chineseName = "不匹配中文名"),
                species(2, scientificName = "No scientific match", chineseName = source.commonName),
                species(3, scientificName = "", chineseName = ""),
                species(
                    4,
                    scientificName = source.scientificName,
                    chineseName = source.commonName,
                    aliasNames = existingAliases,
                ),
            ),
        )

        val result = AliasBackfiller(context, db).backfill()
        val rows = db.speciesDao().getAllForSync().associateBy { it.mushroomId }

        assertFalse(result.skipped)
        assertEquals(2, result.updated)
        assertEquals(source.aliases, rows.getValue(1).aliasNames)
        assertEquals(source.aliases, rows.getValue(2).aliasNames)
        assertEquals(emptyList<String>(), rows.getValue(3).aliasNames)
        assertEquals(existingAliases, rows.getValue(4).aliasNames)
    }

    private fun readAliasSource(): AliasSource {
        val file = File(context.cacheDir, "alias_test_source.db")
        context.assets.open("alias_mushroom.db").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }

        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { aliasDb ->
                aliasDb.rawQuery(
                    """
                    SELECT scientificName, commonName, aliases
                    FROM mushroom_alias
                    WHERE trim(scientificName) != ''
                      AND trim(commonName) != ''
                      AND aliases IS NOT NULL
                      AND aliases != ''
                      AND aliases != '[]'
                    LIMIT 1
                    """.trimIndent(),
                    null,
                ).use { cursor ->
                    check(cursor.moveToFirst()) { "alias_mushroom.db has no usable alias row" }
                    AliasSource(
                        scientificName = cursor.getString(0),
                        commonName = cursor.getString(1),
                        aliases = parseAliases(cursor.getString(2)),
                    )
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun parseAliases(json: String): List<String> =
        com.yangzhiguo.mushroom.data.local.Converters().stringToStringList(json)

    private fun species(
        id: Int,
        scientificName: String,
        chineseName: String,
        aliasNames: List<String> = emptyList(),
    ) = SpeciesEntity(
        mushroomId = id,
        scrawSource = SpeciesEntity.SCRAW_SOURCE_GENERAL_DIRECTORY,
        lastUpdated = 1L,
        scientificName = scientificName,
        chineseName = chineseName,
        aliasNames = aliasNames,
    )

    private data class AliasSource(
        val scientificName: String,
        val commonName: String,
        val aliases: List<String>,
    )
}
