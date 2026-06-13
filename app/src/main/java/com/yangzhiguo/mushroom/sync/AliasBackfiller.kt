package com.yangzhiguo.mushroom.sync

import android.content.Context
import android.util.Log
import com.yangzhiguo.mushroom.data.local.AppDatabase
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
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
 * CREATE TABLE mushroom_alias (
 *   id             INTEGER PRIMARY KEY AUTOINCREMENT,
 *   commonName     TEXT,
 *   scientificName TEXT,
 *   aliases        TEXT   -- JSON 数组字符串,e.g. ["烟云杯伞","clouded funnel"]
 * );
 * ```
 *
 * 匹配策略:
 *   `mushroom_species.scientific_name = alias.scientificName`
 *   OR `mushroom_species.chinese_name = alias.commonName`
 *
 * 两边名称都必须非空，且学名按 NOCASE 匹配；若多个源记录命中，优先选择学名和
 * 中文名都匹配的记录，其次选择学名匹配，最后选择中文名匹配。
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
            db.query("SELECT COUNT(*) FROM $attachAlias.$ALIAS_TABLE").use { cursor ->
                totalRows = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

            // 关键 UPDATE:只回填仍是空(`[]` 或空串/NULL)的行,且别名非空。
            // 用相关子查询 + EXISTS,避免误清空别人手工填过的 alias_names。
            db.execSQL(
                """
                UPDATE mushroom_species
                SET alias_names = COALESCE(
                    (
                        SELECT a.aliases
                        FROM $attachAlias.$ALIAS_TABLE AS a
                        WHERE trim(mushroom_species.scientific_name) != ''
                          AND trim(mushroom_species.chinese_name) != ''
                          AND trim(a.scientificName) != ''
                          AND trim(a.commonName) != ''
                          AND a.scientificName = mushroom_species.scientific_name COLLATE NOCASE
                          AND a.commonName = mushroom_species.chinese_name
                          AND a.aliases IS NOT NULL
                          AND a.aliases != ''
                          AND a.aliases != '[]'
                        LIMIT 1
                    ),
                    (
                        SELECT a.aliases
                        FROM $attachAlias.$ALIAS_TABLE AS a
                        WHERE trim(mushroom_species.scientific_name) != ''
                          AND trim(a.scientificName) != ''
                          AND a.scientificName = mushroom_species.scientific_name COLLATE NOCASE
                          AND a.aliases IS NOT NULL
                          AND a.aliases != ''
                          AND a.aliases != '[]'
                        LIMIT 1
                    ),
                    (
                        SELECT a.aliases
                        FROM $attachAlias.$ALIAS_TABLE AS a
                        WHERE trim(mushroom_species.chinese_name) != ''
                          AND trim(a.commonName) != ''
                          AND a.commonName = mushroom_species.chinese_name
                          AND a.aliases IS NOT NULL
                          AND a.aliases != ''
                          AND a.aliases != '[]'
                        LIMIT 1
                    )
                )
                WHERE EXISTS (
                    SELECT 1
                    FROM $attachAlias.$ALIAS_TABLE AS a
                    WHERE (
                            (
                                trim(mushroom_species.scientific_name) != ''
                                AND trim(a.scientificName) != ''
                                AND a.scientificName = mushroom_species.scientific_name COLLATE NOCASE
                            )
                            OR (
                                trim(mushroom_species.chinese_name) != ''
                                AND trim(a.commonName) != ''
                                AND a.commonName = mushroom_species.chinese_name
                            )
                      )
                      AND a.aliases IS NOT NULL
                      AND a.aliases != ''
                      AND a.aliases != '[]'
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
        const val ALIAS_TABLE = "mushroom_alias"
        /** 内部存储文件名;与 asset 同名以便排查。 */
        const val ALIAS_INTERNAL_FILENAME = "alias_mushroom.db"
    }

    // ════════════════════════════════════════════════════════════════════
    //  簇内别名合并(与 `scripts/backfill_plan.sql` 同语义)
    // ════════════════════════════════════════════════════════════════════

    /**
     * 簇内别名合并结果。
     *
     * @param clusters 总簇数(连通分量,含孤立点)
     * @param multiClusters 大小 ≥2 的簇数
     * @param mergedRecords 被写入新别名 JSON 的记录数(供体也写入以保证幂等)
     */
    data class ClusterResult(
        val totalRecords: Int,
        val clusters: Int,
        val multiClusters: Int,
        val mergedRecords: Int,
    )

    /**
     * 在 [SyncRepository.runFullSync] 末尾、`backfill()` 之后再调一次。
     *
     * 逻辑(与 `scripts/cluster_details.py` 完全一致):
     *  1. 读全部 SpeciesEntity,做名称规范化:TRIM + 拉丁 LOWERCASE。
     *  2. 全局 token 索引:每个 token → 出现该 token 的 entityId 集合。
     *  3. Union-Find:同 token 跨 ≥2 条记录则 union。
     *  4. 对每个 ≥2 簇:把所有成员的 alias_names 取并集去重,写回所有成员(幂等)。
     *  5. 单条记录没有同 token 时不动。
     *
     * 复用 Room DAO 写入,不直连 SQLite;也不需要外部 asset。
     */
    suspend fun backfillClusters(): ClusterResult = withContext(Dispatchers.IO) {
        val dao = roomDb.speciesDao()
        val all = dao.getAllForSync()
        if (all.isEmpty()) {
            return@withContext ClusterResult(0, 0, 0, 0)
        }

        // 规范化 & 解析 alias
        val rows = all.map { e ->
            val sci = (e.scientificName ?: "").trim().lowercase()
            val chn = (e.chineseName ?: "").trim()
            // aliasNames 已经是 List<String>(Room Converters 负责序列化),无需 JSON 中转。
            Normalized(e.aliasNames, sci, chn, e.aliasNames)
        }

        val n = rows.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        // token → id 集合
        val token2ids = HashMap<String, HashSet<Int>>()
        for ((idx, r) in rows.withIndex()) {
            if (r.sciNorm.isNotEmpty()) token2ids.getOrPut(r.sciNorm) { HashSet() }.add(idx)
            if (r.chnNorm.isNotEmpty()) token2ids.getOrPut(r.chnNorm) { HashSet() }.add(idx)
            for (a in r.aliases) token2ids.getOrPut(a) { HashSet() }.add(idx)
        }
        for ((_, ids) in token2ids) {
            if (ids.size < 2) continue
            val head = ids.iterator().next()
            for (o in ids) if (o != head) union(head, o)
        }

        // 按 root 分组
        val groups = HashMap<Int, MutableList<Int>>()
        for (idx in 0 until n) groups.getOrPut(find(idx)) { ArrayList() }.add(idx)

        val multiGroups = groups.values.filter { it.size >= 2 }

        // 计算每个簇的合并别名,收集需写入的实体
        val updates = ArrayList<SpeciesEntity>(n)
        for (memberIdxs in multiGroups) {
            val merged = LinkedHashSet<String>()
            for (i in memberIdxs) for (a in rows[i].aliases) merged.add(a)
            if (merged.isEmpty()) continue
            val newAliases = merged.toList()
            for (i in memberIdxs) {
                val r = rows[i]
                if (r.rawAlias != newAliases) {
                    updates += all[i].copy(aliasNames = newAliases)
                }
            }
        }

        if (updates.isNotEmpty()) {
            dao.upsertAll(updates)
        }

        val result = ClusterResult(
            totalRecords = n,
            clusters = groups.size,
            multiClusters = multiGroups.size,
            mergedRecords = updates.size,
        )
        Log.i(
            tag,
            "Cluster backfill done: total=${result.totalRecords}, " +
                "clusters=${result.clusters}, multi=${result.multiClusters}, " +
                "merged=${result.mergedRecords}",
        )
        result
    }

    /** 内部规范化结果,避免每轮重新解析。 */
    private data class Normalized(
        val rawAlias: List<String>,
        val sciNorm: String,
        val chnNorm: String,
        val aliases: List<String>,
    )
}
