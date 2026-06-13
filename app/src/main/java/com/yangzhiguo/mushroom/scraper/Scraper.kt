package com.yangzhiguo.mushroom.scraper

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * 抓取器:探测式分页,从 page=1 起逐页抓取直到返回空 records。
 * 每页 size=50;总页数会随站点增长而增长,以空页作为结束条件。
 *
 * v10 重设计:
 * - 按 source 粒度回调 [onSourceCompleted]:一个 source 的所有页抓完后回调一次,
 *   调用方(SyncRepository)负责把 records 一次性 upsert 到 Room。
 * - 删除了原来的 `resumePages` 参数 + `onPageRecords` 回调;
 *   断点续传现在以 source 为单位(由 SyncRepository 维护已完成 source 集合)。
 *
 * @param onProgress (completedSources, totalSpecimens) → Unit;每抓完一页回调一次
 *   (累计页数,不是已完成的 source 数)
 * @param onSourceCompleted (source, sourceRecords) → 每抓完一个 source,回调该 source 的
 *   去重后的 records 列表。调用方负责入库。
 */
class Scraper(
    private val api: ApiClient = ApiClient(),
    private val pageSize: Int = 50,
    private val pageLoader: suspend (DataSource, Int, Int) -> List<Specimen> =
        { source, page, size -> api.fetchPage(source, page, size) },
) {
    private val tag = "Scraper"

    suspend fun fetchAll(
        sources: Set<DataSource> = DataSource.entries.toSet(),
        onSourceCompleted: suspend (source: DataSource, sourceRecords: List<ScrapedRecord>) -> Unit =
            { _, _ -> },
        onProgress: suspend (completedSources: Int, totalSpecimens: Int) -> Unit = { _, _ -> },
    ): List<ScrapedRecord> {
        val all = mutableListOf<ScrapedRecord>()
        val seen = mutableSetOf<String>()
        var completedSources = 0
        var skippedPages = 0
        var totalPages = 0

        for (source in sources) {
            // 每个 source 单独 buffer;source 内跨页去重,但不跨 source 去重
            // (同一 mushroom_id 在不同 source 出现时应产生独立 records,因为 (scraw_source, source_url) 是 DB 唯一键)
            val sourceBuffer = mutableListOf<ScrapedRecord>()
            val seenInSource = mutableSetOf<String>()

            var page = 1
            var consecutivePageFailures = 0

            while (true) {
                val specimens = try {
                    pageLoader(source, page, pageSize)
                } catch (t: Exception) {
                    if (t is CancellationException) throw t

                    skippedPages++
                    consecutivePageFailures++
                    totalPages++
                    Log.w(tag, "Skipping failed page ${source.name} page=$page", t)
                    onProgress(completedSources, all.size)

                    page++
                    if (consecutivePageFailures >= MAX_CONSECUTIVE_PAGE_FAILURES) {
                        Log.w(
                            tag,
                            "Stopping ${source.name} after " +
                                "$consecutivePageFailures consecutive page failures",
                        )
                        break
                    }
                    check(page <= MAX_PAGE_NUMBER) {
                        "${source.name} exceeded pagination safety limit without an empty page"
                    }
                    continue
                }

                consecutivePageFailures = 0
                if (specimens.isEmpty()) {
                    Log.i(tag, "${source.name} stopped at empty page $page")
                    break
                }

                for (specimen in specimens) {
                    val record = ScrapedRecord(specimen, source, source.detailUrl(specimen))
                    if (seenInSource.add(record.dedupeKey)) {
                        sourceBuffer += record
                    }
                    if (seen.add(record.dedupeKey)) {
                        all += record
                    }
                }

                totalPages++
                onProgress(completedSources, all.size)
                page++
                check(page <= MAX_PAGE_NUMBER) {
                    "${source.name} exceeded pagination safety limit without an empty page"
                }
            }

            // 一个 source 抓完 → 一次性 emit
            if (sourceBuffer.isNotEmpty()) {
                Log.i(tag, "${source.name} completed: ${sourceBuffer.size} records")
            }
            onSourceCompleted(source, sourceBuffer.toList())
            completedSources++
            onProgress(completedSources, all.size)
        }

        Log.i(
            tag,
            "fetchAll done: ${all.size} unique records across $totalPages pages " +
                "($completedSources sources), skippedPages=$skippedPages",
        )
        return all
    }

    companion object {
        private const val MAX_CONSECUTIVE_PAGE_FAILURES = 3
        private const val MAX_PAGE_NUMBER = 100_000
    }
}
