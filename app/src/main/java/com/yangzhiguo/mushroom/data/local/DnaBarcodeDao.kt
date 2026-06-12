package com.yangzhiguo.mushroom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DnaBarcodeDao {
    /** 物种详情页 DNA 表:按 specimen 维度返回,每 specimen 最多 7 行(7 个基因位点) */
    @Query("""
        SELECT * FROM mushroom_dna_barcode
        WHERE specimen_id IN (:specimenIds)
        ORDER BY specimen_id ASC, gene ASC
    """)
    fun observeForSpecimens(specimenIds: List<Long>): Flow<List<DnaBarcodeEntity>>

    @Query("SELECT * FROM mushroom_dna_barcode WHERE specimen_id = :specimenId ORDER BY gene ASC")
    suspend fun findForSpecimen(specimenId: Long): List<DnaBarcodeEntity>

    /** 跨该 species 下所有 specimen 聚合的 DNA 记录,用于"全部 DNA"视图 */
    @Query("""
        SELECT * FROM mushroom_dna_barcode
        WHERE species_id = :speciesId
        ORDER BY specimen_id ASC, gene ASC
    """)
    suspend fun findForSpecies(speciesId: Int): List<DnaBarcodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DnaBarcodeEntity>)

    @Query("DELETE FROM mushroom_dna_barcode WHERE species_id = :speciesId")
    suspend fun deleteForSpecies(speciesId: Int)
}
