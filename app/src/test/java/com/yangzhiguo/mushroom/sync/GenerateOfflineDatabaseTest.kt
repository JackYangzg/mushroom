package com.yangzhiguo.mushroom.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yangzhiguo.mushroom.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager

/**
 * 使用 App 运行时同步入口生成随包发布的离线数据库。
 *
 * 运行:
 * ```
 * ./gradlew :app:testDebugUnitTest \
 *   --tests "com.yangzhiguo.mushroom.sync.GenerateOfflineDatabaseTest" \
 *   -PgenerateOfflineDatabase
 * ```
 *
 * 该测试直接使用 Room 创建 [AppDatabase],再调用 [SyncRepository.runFullSync]。
 * 因此 assets 数据库与 App 内同步在 schema、映射、DAO 写入和别名回填上没有分叉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenerateOfflineDatabaseTest {

    @Test
    fun generateFromAppSync() {
        assumeTrue(
            "Only runs with -PgenerateOfflineDatabase",
            System.getProperty(GENERATE_PROPERTY) == "true",
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase(TEMP_DB_NAME)

        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEMP_DB_NAME)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        val sourceFile = context.getDatabasePath(TEMP_DB_NAME)

        try {
            val repository = SyncRepository(
                context = context,
                speciesDao = database.speciesDao(),
                roomDb = database,
                aliasBackfiller = AliasBackfiller(context, database),
            )

            runBlocking { repository.runFullSync() }
            val success = repository.state.value as? SyncState.Success
                ?: error("App sync did not complete successfully: ${repository.state.value}")
            check(success.totalSpecimens > 0) { "App sync produced an empty database" }

            database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "Generated Room database failed integrity_check"
                }
            }
            database.openHelper.writableDatabase.query(
                "SELECT COUNT(DISTINCT scraw_source) FROM mushroom_species",
            ).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 4) {
                    "Expected all 4 data sources in the generated database"
                }
            }
        } finally {
            database.close()
        }

        check(sourceFile.isFile && sourceFile.length() > 0) {
            "Room database file was not created: ${sourceFile.absolutePath}"
        }

        val output = File("src/main/assets/${AppDatabase.DB_NAME}").absoluteFile
        val outputDir = requireNotNull(output.parentFile)
        outputDir.mkdirs()
        val staging = File(outputDir, "${output.name}.tmp")
        sourceFile.copyTo(staging, overwrite = true)
        verifyPackagedDatabase(staging)
        Files.move(
            staging.toPath(),
            output.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        File(output.absolutePath + "-wal").delete()
        File(output.absolutePath + "-shm").delete()

        println("Offline database generated from App sync: ${output.absolutePath}")
        println("Size: ${output.length()} bytes")
    }

    private fun verifyPackagedDatabase(file: File) {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { result ->
                    check(result.next() && result.getString(1) == "ok") {
                        "Packaged database failed integrity_check"
                    }
                }
                statement.executeQuery("PRAGMA user_version").use { result ->
                    check(result.next() && result.getInt(1) == 12) {
                        "Expected Room schema version 12"
                    }
                }
                statement.executeQuery(
                    "SELECT identity_hash FROM room_master_table WHERE id = 42",
                ).use { result ->
                    check(result.next() && result.getString(1).isNotBlank()) {
                        "Room identity hash is missing"
                    }
                }
                statement.executeQuery("SELECT COUNT(*) FROM mushroom_species").use { result ->
                    check(result.next() && result.getInt(1) > 0) {
                        "Packaged database contains no species"
                    }
                }
            }
        }
    }

    private companion object {
        const val GENERATE_PROPERTY = "mushroom.generateOfflineDatabase"
        const val SYNC_PREFS = "sync_prefs"
        const val TEMP_DB_NAME = "mushroom-offline-generator.db"
    }
}
