package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DistributionPointDao {
    @Query("SELECT * FROM mushroom_distribution_point WHERE species_id = :speciesId")
    fun observeForSpecies(speciesId: Int): Flow<List<DistributionPointEntity>>

    @Query("SELECT * FROM mushroom_distribution_point WHERE species_id = :speciesId")
    suspend fun findForSpecies(speciesId: Int): List<DistributionPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DistributionPointEntity>)

    @Query("DELETE FROM mushroom_distribution_point WHERE species_id = :speciesId")
    suspend fun deleteForSpecies(speciesId: Int)
}
