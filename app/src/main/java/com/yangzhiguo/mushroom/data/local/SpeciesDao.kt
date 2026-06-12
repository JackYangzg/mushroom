package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesDao {
    @Query("SELECT * FROM mushroom_species ORDER BY id ASC")
    fun observeAll(): Flow<List<SpeciesEntity>>

    @Query("SELECT * FROM mushroom_species WHERE instr(',' || source_types || ',', ',EDIBLE,') > 0 ORDER BY id ASC")
    fun filterEdible(): Flow<List<SpeciesEntity>>

    @Query("SELECT * FROM mushroom_species WHERE use_type = 'MEDICINAL' ORDER BY id ASC")
    fun filterMedicinal(): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE instr(',' || source_types || ',', ',TOXIC,') > 0
        ORDER BY toxicity_level DESC, id ASC
    """)
    fun filterPoisonous(): Flow<List<SpeciesEntity>>

    @Query("SELECT * FROM mushroom_species WHERE use_type = 'CAUTION' ORDER BY id ASC")
    fun filterCaution(): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE chinese_name LIKE '%' || :query || '%' COLLATE NOCASE
           OR scientific_name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY
            CASE
                WHEN chinese_name = :query COLLATE NOCASE THEN 0
                WHEN scientific_name = :query COLLATE NOCASE THEN 1
                WHEN chinese_name LIKE :query || '%' COLLATE NOCASE THEN 2
                WHEN scientific_name LIKE :query || '%' COLLATE NOCASE THEN 3
                ELSE 4
            END,
            id ASC
    """)
    fun searchByName(query: String): Flow<List<SpeciesEntity>>

    @Query("SELECT * FROM mushroom_species WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): SpeciesEntity?

    @Query("SELECT * FROM mushroom_species WHERE scientific_name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByScientificName(name: String): SpeciesEntity?

    @Query("""
        SELECT * FROM mushroom_species
        WHERE (
            :scientificName != '' AND (
                scientific_name LIKE '%' || :scientificName || '%' COLLATE NOCASE
                OR :scientificName LIKE '%' || scientific_name || '%' COLLATE NOCASE
            )
        ) OR (
            :commonName != '' AND (
                chinese_name LIKE '%' || :commonName || '%' COLLATE NOCASE
                OR :commonName LIKE '%' || chinese_name || '%' COLLATE NOCASE
            )
        )
        ORDER BY
            CASE
                WHEN scientific_name = :scientificName COLLATE NOCASE THEN 0
                WHEN chinese_name = :commonName COLLATE NOCASE THEN 1
                WHEN scientific_name LIKE :scientificName || '%' COLLATE NOCASE THEN 2
                WHEN chinese_name LIKE :commonName || '%' COLLATE NOCASE THEN 3
                ELSE 4
            END,
            abs(length(scientific_name) - length(:scientificName))
                + abs(length(chinese_name) - length(:commonName)),
            id ASC
        LIMIT 1
    """)
    suspend fun findBestNameMatch(scientificName: String, commonName: String): SpeciesEntity?

    @Query("SELECT * FROM mushroom_species WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Int>): List<SpeciesEntity>

    @Query("SELECT COUNT(*) FROM mushroom_species")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(species: List<SpeciesEntity>)

    @Query("DELETE FROM mushroom_species")
    suspend fun deleteAll()

    @Query("UPDATE mushroom_species SET image_local_path = :path, last_updated = :ts WHERE id = :id")
    suspend fun updateImagePath(id: Int, path: String?, ts: Long = System.currentTimeMillis())

    @Query("UPDATE mushroom_species SET image_url = :url, last_updated = :ts WHERE id = :id")
    suspend fun updateImageUrl(id: Int, url: String?, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM mushroom_species WHERE is_favorite = 1 ORDER BY last_updated DESC, id ASC")
    fun observeFavorites(): Flow<List<SpeciesEntity>>

    @Query("UPDATE mushroom_species SET is_favorite = :isFavorite, last_updated = :ts WHERE id = :id")
    suspend fun setFavorite(id: Int, isFavorite: Boolean, ts: Long = System.currentTimeMillis())
}
