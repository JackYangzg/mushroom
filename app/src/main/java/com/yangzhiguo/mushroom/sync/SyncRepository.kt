package com.yangzhiguo.mushroom.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yangzhiguo.mushroom.data.local.AppDatabase
import com.yangzhiguo.mushroom.data.local.DistributionPointDao
import com.yangzhiguo.mushroom.data.local.DnaBarcodeDao
import com.yangzhiguo.mushroom.data.local.SpecimenDao
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.data.local.SpeciesImageDao
import com.yangzhiguo.mushroom.scraper.ScrapedDatabase
import com.yangzhiguo.mushroom.scraper.Scraper
import com.yangzhiguo.mushroom.util.RegionCodeDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** 同步状态;UI 通过 [SyncRepository.state] 订阅。 */
sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val page: Int = 0, val totalSpecimens: Int = 0) : SyncState
    data class Success(val totalSpecimens: Int, val elapsedMs: Long) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * 同步协调器:抓取 → 写 scraped DB → 映射导入 Room(5 张表事务)→ 更新 SharedPreferences。
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speciesDao: SpeciesDao,
    private val specimenDao: SpecimenDao,
    private val dnaBarcodeDao: DnaBarcodeDao,
    private val distributionPointDao: DistributionPointDao,
    private val speciesImageDao: SpeciesImageDao,
    private val roomDb: AppDatabase,
) {
    private val tag = "SyncRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val lastSyncAt: Long get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
    val lastSyncCount: Int get() = prefs.getInt(KEY_LAST_SYNC_COUNT, 0)

    val isFirstLaunchSyncScheduled: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_SYNC_DONE, false)

    fun scheduleFirstLaunchSyncIfNeeded(workManager: WorkManager): Boolean {
        if (isFirstLaunchSyncScheduled) {
            Log.d(tag, "First-launch sync already scheduled, skipping.")
            return false
        }
        return try {
            val request = OneTimeWorkRequestBuilder<MushroomSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                MushroomSyncWorker.UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH_SYNC_DONE, true).apply()
            Log.i(tag, "First-launch background sync scheduled.")
            true
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w(tag, "Failed to schedule first-launch sync; will retry next launch", t)
            false
        }
    }

    /** 全量同步入口。失败抛异常给上层;成功返回总条数。 */
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

        // 1. 写 scraped DB
        ScrapedDatabase(context).use { it.replaceAll(records) }
        // 2. 按 sourceUrl 去重,然后一次性映射 + 5 表事务写入
        val now = System.currentTimeMillis()
        val regionLookup = ScraperToRoomMapper.RegionLookup { code ->
            RegionCodeDecoder.decode(context, code)
        }
        val unique = records.distinctBy { it.sourceUrl.trim() }

        var databaseCount = 0
        roomDb.withTransaction {
            // dev 阶段:整库 destructive,所以这里不需要再 deleteAll;
            // 保留 deleteAll 兜底,允许外部手工重置 (例如设置页"清空本地缓存")。
            speciesDao.deleteAll()
            for (record in unique) {
                val batch = ScraperToRoomMapper.toBatch(record, regionLookup = regionLookup, now = now)
                speciesDao.insertAll(batch.species)
                if (batch.specimens.isNotEmpty()) specimenDao.upsertAll(batch.specimens)
                if (batch.images.isNotEmpty()) speciesImageDao.upsertAll(batch.images)
                if (batch.barcodes.isNotEmpty()) dnaBarcodeDao.upsertAll(batch.barcodes)
                if (batch.distributionPoints.isNotEmpty()) distributionPointDao.upsertAll(batch.distributionPoints)
            }
            // 两个来源可能使用相同远端 ID，REPLACE 后的实际行数才是数据库记录数。
            databaseCount = speciesDao.count()
        }
        // 3. 持久化时间戳
        prefs.edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .putInt(KEY_LAST_SYNC_COUNT, databaseCount)
            .apply()

        val elapsed = System.currentTimeMillis() - started
        _state.value = SyncState.Success(databaseCount, elapsed)
    }

    companion object {
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_COUNT = "last_sync_count"
        private const val KEY_FIRST_LAUNCH_SYNC_DONE = "first_launch_sync_done"
    }
}
