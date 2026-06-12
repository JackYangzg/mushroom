package com.yangzhiguo.mushroom.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager 后台同步任务。
 * UI 通过 [androidx.work.WorkManager.getWorkInfoByIdLiveData] 订阅进度；
 * 实际进度由 [SyncRepository.state] 提供。
 */
@HiltWorker
class MushroomSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: SyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        repo.runFullSync()
        Result.success()
    } catch (t: Throwable) {
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }

    companion object {
        const val UNIQUE_NAME = "mushroom_sync"
    }
}
