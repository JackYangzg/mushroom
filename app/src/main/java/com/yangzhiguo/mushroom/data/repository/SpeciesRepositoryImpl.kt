package com.yangzhiguo.mushroom.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.yangzhiguo.mushroom.data.local.AppDatabase
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.local.SpeciesImageDao
import com.yangzhiguo.mushroom.data.local.SpeciesImageEntity
import com.yangzhiguo.mushroom.domain.model.UseType
import com.yangzhiguo.mushroom.scraper.ApiClient
import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.ScrapedRecord
import com.yangzhiguo.mushroom.sync.ScraperToRoomMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeciesRepositoryImpl @Inject constructor(
    private val dao: SpeciesDao,
    private val speciesImageDao: SpeciesImageDao,
    private val database: AppDatabase,
    private val apiClient: ApiClient,
    @ApplicationContext private val context: Context,
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
    override suspend fun findByMushroomId(mushroomId: Int): SpeciesEntity? = dao.findByMushroomId(mushroomId)
    override suspend fun findByMushroomIds(mushroomIds: List<Int>): List<SpeciesEntity> = dao.findByMushroomIds(mushroomIds)

    /**
     * 按业务 id [mushroomId] 从 iflora 拉一次详情,合并到本地。
     * 只更新主表 + 图片表;子表已删。
     */
    override suspend fun refreshDetails(mushroomId: Int): SpeciesEntity? {
        val current = dao.findByMushroomId(mushroomId) ?: return null
        val detail = apiClient.fetchSpeciesDetail(mushroomId) ?: return current
        if (!SpeciesIdentity.matches(current, detail)) {
            return current
        }
        val record = ScrapedRecord(
            specimen = detail,
            source = DataSource.GENERAL_DIRECTORY,
            sourceUrl = current.sourceUrl,
        )
        val batch = ScraperToRoomMapper.toBatch(record = record)
        val refreshed = batch.species.single().copy(
            mushroomId = current.mushroomId,
            isFavorite = current.isFavorite,
            model3dUrl = current.model3dUrl,
            identificationPoints = current.identificationPoints,
            lookAlikeIds = current.lookAlikeIds,
            toxicitySymptoms = current.toxicitySymptoms,
            season = current.season,
            sourceTypes = (current.sourceTypes.split(",") + batch.species.single().sourceTypes.split(","))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(","),
        )
        database.withTransaction {
            dao.upsertAll(listOf(refreshed))
            speciesImageDao.deleteForSpecies(mushroomId)
            if (batch.images.isNotEmpty()) speciesImageDao.upsertAll(batch.images)
        }
        return refreshed
    }

    override suspend fun findImages(mushroomId: Int): List<SpeciesImageEntity> =
        speciesImageDao.findForSpecies(mushroomId)

    override suspend fun toggleFavorite(mushroomId: Int) {
        val current = dao.findByMushroomId(mushroomId) ?: return
        dao.setFavorite(mushroomId, !current.isFavorite)
    }
}