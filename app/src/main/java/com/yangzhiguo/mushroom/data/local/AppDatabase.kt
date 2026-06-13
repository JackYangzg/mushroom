package com.yangzhiguo.mushroom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Schema v12 — 图片并入 `mushroom_species`,主键改为
 * `(mushroom_id, scraw_source)`。
 *
 * v10 → v11 主要变化:
 * - `mushroom_species.alias_names`:JSON 数组字符串,存该物种的别名 / 俗名 list;
 *   DAO 的名称匹配(`searchByName` / `findBestNameMatch`)在 `scientific_name` /
 *   `chinese_name` miss 时回退到本字段。
 *
 * Migration policy:不兼容旧结构。旧数据库直接 destructive rebuild，随包数据库
 * 始终由当前同步逻辑重新爬取生成。
 */
@Database(
    entities = [
        SpeciesEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speciesDao(): SpeciesDao

    companion object {
        const val DB_NAME = "mushroom.db"
    }
}
