package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesImageDao {
    @Query("SELECT * FROM mushroom_image WHERE species_id = :speciesId ORDER BY sort_order ASC, uf_id ASC")
    fun observeForSpecies(speciesId: Int): Flow<List<SpeciesImageEntity>>

    @Query("SELECT * FROM mushroom_image WHERE species_id = :speciesId ORDER BY sort_order ASC, uf_id ASC")
    suspend fun findForSpecies(speciesId: Int): List<SpeciesImageEntity>

    @Query("SELECT * FROM mushroom_image WHERE species_id = :speciesId ORDER BY sort_order ASC LIMIT 1")
    suspend fun findPrimaryForSpecies(speciesId: Int): SpeciesImageEntity?

    @Query("UPDATE mushroom_image SET local_path = :path WHERE species_id = :speciesId AND uf_id = :ufId")
    suspend fun updateLocalPath(speciesId: Int, ufId: String, path: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SpeciesImageEntity>)

    @Query("DELETE FROM mushroom_image WHERE species_id = :speciesId")
    suspend fun deleteForSpecies(speciesId: Int)
}
