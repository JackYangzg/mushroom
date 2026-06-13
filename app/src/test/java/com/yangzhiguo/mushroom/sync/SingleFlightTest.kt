package com.yangzhiguo.mushroom.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试:并发调用 SingleFlight.run 只执行 block 一次。
 * 此前 SyncRepository.runFullSync 缺少这层门闩,WorkManager 后台任务与 Settings
 * 手动点击会在 Room / `_state` 两处竞态——UI 表现为「同步重新开始」,
 * 即 page 计数忽大忽小、两条 HTTP 抓取线并行。修法:加这道门闩,后续调用静默返回。
 */
class SingleFlightTest {

    @Test
    fun concurrentInvocationsExecuteBlockExactlyOnce() = runBlocking {
        val sf = SingleFlight()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0

        // 第一个调用：进入 block 后挂起,等测试显式释放
        val first = launch {
            sf.run {
                executions++
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()

        // 第二个/第三个调用在 first 还在跑时进入 — 必须**静默返回**且 block 不被执行
        val second = launch { sf.run { executions++ } }
        val third = launch { sf.run { executions++ } }

        // 给协程一点时间尝试进入
        repeat(20) { yield() }
        assertEquals("concurrent calls must not execute the block", 1, executions)
        assertTrue("guard must report running", sf.isRunning)

        release.complete(Unit)
        first.join()
        second.join()
        third.join()
        assertEquals(1, executions)
        assertFalse(sf.isRunning)
    }

    @Test
    fun afterFirstCompletesSecondRunsNormally() = runBlocking {
        val sf = SingleFlight()
        var executions = 0
        sf.run { executions++ }
        sf.run { executions++ }
        sf.run { executions++ }
        assertEquals(3, executions)
    }

    @Test
    fun exceptionInBlockReleasesGuardSoNextCallCanRun() = runBlocking {
        val sf = SingleFlight()
        var executions = 0
        try {
            sf.run { executions++; error("boom") }
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(1, executions)
        assertFalse(sf.isRunning)

        sf.run { executions++ }
        assertEquals(2, executions)
    }

    @Test
    fun isRunningReflectsInFlightState() = runBlocking {
        val sf = SingleFlight()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        assertFalse(sf.isRunning)

        val job = launch {
            sf.run {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        assertTrue(sf.isRunning)

        release.complete(Unit)
        job.join()
        assertFalse(sf.isRunning)
    }

    @Test
    fun asyncChildAlsoSkipsWhileParentRunning() = runBlocking {
        val sf = SingleFlight()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0

        val parent = launch {
            sf.run {
                executions++
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()

        // async 也算"调用" — 同样应该被门闩挡住
        val child = async { sf.run { executions++ } }
        // 给协程调度机会
        repeat(20) { delay(1) }
        assertEquals(1, executions)
        assertTrue(sf.isRunning)

        release.complete(Unit)
        parent.join()
        child.await()
        assertEquals(1, executions)
    }
}
