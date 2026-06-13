package com.yangzhiguo.mushroom.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.yangzhiguo.mushroom.data.local.AppDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseModuleTest {

    @Test
    fun emptyInstalledDatabaseIsDeletedBeforeRoomOpens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DB_NAME)
        createDatabase(context, rowCount = 0)

        assertTrue(DatabaseModule.deleteEmptyInstalledDatabase(context))
        assertFalse(context.getDatabasePath(AppDatabase.DB_NAME).exists())
    }

    @Test
    fun nonEmptyInstalledDatabaseIsPreserved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DB_NAME)
        createDatabase(context, rowCount = 1)

        assertFalse(DatabaseModule.deleteEmptyInstalledDatabase(context))
        assertTrue(context.getDatabasePath(AppDatabase.DB_NAME).exists())
        context.deleteDatabase(AppDatabase.DB_NAME)
    }

    private fun createDatabase(context: Context, rowCount: Int) {
        val file = context.getDatabasePath(AppDatabase.DB_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL(
                "CREATE TABLE mushroom_species (id INTEGER PRIMARY KEY)",
            )
            repeat(rowCount) {
                database.execSQL("INSERT INTO mushroom_species DEFAULT VALUES")
            }
        }
    }
}
