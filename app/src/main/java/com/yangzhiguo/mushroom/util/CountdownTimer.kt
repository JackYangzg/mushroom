package com.yangzhiguo.mushroom.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 1Hz tick flow emitting remaining ms. Caller collects; when flow completes
 * the SLA is up.
 */
fun countdownFlow(durationMs: Long): Flow<Long> = flow {
    val end = System.currentTimeMillis() + durationMs
    while (true) {
        val remaining = end - System.currentTimeMillis()
        if (remaining <= 0) {
            emit(0L)
            break
        }
        emit(remaining)
        delay(1000L)
    }
}

fun formatMmSs(remainingMs: Long): String {
    val total = (remainingMs / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}
