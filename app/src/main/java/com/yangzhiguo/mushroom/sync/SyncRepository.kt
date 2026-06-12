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
 *
 * 触发策略（重要，请勿随意新增自动入口）：
 * - 自动同步：**仅** 在 APP 首次启动时由 [com.yangzhiguo.mushroom.MushroomApplication.onCreate]
 *   调度一次（用 SharedPreferences 标志位 + ExistingWorkPolicy.KEEP 幂等去重）。
 * - 手动同步：由用户在「设置」页点击触发，调用 [runFullSync] 直接前台执行。
 * - **禁止** 周期性同步、AppLifecycleObserver / onResume 触发、BOOT_COMPLETED 触发、
 *   ConnectivityManager.NetworkCallback 触发等任何其他自动入口。
 *   如需定时刷新数据，请在产品评审通过后单独开 PR。
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

    /** 首次启动时是否已经调度过后台同步（用于幂等去重）。 */
    val isFirstLaunchSyncScheduled: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_SYNC_DONE, false)

    /**
     * 首次启动触发：在后台调度一次蘑菇数据库全量同步，且只调度一次。
     *
     * 设计要点：
     * - 通过 SharedPreferences 标志位保证幂等：仅在「未调度过」时执行；
     *   调度成功后才写标志，避免抛异常时阻塞下次重试。
     * - 使用 [ExistingWorkPolicy.KEEP]：若同名 work 已在运行/排队，
     *   跳过本次 enqueue，避免与「设置页手动同步」产生重复。
     * - 加 [NetworkType.CONNECTED] 约束：等待联网后再抓取。
     *
     * @return true = 本次确实入队了一个新 work；false = 已是后续启动、未入队或入队失败。
     */
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
        // 2. URL 是抓取记录的唯一键；名称、来源和远端数值 ID 都不参与去重。
        val now = System.currentTimeMillis()
        val entities = records
            .distinctBy { it.sourceUrl.trim() }
            .map { record ->
                val mapped = ScraperToRoomMapper.toEntity(record.specimen, now)
                mapped.copy(
                    id = 0,
                    sourceUrl = record.sourceUrl,
                    sourceTypes = sequenceOf(record.sourceType, mapped.sourceTypes)
                        .filter(String::isNotBlank)
                        .joinToString(","),
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

    companion object {
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_COUNT = "last_sync_count"
        private const val KEY_FIRST_LAUNCH_SYNC_DONE = "first_launch_sync_done"
    }
}
