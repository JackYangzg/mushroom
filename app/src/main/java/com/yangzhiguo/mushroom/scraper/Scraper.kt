package com.yangzhiguo.mushroom.scraper

import android.util.Log

/**
 * 抓取器：探测式分页，从 current=1 起逐页抓取直到返回空 records。
 * 每页 size=6（API 默认）；总页数会随站点增长而增长——不能写死 820。
 *
 * @param onProgress (currentPage, totalSpecimens) → Unit；每完成一页回调一次
 */
class Scraper(
    private val api: ApiClient = ApiClient(),
    private val pageSize: Int = 6,
) {
    private val tag = "Scraper"

    suspend fun fetchAll(
        onProgress: suspend (page: Int, totalSpecimens: Int) -> Unit = { _, _ -> },
    ): List<ScrapedRecord> {
        val all = mutableListOf<ScrapedRecord>()
        val seen = mutableSetOf<String>()
        var completedPages = 0

        for (source in DataSource.entries) {
            var page = 1
            while (true) {
                val specimens = api.fetchPage(source, page, pageSize)
                if (specimens.isEmpty()) {
                    Log.i(tag, "${source.name} stopped at empty page $page")
                    break
                }
                specimens.forEach { specimen ->
                    val record = ScrapedRecord(specimen, source, source.detailUrl(specimen))
                    if (seen.add(record.dedupeKey)) all += record
                }
                completedPages++
                onProgress(completedPages, all.size)
                page++
                check(page <= 100_000) {
                    "${source.name} exceeded pagination safety limit without an empty page"
                }
            }
        }
        Log.i(tag, "fetchAll done: ${all.size} unique records across $completedPages pages")
        return all
    }
}
