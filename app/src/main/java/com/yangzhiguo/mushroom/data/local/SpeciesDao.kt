package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 在窄字段 CTE 中一次性计算图鉴权威记录，避免列表每一行都对整张宽表执行相关子查询。
 *
 * 不使用 ROW_NUMBER 等窗口函数，以兼容 minSdk 24 设备自带的旧版 SQLite。
 */
private const val CATALOG_WINNERS_CTE = """
    WITH curated AS (
        SELECT
            mushroom_id,
            scraw_source,
            lower(trim(scientific_name)) AS normalized_name,
            CASE scraw_source
                WHEN 'general_directory' THEN 0
                WHEN 'edible_fungi' THEN 1
                ELSE 2
            END AS source_rank
        FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
    ),
    id_winners AS (
        SELECT mushroom_id, min(source_rank) AS source_rank
        FROM curated
        GROUP BY mushroom_id
    ),
    name_priorities AS (
        SELECT normalized_name, min(source_rank) AS source_rank
        FROM curated
        WHERE normalized_name != ''
        GROUP BY normalized_name
    ),
    name_winners AS (
        SELECT
            candidate.normalized_name,
            candidate.source_rank,
            min(candidate.mushroom_id) AS mushroom_id
        FROM curated AS candidate
        INNER JOIN name_priorities AS priority
            ON priority.normalized_name = candidate.normalized_name
           AND priority.source_rank = candidate.source_rank
        GROUP BY candidate.normalized_name, candidate.source_rank
    ),
    winners AS (
        SELECT candidate.mushroom_id, candidate.scraw_source
        FROM curated AS candidate
        INNER JOIN id_winners AS id_winner
            ON id_winner.mushroom_id = candidate.mushroom_id
           AND id_winner.source_rank = candidate.source_rank
        LEFT JOIN name_winners AS name_winner
            ON name_winner.normalized_name = candidate.normalized_name
        WHERE candidate.normalized_name = ''
           OR (
                name_winner.source_rank = candidate.source_rank
                AND name_winner.mushroom_id = candidate.mushroom_id
           )
    )
"""

@Dao
interface SpeciesDao {
    /**
     * 图鉴默认展示。仅 curated 三 source(`general_directory` / `edible_fungi` /
     * `toxic_fungi`)。同 mushroom_id 或同规范化学名多条时按 source 优先级 +
     * id 取一条权威记录。
     *
     * 注意:该查询**不再**混入 `species_specimen`。标本记录仍在表中供 LLM 识别
     * 匹配 / 详情页使用,但图鉴默认不展示(specimen 通常没有业务 mushroom_id,
     * 展示意义有限且显著拖慢列表)。
     */
    @Query(CATALOG_WINNERS_CTE + """
        SELECT species.*
        FROM mushroom_species AS species
        INNER JOIN winners
            ON winners.mushroom_id = species.mushroom_id
           AND winners.scraw_source = species.scraw_source
        ORDER BY species.mushroom_id ASC
    """)
    fun observeAll(): Flow<List<SpeciesEntity>>

    @Query(CATALOG_WINNERS_CTE + """
        SELECT species.*
        FROM mushroom_species AS species
        INNER JOIN winners
            ON winners.mushroom_id = species.mushroom_id
           AND winners.scraw_source = species.scraw_source
        WHERE instr(',' || species.source_types || ',', ',EDIBLE,') > 0
        ORDER BY species.mushroom_id ASC
    """)
    fun filterEdible(): Flow<List<SpeciesEntity>>

    @Query(CATALOG_WINNERS_CTE + """
        SELECT species.*
        FROM mushroom_species AS species
        INNER JOIN winners
            ON winners.mushroom_id = species.mushroom_id
           AND winners.scraw_source = species.scraw_source
        WHERE instr(',' || species.source_types || ',', ',MEDICINAL,') > 0
        ORDER BY species.mushroom_id ASC
    """)
    fun filterMedicinal(): Flow<List<SpeciesEntity>>

    @Query(CATALOG_WINNERS_CTE + """
        SELECT species.*
        FROM mushroom_species AS species
        INNER JOIN winners
            ON winners.mushroom_id = species.mushroom_id
           AND winners.scraw_source = species.scraw_source
        WHERE instr(',' || species.source_types || ',', ',POISONOUS,') > 0
        ORDER BY species.toxicity_level DESC, species.mushroom_id ASC
    """)
    fun filterPoisonous(): Flow<List<SpeciesEntity>>

    @Query(CATALOG_WINNERS_CTE + """
        SELECT species.*
        FROM mushroom_species AS species
        INNER JOIN winners
            ON winners.mushroom_id = species.mushroom_id
           AND winners.scraw_source = species.scraw_source
        WHERE instr(',' || species.source_types || ',', ',CAUTION,') > 0
        ORDER BY species.mushroom_id ASC
    """)
    fun filterCaution(): Flow<List<SpeciesEntity>>

    /**
     * 搜索:查询整个数据库,不按 scraw_source 白名单过滤。
     * 去重逻辑保留:权威来源仍是 `general_directory / edible_fungi / toxic_fungi`,
     * 其他 source 行(包括 `species_specimen` 和未来新 source)仅在无权威来源
     * 对应行时显示。未来接入新 source 后会自动纳入搜索结果,无需修改此 SQL。
     */
    @Query("""
        SELECT * FROM mushroom_species AS species
        WHERE (
            species.chinese_name LIKE '%' || :query || '%' COLLATE NOCASE
            OR species.scientific_name LIKE '%' || :query || '%' COLLATE NOCASE
            OR species.alias_names LIKE '%' || :query || '%' COLLATE NOCASE
        )
          AND NOT EXISTS (
                SELECT 1 FROM mushroom_species AS preferred
                WHERE preferred.mushroom_id = species.mushroom_id
                  AND preferred.scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
                  AND CASE preferred.scraw_source
                        WHEN 'general_directory' THEN 0
                        WHEN 'edible_fungi' THEN 1
                        ELSE 2
                      END
                      < CASE species.scraw_source
                          WHEN 'general_directory' THEN 0
                          WHEN 'edible_fungi' THEN 1
                          ELSE 2
                        END
          )
        ORDER BY
            CASE
                WHEN species.chinese_name = :query COLLATE NOCASE THEN 0
                WHEN species.scientific_name = :query COLLATE NOCASE THEN 1
                WHEN species.chinese_name LIKE :query || '%' COLLATE NOCASE THEN 2
                WHEN species.scientific_name LIKE :query || '%' COLLATE NOCASE THEN 3
                ELSE 4
            END,
            species.mushroom_id ASC
    """)
    fun searchByName(query: String): Flow<List<SpeciesEntity>>

    /** 业务 id 查找 — UI / repository 实际调用的入口。 */
    @Query("""
        SELECT * FROM mushroom_species
        WHERE mushroom_id = :mushroomId
        ORDER BY CASE scraw_source
            WHEN 'general_directory' THEN 0
            WHEN 'edible_fungi' THEN 1
            WHEN 'toxic_fungi' THEN 2
            ELSE 3
        END
        LIMIT 1
    """)
    suspend fun findByMushroomId(mushroomId: Int): SpeciesEntity?

    @Query("""
        SELECT * FROM mushroom_species
        WHERE mushroom_id = :mushroomId AND scraw_source = :scrawSource
        LIMIT 1
    """)
    suspend fun findByIdentity(mushroomId: Int, scrawSource: String): SpeciesEntity?

    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
          AND scientific_name = :name COLLATE NOCASE
        LIMIT 1
    """)
    suspend fun findByScientificName(name: String): SpeciesEntity?

    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
          AND (
            (
                :scientificName != '' AND (
                    scientific_name LIKE '%' || :scientificName || '%' COLLATE NOCASE
                    OR :scientificName LIKE '%' || scientific_name || '%' COLLATE NOCASE
                )
            ) OR (
                :commonName != '' AND (
                    chinese_name LIKE '%' || :commonName || '%' COLLATE NOCASE
                    OR :commonName LIKE '%' || chinese_name || '%' COLLATE NOCASE
                )
            ) OR (
                :aliasName != '' AND alias_names LIKE '%' || :aliasName || '%' COLLATE NOCASE
            )
          )
        ORDER BY
            CASE
                WHEN scientific_name = :scientificName COLLATE NOCASE THEN 0
                WHEN chinese_name = :commonName COLLATE NOCASE THEN 1
                WHEN alias_names LIKE '%"' || :aliasName || '"%' COLLATE NOCASE THEN 2
                WHEN scientific_name LIKE :scientificName || '%' COLLATE NOCASE THEN 3
                WHEN chinese_name LIKE :commonName || '%' COLLATE NOCASE THEN 4
                ELSE 5
            END,
            abs(length(scientific_name) - length(:scientificName))
                + abs(length(chinese_name) - length(:commonName)),
            mushroom_id ASC
        LIMIT 1
    """)
    suspend fun findBestNameMatch(
        scientificName: String,
        commonName: String,
        aliasName: String = "",
    ): SpeciesEntity?

    @Query("SELECT * FROM mushroom_species WHERE mushroom_id IN (:mushroomIds)")
    suspend fun findByMushroomIds(mushroomIds: List<Int>): List<SpeciesEntity>

    @Query("SELECT COUNT(*) FROM mushroom_species")
    suspend fun count(): Int

    /** 同步回填专用:读全表(单次小查询,5922 行 ≈ 数 MB)。 */
    @Query("SELECT * FROM mushroom_species")
    suspend fun getAllForSync(): List<SpeciesEntity>

    /**
     * Upsert by `(mushroom_id, scraw_source)` 复合主键。
     */
    @Upsert
    suspend fun upsertAll(species: List<SpeciesEntity>)

    @Query("DELETE FROM mushroom_species")
    suspend fun deleteAll()

    @Query("""
        UPDATE mushroom_species SET image_local_path = :path, last_updated = :ts
        WHERE mushroom_id = :mushroomId AND scraw_source = :scrawSource
    """)
    suspend fun updateImagePath(mushroomId: Int, scrawSource: String, path: String?, ts: Long = System.currentTimeMillis())

    @Query("""
        UPDATE mushroom_species SET image_url = :url, last_updated = :ts
        WHERE mushroom_id = :mushroomId AND scraw_source = :scrawSource
    """)
    suspend fun updateImageUrl(mushroomId: Int, scrawSource: String, url: String?, ts: Long = System.currentTimeMillis())

}
