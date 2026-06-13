package com.yangzhiguo.mushroom.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 单飞门闩：同一时刻只允许一段 [block] 在跑。后续调用 [run] 若发现门闩已被占用,
 * 会**静默返回**(不再抛异常、不再排队),让已经在跑的实例独占完成。
 *
 * 设计目标:数据库同步的多个入口(WorkManager 后台首启 + 设置页手动点击 + WorkManager 重试)
 * 共享同一个 SyncRepository,本类确保不会出现两个 `runFullSync` 并发执行,
 * 避免 Room / `_state` 两方竞态。
 *
 * 为什么选「静默返回」而不是「抛异常」或「挂起等待」:
 *  - 抛异常会让 WorkManager 误以为失败 → 触发重试 → 又一次并发调用,死循环。
 *  - 挂起等待会让 `viewModelScope.launch` 在用户已经离开设置页时还卡着协程,
 *    且 WorkManager 的 CoroutineWorker 上下文不支持长挂起(任务会被系统杀掉)。
 *
 * 用法:
 * ```
 * private val syncGuard = SingleFlight()
 * suspend fun runFullSync() = syncGuard.run { runFullSyncInternal() }
 * ```
 */
class SingleFlight {
    private val mutex = Mutex()
    private var inFlight = false

    /**
     * 若当前无任务在跑,则将 [block] 作为唯一执行者跑完(无论成败都释放门闩);
     * 若门闩已被占用,直接返回,不抛异常。
     */
    suspend fun run(block: suspend () -> Unit) {
        val weAreStarter = mutex.withLock {
            if (inFlight) {
                false
            } else {
                inFlight = true
                true
            }
        }
        if (!weAreStarter) return
        try {
            block()
        } finally {
            mutex.withLock { inFlight = false }
        }
    }

    /** 仅供测试/观测使用:是否正在执行。 */
    val isRunning: Boolean
        get() = inFlight
}
