package com.yangzhiguo.mushroom.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import com.yangzhiguo.mushroom.data.local.AppDatabase
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.data.local.SpeciesImageDao
import com.yangzhiguo.mushroom.data.repository.SpeciesRepository
import com.yangzhiguo.mushroom.data.repository.SpeciesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        deleteEmptyInstalledDatabase(context)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .createFromAsset("mushroom.db")  // 首次安装把 assets/mushroom.db 拷到 /data/data/.../databases/
            .addMigrations(AppDatabase.MIGRATION_10_11)  // v10→v11:新增 alias_names 列,保留用户数据
            .fallbackToDestructiveMigration()  // dev 阶段:无注册的旧版本直接重建
            .build()
    }

    /**
     * 覆盖安装不会重新复制 createFromAsset 数据。仅修复历史版本留下的空数据库；
     * 非空数据库保持原样，避免覆盖用户收藏和已同步数据。
     */
    internal fun deleteEmptyInstalledDatabase(context: Context): Boolean {
        val file = context.getDatabasePath(AppDatabase.DB_NAME)
        if (!file.isFile) return false

        val isEmpty = runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                database.rawQuery(
                    "SELECT COUNT(*) FROM mushroom_species",
                    null,
                ).use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) == 0L
                }
            }
        }.getOrElse {
            // 旧 schema 或损坏库交给 Room migration / fallback 处理。
            Log.w("DatabaseModule", "Unable to inspect installed database", it)
            false
        }

        if (!isEmpty) return false
        Log.w("DatabaseModule", "Deleting empty installed database to restore packaged data")
        return context.deleteDatabase(AppDatabase.DB_NAME)
    }

    @Provides
    fun provideSpeciesDao(db: AppDatabase): SpeciesDao = db.speciesDao()

    @Provides
    fun provideSpeciesImageDao(db: AppDatabase): SpeciesImageDao = db.speciesImageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSpeciesRepository(impl: SpeciesRepositoryImpl): SpeciesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ScraperModule {
    @Provides @Singleton
    fun provideApiClient(): com.yangzhiguo.mushroom.scraper.ApiClient = com.yangzhiguo.mushroom.scraper.ApiClient()
}
