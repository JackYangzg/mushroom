package com.yangzhiguo.mushroom

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.yangzhiguo.mushroom.sync.SyncRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MushroomApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncRepository: SyncRepository

    override fun onCreate() {
        super.onCreate()
        // ★ 这是「自动同步」的唯一入口：APP 首次启动时在后台调度一次蘑菇数据库全量更新。
        //   SyncRepository 内部用 SharedPreferences 标志位保证幂等（仅首次入队），
        //   并使用 ExistingWorkPolicy.KEEP 避免与「设置页手动同步」重复。
        //   请勿在此处或别处再加其他自动触发（如 onResume / BroadcastReceiver / 周期任务）——
        //   同步策略详见 SyncRepository KDoc。
        //   此处用 runCatching 包裹：调度失败也不能让 APP 起不来。
        runCatching {
            syncRepository.scheduleFirstLaunchSyncIfNeeded(WorkManager.getInstance(this))
        }.onFailure { Log.w(TAG, "First-launch sync scheduling failed", it) }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private companion object {
        const val TAG = "MushroomApplication"
    }
}
