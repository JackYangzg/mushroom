package com.yangzhiguo.mushroom

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MushroomApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // 数据库同步策略:**只在用户手动触发时执行**(SettingsScreen「立即同步」按钮 →
        // [com.yangzhiguo.mushroom.ui.settings.SettingsViewModel.startSync])。
        //
        // 历史背景:早期版本会在这里调用 `syncRepository.scheduleFirstLaunchSyncIfNeeded(...)`
        // 在首次启动时自动入队全量同步,导致新装用户一开 APP 就跑 3-source 网络爬取,
        // 既费流量又拖慢首屏。现已删除——APP 启动只读 assets 里预先 ship 的 `mushroom.db`,
        // 不主动联网。
        //
        // 请勿在此处或别处重新加入任何自动触发(onCreate / onResume / BroadcastReceiver /
        // 周期 WorkManager 等)。同步策略详见 [com.yangzhiguo.mushroom.sync.SyncRepository] KDoc。
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
