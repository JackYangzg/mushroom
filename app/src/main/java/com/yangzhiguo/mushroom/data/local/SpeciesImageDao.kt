package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeciesImageDao {
    @Query("SELECT * FROM mushroom_image WHERE mushroom_id = :mushroomId ORDER BY sort_order ASC, uf_id ASC")
    fun observeForSpecies(mushroomId: Int): Flow<List<SpeciesImageEntity>>

    @Query("SELECT * FROM mushroom_image WHERE mushroom_id = :mushroomId ORDER BY sort_order ASC, uf_id ASC")
    suspend fun findForSpecies(mushroomId: Int): List<SpeciesImageEntity>

    @Query("SELECT * FROM mushroom_image WHERE mushroom_id = :mushroomId ORDER BY sort_order ASC LIMIT 1")
    suspend fun findPrimaryForSpecies(mushroomId: Int): SpeciesImageEntity?

    @Query("UPDATE mushroom_image SET local_path = :path WHERE mushroom_id = :mushroomId AND uf_id = :ufId")
    suspend fun updateLocalPath(mushroomId: Int, ufId: String, path: String?)

    @Upsert
    suspend fun upsertAll(items: List<SpeciesImageEntity>)

    @Query("DELETE FROM mushroom_image WHERE mushroom_id = :mushroomId")
    suspend fun deleteForSpecies(mushroomId: Int)

    @Query("DELETE FROM mushroom_image")
    suspend fun deleteAll()
}
