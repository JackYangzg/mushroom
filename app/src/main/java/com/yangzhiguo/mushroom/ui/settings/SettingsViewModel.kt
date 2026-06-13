package com.yangzhiguo.mushroom.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.sync.SyncRepository
import com.yangzhiguo.mushroom.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepo: SyncRepository,
    private val speciesDao: SpeciesDao,
) : ViewModel() {

    val state: StateFlow<SyncState> = syncRepo.state

    /**
     * 数据库中当前条目数（实时来自 [SpeciesDao.count]）。
     * 即使从未同步，也会显示种子数据条数；同步结束后自动刷新。
     */
    private val _databaseCount = MutableStateFlow(0)
    val databaseCount: StateFlow<Int> = _databaseCount.asStateFlow()

    init {
        refreshDatabaseCount()
    }

    val lastSyncLabel: String
        get() {
            val ts = syncRepo.lastSyncAt
            if (ts == 0L) return "从未同步"
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return "${fmt.format(Date(ts))}  ·  ${databaseCount.value} 条"
        }

    val isSyncing: Boolean
        get() = state.value is SyncState.Running

    /**
     * 用户手动触发同步入口（设置页「立即同步」按钮）。
     *
     * 不直接在 viewModelScope 里跑 `runFullSync`——那样用户切到后台、Activity
     * 被销毁时协程会被取消,导致同步中途被打断。改用 WorkManager 入队
     * [com.yangzhiguo.mushroom.sync.MushroomSyncWorker],系统会接管协程、跨
     * Activity 生命周期,且 ExistingWorkPolicy.KEEP 保证不会和首启入队的 worker
     * 双跑。UI 通过 [SyncRepository.state] 看到进度。
     */
    fun startSync() {
        syncRepo.scheduleSync(WorkManager.getInstance(context))
    }

    /** 重新从 Room 读一次当前条数。同步完成或外部清库后都可调。 */
    fun refreshDatabaseCount() {
        viewModelScope.launch {
            _databaseCount.value = speciesDao.count()
        }
    }
}
