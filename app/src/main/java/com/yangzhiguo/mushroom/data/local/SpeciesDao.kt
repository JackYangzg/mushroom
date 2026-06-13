package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesDao {
    /**
     * 图鉴展示三个物种名录来源。
     */
    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
        ORDER BY mushroom_id ASC
    """)
    fun observeAll(): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
          AND instr(',' || source_types || ',', ',EDIBLE,') > 0
        ORDER BY mushroom_id ASC
    """)
    fun filterEdible(): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
          AND instr(',' || source_types || ',', ',MEDICINAL,') > 0
        ORDER BY mushroom_id ASC
    """)
    fun filterMedicinal(): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
          AND instr(',' || source_types || ',', ',POISONOUS,') > 0
        ORDER BY toxicity_level DESC, mushroom_id ASC
    """)
    fun filterPoisonous(): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
          AND instr(',' || source_types || ',', ',CAUTION,') > 0
        ORDER BY mushroom_id ASC
    """)
    fun filterCaution(): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE scraw_source IN ('general_directory', 'edible_fungi', 'toxic_fungi')
          AND (
            chinese_name LIKE '%' || :query || '%' COLLATE NOCASE
            OR scientific_name LIKE '%' || :query || '%' COLLATE NOCASE
            OR alias_names LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY
            CASE
                WHEN chinese_name = :query COLLATE NOCASE THEN 0
                WHEN scientific_name = :query COLLATE NOCASE THEN 1
                WHEN chinese_name LIKE :query || '%' COLLATE NOCASE THEN 2
                WHEN scientific_name LIKE :query || '%' COLLATE NOCASE THEN 3
                ELSE 4
            END,
            mushroom_id ASC
    """)
    fun searchByName(query: String): Flow<List<SpeciesEntity>>

    /** 按 PK 查找 — 仅 debug 用,业务查找请用 [findByMushroomId]。 */
    @Query("SELECT * FROM mushroom_species WHERE id = :rowId LIMIT 1")
    suspend fun findByRowId(rowId: Long): SpeciesEntity?

    /** 业务 id 查找 — UI / repository 实际调用的入口。 */
    @Query("SELECT * FROM mushroom_species WHERE mushroom_id = :mushroomId LIMIT 1")
    suspend fun findByMushroomId(mushroomId: Int): SpeciesEntity?

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

    /**
     * Upsert by `(scraw_source, source_url)` 唯一索引。
     * PK `id` 由 SQLite 分配;已存在的 (source, url) 行会被原地覆盖更新。
     */
    @Upsert
    suspend fun upsertAll(species: List<SpeciesEntity>)

    @Query("DELETE FROM mushroom_species")
    suspend fun deleteAll()

    @Query("UPDATE mushroom_species SET image_local_path = :path, last_updated = :ts WHERE mushroom_id = :mushroomId")
    suspend fun updateImagePath(mushroomId: Int, path: String?, ts: Long = System.currentTimeMillis())

    @Query("UPDATE mushroom_species SET image_url = :url, last_updated = :ts WHERE mushroom_id = :mushroomId")
    suspend fun updateImageUrl(mushroomId: Int, url: String?, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM mushroom_species WHERE is_favorite = 1 ORDER BY last_updated DESC, mushroom_id ASC")
    fun observeFavorites(): Flow<List<SpeciesEntity>>

    @Query("UPDATE mushroom_species SET is_favorite = :isFavorite, last_updated = :ts WHERE mushroom_id = :mushroomId")
    suspend fun setFavorite(mushroomId: Int, isFavorite: Boolean, ts: Long = System.currentTimeMillis())
}