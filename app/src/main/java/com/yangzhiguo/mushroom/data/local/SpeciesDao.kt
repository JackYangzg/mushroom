package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yangzhiguo.mushroom.domain.model.UseType
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesDao {
    @Query("SELECT * FROM mushroom_species ORDER BY id ASC")
    fun observeAll(): Flow<List<SpeciesEntity>>

    @Query("SELECT * FROM mushroom_species WHERE use_type = :useType ORDER BY id ASC")
    fun filterByUseType(useType: UseType): Flow<List<SpeciesEntity>>

    @Query("""
        SELECT * FROM mushroom_species
        WHERE chinese_name LIKE '%' || :query || '%'
           OR scientific_name LIKE '%' || :query || '%'
        ORDER BY id ASC
    """)
    fun searchByName(query: String): Flow<List<SpeciesEntity>>

    @Query("SELECT * FROM mushroom_species WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): SpeciesEntity?

    @Query("SELECT * FROM mushroom_species WHERE scientific_name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByScientificName(name: String): SpeciesEntity?

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

    @Query("SELECT * FROM mushroom_species WHERE is_favorite = 1 ORDER BY last_updated DESC, id ASC")
    fun observeFavorites(): Flow<List<SpeciesEntity>>

    @Query("UPDATE mushroom_species SET is_favorite = :isFavorite, last_updated = :ts WHERE id = :id")
    suspend fun setFavorite(id: Int, isFavorite: Boolean, ts: Long = System.currentTimeMillis())
}
