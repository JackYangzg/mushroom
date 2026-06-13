package com.yangzhiguo.mushroom.data.repository

import com.yangzhiguo.mushroom.data.local.SpeciesEntity
import com.yangzhiguo.mushroom.data.local.SpeciesImageEntity
import com.yangzhiguo.mushroom.domain.model.UseType
import kotlinx.coroutines.flow.Flow

interface SpeciesRepository {
    fun observeAll(): Flow<List<SpeciesEntity>>
    fun filterByUseType(useType: UseType): Flow<List<SpeciesEntity>>
    fun search(query: String): Flow<List<SpeciesEntity>>
    fun observeFavorites(): Flow<List<SpeciesEntity>>

    /** 业务 id 查找(对应 iflora API 物种 id)。 */
    suspend fun findByMushroomId(mushroomId: Int): SpeciesEntity?

    /** 业务 id 批量查找。 */
    suspend fun findByMushroomIds(mushroomIds: List<Int>): List<SpeciesEntity>

    /** 按业务 id 刷新详情(从远端拉最新一次,合并图片/字段)。返回合并后的 entity。 */
    suspend fun refreshDetails(mushroomId: Int): SpeciesEntity?

    /** 按业务 id 取该蘑菇的所有图片。 */
    suspend fun findImages(mushroomId: Int): List<SpeciesImageEntity>

    suspend fun toggleFavorite(mushroomId: Int)
}