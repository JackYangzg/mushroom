package com.yangzhiguo.mushroom.data.repository

import com.yangzhiguo.mushroom.data.favorite.FavoriteStore
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.ApiClient
import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.ScrapedRecord
import com.yangzhiguo.mushroom.sync.ScraperToRoomMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeciesRepositoryImpl @Inject constructor(
    private val dao: SpeciesDao,
    private val apiClient: ApiClient,
    private val favoriteStore: FavoriteStore,
) : SpeciesRepository {
    override fun observeAll(): Flow<List<SpeciesEntity>> = dao.observeAll().withFavoriteState()
    override fun filterByUseType(useType: UseType): Flow<List<SpeciesEntity>> = when (useType) {
        UseType.EDIBLE -> dao.filterEdible()
        UseType.MEDICINAL -> dao.filterMedicinal()
        UseType.POISONOUS -> dao.filterPoisonous()
        UseType.CAUTION -> dao.filterCaution()
        UseType.UNREPORTED -> dao.observeAll()
    }.withFavoriteState()
    override fun search(query: String): Flow<List<SpeciesEntity>> =
        dao.searchByName(query.trim()).withFavoriteState()

    override fun observeFavorites(): Flow<List<SpeciesEntity>> =
        combine(dao.observeAll(), favoriteStore.ids) { species, favoriteIds ->
            species
                .filter { it.mushroomId in favoriteIds }
                .map { it.copy(isFavorite = true) }
        }

    override suspend fun findByMushroomId(mushroomId: Int): SpeciesEntity? =
        dao.findByMushroomId(mushroomId)?.withFavoriteState()

    override suspend fun findByMushroomIds(mushroomIds: List<Int>): List<SpeciesEntity> =
        dao.findByMushroomIds(mushroomIds).map { it.withFavoriteState() }

    /**
     * 按当前记录来源从 iflora 拉一次详情,合并到本地。
     * 目录记录使用业务 id；无业务 id 的标本记录从 source_url 提取标本 id。
     */
    override suspend fun refreshDetails(mushroomId: Int): SpeciesEntity? {
        val current = dao.findByMushroomId(mushroomId)?.withFavoriteState() ?: return null
        val isSpecimen = current.scrawSource == SpeciesEntity.SCRAW_SOURCE_SPECIES_SPECIMEN
        val detail = if (isSpecimen) {
            apiClient.fetchSpecimenDetail(current.sourceUrl)
        } else {
            apiClient.fetchSpeciesDetail(mushroomId)
        } ?: return current
        if (!SpeciesIdentity.matches(current, detail)) {
            return current
        }
        val record = ScrapedRecord(
            specimen = detail,
            source = if (isSpecimen) DataSource.SPECIMEN else DataSource.GENERAL_DIRECTORY,
            sourceUrl = current.sourceUrl,
        )
        val batch = ScraperToRoomMapper.toBatch(record = record)
        val refreshed = batch.species.single().copy(
            mushroomId = current.mushroomId,
            scrawSource = current.scrawSource,
            isFavorite = false,
            model3dUrl = current.model3dUrl,
            identificationPoints = current.identificationPoints,
            lookAlikeIds = current.lookAlikeIds,
            toxicitySymptoms = current.toxicitySymptoms,
            season = current.season,
            sourceUrl = current.sourceUrl,
            sourceTypes = (current.sourceTypes.split(",") + batch.species.single().sourceTypes.split(","))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(","),
        )
        dao.upsertAll(listOf(refreshed))
        return refreshed.withFavoriteState()
    }

    override suspend fun toggleFavorite(mushroomId: Int) {
        if (dao.findByMushroomId(mushroomId) == null) return
        favoriteStore.toggle(mushroomId)
    }

    private fun Flow<List<SpeciesEntity>>.withFavoriteState(): Flow<List<SpeciesEntity>> =
        combine(favoriteStore.ids) { species, favoriteIds ->
            species.map { it.copy(isFavorite = it.mushroomId in favoriteIds) }
        }

    private fun SpeciesEntity.withFavoriteState(): SpeciesEntity =
        copy(isFavorite = favoriteStore.isFavorite(mushroomId))
}
