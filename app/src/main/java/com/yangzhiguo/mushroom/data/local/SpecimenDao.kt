package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecimenDao {
    @Query("SELECT * FROM mushroom_specimen WHERE species_id = :speciesId ORDER BY collect_time DESC, id ASC")
    fun observeForSpecies(speciesId: Int): Flow<List<SpecimenEntity>>

    @Query("SELECT * FROM mushroom_specimen WHERE species_id = :speciesId ORDER BY collect_time DESC, id ASC")
    suspend fun findBySpecies(speciesId: Int): List<SpecimenEntity>

    @Query("SELECT * FROM mushroom_specimen WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SpecimenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SpecimenEntity>)

    @Query("DELETE FROM mushroom_specimen WHERE species_id = :speciesId")
    suspend fun deleteForSpecies(speciesId: Int)
}
