package com.yangzhiguo.mushroom.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
            val count = runBlocking { database.speciesDao().count() }
            check(count > 0) { "Packaged database contains no species" }
        } finally {
            database.close()
            context.deleteDatabase(TEST_DB_NAME)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "packaged-mushroom-test.db"
    }
}
