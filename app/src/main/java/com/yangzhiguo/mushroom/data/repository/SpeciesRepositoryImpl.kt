package com.yangzhiguo.mushroom.data.repository

import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.UseType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeciesRepositoryImpl @Inject constructor(
    private val dao: SpeciesDao,
) : SpeciesRepository {
    override fun observeAll(): Flow<List<SpeciesEntity>> = dao.observeAll()
    override fun filterByUseType(useType: UseType): Flow<List<SpeciesEntity>> = when (useType) {
        UseType.EDIBLE -> dao.filterEdible()
        UseType.MEDICINAL -> dao.filterMedicinal()
        UseType.POISONOUS -> dao.filterPoisonous()
        UseType.CAUTION -> dao.filterCaution()
        UseType.UNREPORTED -> dao.observeAll()
    }
    override fun search(query: String): Flow<List<SpeciesEntity>> = dao.searchByName(query.trim())
    override fun observeFavorites(): Flow<List<SpeciesEntity>> = dao.observeFavorites()
    override suspend fun findById(id: Int): SpeciesEntity? = dao.findById(id)
    override suspend fun findByIds(ids: List<Int>): List<SpeciesEntity> = dao.findByIds(ids)
    override suspend fun toggleFavorite(id: Int) {
        val current = dao.findById(id) ?: return
        dao.setFavorite(id, !current.isFavorite)
    }
}
