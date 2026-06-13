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
import com.yangzhiguo.mushroom.data.local.SpeciesImageDao
import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.ScrapedRecord
import com.yangzhiguo.mushroom.scraper.Scraper
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
    /**
     * @param completedSources 已完成入库的 source 数(0..totalSources)
     * @param totalSources 本次同步的目标 source 数(默认 4)
     * @param totalSpecimens 累计抓到/写入的 specimen 数
     */
    data class Running(
        val completedSources: Int,
        val totalSources: Int,
        val totalSpecimens: Int,
    ) : SyncState
    data class Success(val totalSpecimens: Int, val elapsedMs: Long) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * 同步协调器(v11)。App 运行时同步和本地离线数据库生成都调用 [runFullSync]，
 * 因此两种产物共享同一套拉取、映射、合并、Room 写入和别名回填逻辑。
 *
 *   - **手动触发**:同步只在用户点击设置页「立即同步」时启动(走 [scheduleSync] →
 *     WorkManager → [MushroomSyncWorker])。**首次启动不再自动同步**,APP 启动只
 *     读 assets 里 ship 的 `mushroom.db`,不主动联网。
 *   - **按 source 粒度入库**:一个 scraw_source 抓完所有页 → 立即 merge + 一次性
 *     upsert 到 Room(2 张表)。后续 source 失败不影响已完成 source 的数据。
 *   - **4 source 全部完成**才发出 `SyncState.Success`,并在 Success 之前用
 *     [AliasBackfiller] 从 `assets/alias_mushroom.db` 回填 `alias_names` 字段。
 *   - **断点续传**:SharedPreferences 存「已完成 source 集合」;中途中断后,下次同步
 *     跳过已完成 source,从第一个未完成 source 重抓。
 *   - **单飞门闩**:用 [SingleFlight] 防止 WorkManager 任务、设置页手动触发、
 *     WorkManager 内部重试三条路径并发执行。
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speciesDao: SpeciesDao,
    private val speciesImageDao: SpeciesImageDao,
    private val roomDb: AppDatabase,
    private val aliasBackfiller: AliasBackfiller,
) {
    private val tag = "SyncRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** 单飞门闩:任意时刻只允许一段 [runFullSync] 实际执行。 */
    private val syncGuard = SingleFlight()

    val lastSyncAt: Long get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
    val lastSyncCount: Int get() = prefs.getInt(KEY_LAST_SYNC_COUNT, 0)

    /**
     * 把 [MushroomSyncWorker] 放进 WorkManager。ExistingWorkPolicy.KEEP:
     *   - 若已有同名 pending work → 忽略本次入队,沿用已调度的那次;
     *   - 若上次已 succeeded/failed/cancelled → 重新入队一次新的全量同步。
     */
    fun scheduleSync(workManager: WorkManager): Boolean {
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
            Log.i(tag, "Background sync enqueued (policy=KEEP).")
            true
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w(tag, "Failed to enqueue sync; will retry next launch", t)
            false
        }
    }

    /**
     * 全量同步入口。被 [MushroomSyncWorker] 调用。
     *
     * - 单飞:若已有同步在跑,本次调用静默返回。
     * - 在门闩内:决定续传/全新 → Scraper 按 source 抓 → 每个 source 完成后立即
     *   writeSourceToRoom → checkpoint 落盘 → 4 source 全部完成才 Success。
     */
    suspend fun runFullSync() {
        syncGuard.run {
            try {
                _state.value = SyncState.Running(completedSources = 0, totalSources = DataSource.entries.size, totalSpecimens = 0)
                runFullSyncInternal()
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                _state.value = SyncState.Error(t.message ?: "同步失败")
                throw t
            }
        }
    }

    private suspend fun runFullSyncInternal() {
        val started = System.currentTimeMillis()
        val now = System.currentTimeMillis()
        val totalSources = DataSource.entries.size

        // ── 决定是「续传」还是「全新同步」───────────────────────────────
        val inProgress = prefs.getBoolean(KEY_SYNC_IN_PROGRESS, false)
        var completedSources: Set<DataSource> = if (inProgress) {
            readCompletedSources()
        } else {
            // 上次已正常结束(或从未同步过):清 checkpoint,wipe 一次 DB,从 source=0 全量开始
            clearCheckpoints()
            roomDb.withTransaction {
                speciesImageDao.deleteAll()
                speciesDao.deleteAll()
            }
            emptySet()
        }
        // 立刻把 in-progress 标记置位,即便抓第 1 个 source 前进程被杀,下次也能识别为「续传」
        prefs.edit().putBoolean(KEY_SYNC_IN_PROGRESS, true).apply()

        val pendingSources = DataSource.entries.toSet() - completedSources
        var completedCount = completedSources.size
        var databaseCount = speciesDao.count()
        _state.value = SyncState.Running(
            completedSources = completedCount,
            totalSources = totalSources,
            totalSpecimens = databaseCount,
        )

        Log.i(tag, "Sync start: inProgress=$inProgress, completed=$completedCount/$totalSources, pending=${pendingSources.map { it.name }}")

        val scraper = Scraper()
        scraper.fetchAll(
            sources = pendingSources,
            onSourceCompleted = { source, sourceRecords ->
                // ★ Part C 核心:一源完成 → 立即 merge + 一次性入库
                writeSourceToRoom(source, sourceRecords, now)
                completedSources = completedSources + source
                prefs.edit()
                    .putStringSet(
                        KEY_SYNC_COMPLETED_SOURCES,
                        completedSources.map { it.name }.toSet(),
                    )
                    .apply()
                completedCount = completedSources.size
                databaseCount = speciesDao.count()
                _state.value = SyncState.Running(
                    completedSources = completedCount,
                    totalSources = totalSources,
                    totalSpecimens = databaseCount,
                )
            },
            onProgress = { doneSources, total ->
                _state.value = SyncState.Running(
                    completedSources = doneSources,
                    totalSources = totalSources,
                    totalSpecimens = total,
                )
            },
        )

        // ★ 只有所有 source 都完成才标记 Success
        if (completedCount == totalSources) {
            clearCheckpoints()
            prefs.edit().putBoolean(KEY_SYNC_IN_PROGRESS, false).apply()

            // ★ 同步完成后,最后用 assets/alias_mushroom.db 回填别名字段。
            //   必须放在 withTransaction 之外(ATTACH DATABASE 不能在事务里)。
            //   失败不致命:Result.skipped=true 时降级为「无别名」状态,不影响 species 主表。
            runCatching { aliasBackfiller.backfill() }
                .onSuccess { r ->
                    Log.i(
                        tag,
                        "Alias backfill: updated=${r.updated}, " +
                            "aliasRows=${r.totalAliasRows}, skipped=${r.skipped}",
                    )
                }
                .onFailure { Log.w(tag, "Alias backfill failed; continuing", it) }

            prefs.edit()
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .putInt(KEY_LAST_SYNC_COUNT, databaseCount)
                .apply()
            val elapsed = System.currentTimeMillis() - started
            _state.value = SyncState.Success(databaseCount, elapsed)
            Log.i(tag, "Sync success: $databaseCount species in ${elapsed}ms")
        } else {
            Log.w(tag, "Sync ended with $completedCount/$totalSources sources; staying in-progress for next resume")
        }
    }

    /**
     * 把一个 source 抓到的所有 records 入库(2 张表)。
     * 同 source 同 scientific name 的 records 由 [CatalogRecordMerger] 折叠,
     * sourceTypes 累加;每个 unique mushroom 写一行 species + 关联 images。
     */
    private suspend fun writeSourceToRoom(
        source: DataSource,
        records: List<ScrapedRecord>,
        now: Long,
    ) {
        if (records.isEmpty()) return
        Log.i(tag, "writeSourceToRoom ${source.name} records=${records.size}")
        val merged = CatalogRecordMerger.merge(records)
        roomDb.withTransaction {
            for (m in merged) {
                val record = m.base
                val batch = ScraperToRoomMapper.toBatch(record = record, now = now)
                val species = batch.species[0].copy(
                    sourceTypes = m.sourceTypes.distinct().joinToString(","),
                )
                speciesDao.upsertAll(listOf(species))
                if (batch.images.isNotEmpty()) speciesImageDao.upsertAll(batch.images)
            }
        }
    }

    private fun readCompletedSources(): Set<DataSource> =
        prefs.getStringSet(KEY_SYNC_COMPLETED_SOURCES, emptySet()).orEmpty()
            .mapNotNull { runCatching { DataSource.valueOf(it) }.getOrNull() }
            .toSet()

    private fun clearCheckpoints() {
        prefs.edit()
            .remove(KEY_SYNC_COMPLETED_SOURCES)
            .apply()
    }

    companion object {
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_COUNT = "last_sync_count"
        private const val KEY_SYNC_IN_PROGRESS = "sync_in_progress"
        private const val KEY_SYNC_COMPLETED_SOURCES = "sync_completed_sources"
    }
}
