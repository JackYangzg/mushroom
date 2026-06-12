package com.yangzhiguo.mushroom.data.repository

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.UseType
import kotlinx.coroutines.flow.Flow

interface SpeciesRepository {
    fun observeAll(): Flow<List<SpeciesEntity>>
    fun filterByUseType(useType: UseType): Flow<List<SpeciesEntity>>
    fun search(query: String): Flow<List<SpeciesEntity>>
    fun observeFavorites(): Flow<List<SpeciesEntity>>
    suspend fun findById(id: Int): SpeciesEntity?
    suspend fun findByIds(ids: List<Int>): List<SpeciesEntity>
    suspend fun toggleFavorite(id: Int)
}
