package com.yangzhiguo.mushroom.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.yangzhiguo.mushroom.data.local.AppDatabase
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.scraper.ScrapedDatabase
import com.yangzhiguo.mushroom.scraper.Scraper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** 同步状态；UI 通过 [SyncRepository.state] 订阅。 */
sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val page: Int = 0, val totalSpecimens: Int = 0) : SyncState
    data class Success(val totalSpecimens: Int, val elapsedMs: Long) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * 同步协调器：抓取 → 写 scraped DB → 映射导入 Room → 更新 SharedPreferences 时间戳。
 * 被 MushroomSyncWorker 调用；亦可被 SettingsViewModel 直接前台调用。
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speciesDao: SpeciesDao,
    private val roomDb: AppDatabase,
) {
    private val tag = "SyncRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val lastSyncAt: Long get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
    val lastSyncCount: Int get() = prefs.getInt(KEY_LAST_SYNC_COUNT, 0)

    /** 全量同步入口。失败抛异常给上层；成功返回总条数。 */
    suspend fun runFullSync() {
        try {
            runFullSyncInternal()
        } catch (t: Exception) {
            if (t is CancellationException) throw t
            _state.value = SyncState.Error(t.message ?: "同步失败")
            throw t
        }
    }

    private suspend fun runFullSyncInternal() {
        val started = System.currentTimeMillis()
        val scraper = Scraper()

        val records = scraper.fetchAll { page, total ->
            _state.value = SyncState.Running(page = page, totalSpecimens = total)
        }

        if (records.isEmpty()) {
            throw IllegalStateException("未获取到物种数据，已保留现有数据库")
        }

        // 1. 写 scraped DB（含完整全字段）
        ScrapedDatabase(context).use { it.replaceAll(records) }
        // 2. 映射导入 Room
        val now = System.currentTimeMillis()
        val entities = records
            .groupBy { normalizedSpeciesKey(it) }
            .toSortedMap()
            .values
            .mapIndexed { index, sourceRecords ->
                val preferred = sourceRecords.maxBy { recordCompleteness(it) }
                val sourceTypes = sourceRecords
                    .map { it.sourceType }
                    .distinct()
                    .sorted()
                val mergedSpecimen = preferred.specimen.copy(
                    edibleFungus = if ("EDIBLE" in sourceTypes) "是" else preferred.specimen.edibleFungus,
                    toxicFungus = if ("TOXIC" in sourceTypes) "是" else preferred.specimen.toxicFungus,
                )
                ScraperToRoomMapper.toEntity(mergedSpecimen, now).copy(
                    id = index + 1,
                    sourceUrl = preferred.sourceUrl,
                    sourceTypes = sourceTypes.joinToString(","),
                )
            }
        roomDb.withTransaction {
            speciesDao.deleteAll()  // 先清空（SpeciesDao 需要此方法）
            speciesDao.insertAll(entities)
        }
        // 3. 持久化时间戳
        prefs.edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .putInt(KEY_LAST_SYNC_COUNT, entities.size)
            .apply()

        val elapsed = System.currentTimeMillis() - started
        _state.value = SyncState.Success(entities.size, elapsed)
    }

    private fun normalizedSpeciesKey(record: com.yangzhiguo.mushroom.scraper.ScrapedRecord): String {
        val specimen = record.specimen
        return sequenceOf(specimen.speciesLatin, specimen.speciesChinese, specimen.speciesCommon)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            ?: "${record.sourceType}:${specimen.id}"
    }

    private fun recordCompleteness(record: com.yangzhiguo.mushroom.scraper.ScrapedRecord): Int {
        val specimen = record.specimen
        return listOf(
            specimen.speciesLatin,
            specimen.speciesChinese,
            specimen.familyChinese,
            specimen.familyEnglish,
            specimen.genusChinese,
            specimen.genusEnglish,
            specimen.speciesDescription,
            specimen.speciesHabitat,
            specimen.cap,
            specimen.lamella,
            specimen.stipe,
        ).count { !it.isNullOrBlank() }
    }

    companion object {
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_COUNT = "last_sync_count"
    }
}
