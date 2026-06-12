package com.yangzhiguo.mushroom.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yangzhiguo.mushroom.sync.SyncRepository
import com.yangzhiguo.mushroom.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncRepo: SyncRepository,
) : ViewModel() {

    val state: StateFlow<SyncState> = syncRepo.state

    val lastSyncLabel: String
        get() {
            val ts = syncRepo.lastSyncAt
            if (ts == 0L) return "从未同步"
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return "${fmt.format(Date(ts))}  ·  ${syncRepo.lastSyncCount} 条"
        }

    val isSyncing: Boolean
        get() = state.value is SyncState.Running

    /** 用户手动触发同步入口（设置页「立即同步」按钮）。 */
    fun startSync() {
        viewModelScope.launch {
            runCatching { syncRepo.runFullSync() }
        }
    }
}
