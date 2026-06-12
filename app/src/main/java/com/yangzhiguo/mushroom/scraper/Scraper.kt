package com.yangzhiguo.mushroom.scraper

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 抓取器：探测式分页，从 current=1 起逐页抓取直到返回空 records。
 * 每页 size=6（API 默认）；总页数会随站点增长而增长——不能写死 820。
 *
 * @param onProgress (currentPage, totalSpecimens) → Unit；每完成一页回调一次
 */
class Scraper(
    private val api: ApiClient = ApiClient(),
    private val pageSize: Int = 6,
    private val pageConcurrency: Int = 6,
    private val maxEmptyPagesInARow: Int = 3,
) {
    private val tag = "Scraper"

    suspend fun fetchAll(
        onProgress: suspend (page: Int, totalSpecimens: Int) -> Unit = { _, _ -> },
    ): List<Specimen> {
        val all = mutableListOf<Specimen>()
        var page = 1
        var emptyStreak = 0

        while (true) {
            val records: List<Specimen> = try {
                val resp = api.fetchPage(page.toLong(), pageSize)
                resp.data?.records.orEmpty()
            } catch (t: Throwable) {
                Log.w(tag, "page $page failed: ${t.message}")
                emptyStreak++
                if (emptyStreak >= maxEmptyPagesInARow) break
                page++
                continue
            }

            if (records.isEmpty()) {
                emptyStreak++
                Log.d(tag, "page $page empty (streak=$emptyStreak)")
                if (emptyStreak >= maxEmptyPagesInARow) break
                page++
                continue
            }

            emptyStreak = 0
            all += records
            onProgress(page, all.size)
            page++

            if (all.size >= 50_000) {
                Log.w(tag, "hit soft cap 50000, stopping")
                break
            }
        }
        Log.i(tag, "fetchAll done: ${all.size} specimens across $page pages")
        return all
    }

    @Suppress("unused")
    suspend fun fetchAllParallel(
        pageRange: IntRange,
        onProgress: suspend (page: Int, totalSpecimens: Int) -> Unit = { _, _ -> },
    ): List<Specimen> {
        val semaphore = Semaphore(pageConcurrency)
        val all = mutableListOf<Specimen>()
        coroutineScope {
            val jobs = pageRange.map { p ->
                async {
                    semaphore.withPermit {
                        try {
                            api.fetchPage(p.toLong(), pageSize).data?.records.orEmpty()
                        } catch (t: Throwable) {
                            Log.w(tag, "page $p failed: ${t.message}")
                            emptyList<Specimen>()
                        }
                    }
                }
            }
            jobs.awaitAll().forEachIndexed { i, recs ->
                all += recs
                onProgress(pageRange.first + i, all.size)
            }
        }
        return all
    }

    fun sourceUrlFor(page: Int) = "https://fungi.iflora.cn/#/species_specimen/retrieve?page=$page"
}