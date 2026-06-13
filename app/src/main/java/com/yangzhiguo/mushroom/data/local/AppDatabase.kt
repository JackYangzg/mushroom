package com.yangzhiguo.mushroom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema v11 — `mushroom_species` 新增 `alias_names`(TEXT,JSON 数组字符串)。
 *
 * v10 → v11 主要变化:
 * - `mushroom_species.alias_names`:JSON 数组字符串,存该物种的别名 / 俗名 list;
 *   DAO 的名称匹配(`searchByName` / `findBestNameMatch`)在 `scientific_name` /
 *   `chinese_name` miss 时回退到本字段。
 *
 * Migration policy: **dev 阶段跳过 compat**。Provider 配置 `fallbackToDestructiveMigration()`,
 * 旧 `mushroom.db` 直接 wipe。release 阶段需要保留 [MIGRATION_10_11]。
 */
@Database(
    entities = [
        SpeciesEntity::class,
        SpeciesImageEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speciesDao(): SpeciesDao
    abstract fun speciesImageDao(): SpeciesImageDao

    companion object {
        const val DB_NAME = "mushroom.db"

        /**
         * v10 → v11: 新增 `alias_names` 列(可空 JSON 数组字符串,默认 `[]`)。
         * 旧数据按空别名处理 — 不影响 `scientific_name` / `chinese_name` 已有匹配,
         * 匹配回退到 `alias_names` 也不会命中旧行的空字符串。
         */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE mushroom_species ADD COLUMN alias_names TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }
    }
}