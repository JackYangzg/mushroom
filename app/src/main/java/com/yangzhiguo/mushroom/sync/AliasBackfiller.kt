package com.yangzhiguo.mushroom.sync

import android.content.Context
import android.util.Log
import com.yangzhiguo.mushroom.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用 `assets/alias_mushroom.db` 中的别名表回填 `mushroom_species.alias_names`。
 *
 * 别名 DB 表结构:
 * ```
 * CREATE TABLE mushroom (
 *   id             INTEGER PRIMARY KEY AUTOINCREMENT,
 *   commonName     TEXT,
 *   scientificName TEXT,
 *   aliases        TEXT   -- JSON 数组字符串,e.g. ["烟云杯伞","clouded funnel"]
 * );
 * ```
 *
 * 匹配策略(strict AND):
 *   `mushroom_species.scientific_name = alias.scientificName`
 *   AND `mushroom_species.chinese_name = alias.commonName`
 *
 * `aliases` 列已经是 Room `Converters.stringListToString` 期望的 JSON 数组格式,
 * 所以直接当作 `alias_names` 写入即可,无需任何转码。
 *
 * 调用时机:
 *   - 运行时 [SyncRepository.runFullSync] 四个 source 全部抓完后,在 Success 之前;
 *   - 本地离线数据库生成同样调用 [SyncRepository.runFullSync],不再维护独立 JDBC 路径。
 */
@Singleton
class AliasBackfiller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roomDb: AppDatabase,
) {
    private val tag = "AliasBackfiller"

    /**
     * 回填执行结果。
     *
     * @param updated 实际被更新的 `mushroom_species` 行数(只统计 alias_names 由空变为非空)
     * @param totalAliasRows 别名 DB 中的总行数(用于日志)
     * @param skipped 若别名 DB 不存在/不可读,则 true 表示跳过,updated=0
     */
    data class Result(
        val updated: Int,
        val totalAliasRows: Int,
        val skipped: Boolean,
    )

    /**
     * 执行回填。**不要**在 Room `withTransaction { }` 内调用——ATTACH DATABASE 在事务中会失败。
     */
    suspend fun backfill(): Result = withContext(Dispatchers.IO) {
        val aliasFile = ensureAliasDbAvailable() ?: run {
            Log.w(tag, "alias_mushroom.db not present in assets; alias backfill skipped")
            return@withContext Result(updated = 0, totalAliasRows = 0, skipped = true)
        }

        val db = roomDb.openHelper.writableDatabase
        // ATTACH 路径必须用单引号字符串字面量;Android 的绝对路径里不会含单引号,
        // 但用 replace 处理一次防御一下别名目录被改的极端情况。
        val safePath = aliasFile.absolutePath.replace("'", "''")
        val attachAlias = "alias_src"

        val totalRows: Int
        val updatedRows: Int
        try {
            db.execSQL("ATTACH DATABASE '$safePath' AS $attachAlias")

            // 统计源表行数(只为日志):
            db.query("SELECT COUNT(*) FROM $attachAlias.mushroom").use { cursor ->
                totalRows = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

            // 关键 UPDATE:只回填仍是空(`[]` 或空串/NULL)的行,且别名非空。
            // 用相关子查询 + EXISTS,避免误清空别人手工填过的 alias_names。
            db.execSQL(
                """
                UPDATE mushroom_species
                SET alias_names = (
                    SELECT a.aliases
                    FROM $attachAlias.mushroom AS a
                    WHERE a.scientificName = mushroom_species.scientific_name
                      AND a.commonName    = mushroom_species.chinese_name
                      AND a.aliases IS NOT NULL
                      AND a.aliases != ''
                    LIMIT 1
                )
                WHERE EXISTS (
                    SELECT 1
                    FROM $attachAlias.mushroom AS a
                    WHERE a.scientificName = mushroom_species.scientific_name
                      AND a.commonName    = mushroom_species.chinese_name
                      AND a.aliases IS NOT NULL
                      AND a.aliases != ''
                )
                  AND (alias_names IS NULL OR alias_names = '' OR alias_names = '[]')
                """.trimIndent()
            )

            // 取 changes() 必须在同一连接、同一事务序列里;Room 的 openHelper 在 IO 协程
            // 内独占,所以这里安全。
            db.query("SELECT changes()").use { cursor ->
                updatedRows = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            runCatching { db.execSQL("DETACH DATABASE $attachAlias") }
                .onFailure { Log.w(tag, "DETACH alias DB failed", it) }
        }

        Log.i(tag, "Alias backfill done: updated=$updatedRows / aliasRows=$totalRows")
        Result(updated = updatedRows, totalAliasRows = totalRows, skipped = false)
    }

    /**
     * 把 `assets/alias_mushroom.db` 拷到 app 内部存储一次(幂等);
     * 拷贝过程对 alias DB 内容做内容感知:若大小变化(asset 升级)则覆盖,否则跳过。
     *
     * 返回内部存储中的别名 DB 文件;若 asset 不存在则返回 null。
     */
    private fun ensureAliasDbAvailable(): File? {
        val dest = File(context.filesDir, ALIAS_INTERNAL_FILENAME)
        val assetSize = runCatching {
            context.assets.openFd(ALIAS_ASSET_FILENAME).use { it.length }
        }.getOrNull()
            ?: runCatching {
                // 部分 asset 没法走 openFd(被 aapt 压缩);走流式拷贝时仍能成功。
                context.assets.open(ALIAS_ASSET_FILENAME).use { it.available().toLong() }
            }.getOrNull()
            ?: return null

        if (dest.exists() && dest.length() == assetSize) return dest

        runCatching {
            context.assets.open(ALIAS_ASSET_FILENAME).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            Log.w(tag, "Failed to copy $ALIAS_ASSET_FILENAME to internal storage", it)
            return null
        }
        return dest
    }

    private companion object {
        const val ALIAS_ASSET_FILENAME = "alias_mushroom.db"
        /** 内部存储文件名;与 asset 同名以便排查。 */
        const val ALIAS_INTERNAL_FILENAME = "alias_mushroom.db"
    }
}
