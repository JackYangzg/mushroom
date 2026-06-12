package com.yangzhiguo.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * 临时爬虫入口（**仅元数据，不下载图片**）。
 *  1. 遍历 page=1..maxPage（默认 820）抓 specimen 列表 JSON
 *  2. 写入 SQLite（data/mushroom.db），每条记录附 source_url 用于反查
 *  3. 打印统计
 *
 * 用法（项目根目录）：
 *   ./gradlew :scraper:run                                # 全量
 *   ./gradlew :scraper:run --args="--pages 1 5"           # 仅前 5 页（烟测）
 *   ./gradlew :scraper:run --args="--fresh"               # 清库重抓
 *   ./gradlew :scraper:run --args="--resume"              # 跳过已抓页面
 */
fun main(args: Array<String>) = runBlocking {
    val log = LoggerFactory.getLogger("Main")

    val opts = parseArgs(args)
    val cwd = Paths.get("").toAbsolutePath()
    val projectRoot = if (cwd.fileName?.toString() == "scraper") cwd.parent else cwd
    val dataDir = opts.dataDir ?: projectRoot.resolve("data")
    val dbPath = dataDir.resolve("mushroom.db")

    Files.createDirectories(dataDir)
    if (opts.fresh && Files.exists(dbPath)) {
        Files.delete(dbPath)
        listOf("$dbPath-wal", "$dbPath-shm").forEach {
            val p = Paths.get(it)
            if (Files.exists(p)) Files.delete(p)
        }
        log.info("Deleted existing DB (fresh mode)")
    }

    log.info("data dir   : $dataDir")
    log.info("db path    : $dbPath")
    log.info("pages      : ${opts.startPage}..${opts.endPage}")
    log.info("pageSize   : ${opts.pageSize}")
    log.info("pageConcur : ${opts.pageConcurrency}")
    log.info("fresh      : ${opts.fresh}, resume: ${opts.resume}")

    Database(dbPath).use { db ->
        val api = ApiClient()
        val started = Instant.now()

        scrapeAllPages(api, db, opts)

        val dur = Duration.between(started, Instant.now())
        log.info("===== DONE =====")
        log.info("specimens  : ${db.countSpecimens()}")
        log.info("elapsed    : ${dur.toSeconds()}s")
        log.info("samples    :")
        for (s in db.sample(3)) {
            log.info("  id={} latin='{}' chinese='{}' url={}", s.id, s.speciesLatin, s.speciesChinese, s.sourceUrl)
        }
    }
}

private data class Options(
    val startPage: Int = 1,
    val endPage: Int = 820,
    val pageSize: Int = 6,
    val pageConcurrency: Int = 6,
    val fresh: Boolean = false,
    val resume: Boolean = false,
    val dataDir: Path? = null,
)

private fun parseArgs(args: Array<String>): Options {
    var o = Options()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--pages" -> { o = o.copy(startPage = args[++i].toInt(), endPage = args[++i].toInt()) }
            "--size" -> o = o.copy(pageSize = args[++i].toInt())
            "--page-concurrency" -> o = o.copy(pageConcurrency = args[++i].toInt())
            "--fresh" -> o = o.copy(fresh = true)
            "--resume" -> o = o.copy(resume = true)
            "--data-dir" -> o = o.copy(dataDir = Paths.get(args[++i]))
            "-h", "--help" -> { printHelp(); exitProcess(0) }
            else -> System.err.println("Unknown arg: ${args[i]}")
        }
        i++
    }
    return o
}

private fun printHelp() {
    println(
        """
        Mushroom Scraper — temporary offline metadata crawler for iflora.cn

        Usage:
          ./gradlew :scraper:run --args="[options]"

        Options:
          --pages A B              Page range (1-indexed, default: 1 820)
          --size N                 Records per page (default: 6)
          --page-concurrency N     Concurrent page requests (default: 6)
          --fresh                  Wipe existing DB before scraping
          --resume                 Skip pages already represented in DB
          --data-dir PATH          Override data output directory

        Output:
          data/mushroom.db   — SQLite with table mushroom_specimen (id + 100 API fields + source_url)
        """.trimIndent()
    )
}

private suspend fun scrapeAllPages(api: ApiClient, db: Database, opts: Options) {
    val log = LoggerFactory.getLogger("Scrape")
    val semaphore = Semaphore(opts.pageConcurrency)
    val total = opts.endPage - opts.startPage + 1
    val done = AtomicInteger(0)
    val errored = AtomicInteger(0)
    val started = Instant.now()

    coroutineScope {
        val jobs = (opts.startPage..opts.endPage).map { page ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        if (opts.resume && db.countSpecimens() > 0) {
                            val expected = (page - opts.startPage + 1) * opts.pageSize
                            val current = db.countSpecimens().toInt()
                            if (current >= expected) {
                                log.debug("page {} skipped (resume)", page)
                                return@async
                            }
                        }
                        val resp = api.fetchPage(page.toLong(), opts.pageSize)
                        val records = resp.data?.records.orEmpty()
                        for (s in records) {
                            val srcUrl = "https://fungi.iflora.cn/#/species_specimen/retrieve?page=$page"
                            db.upsertSpecimen(s, srcUrl)
                        }
                        val n = done.incrementAndGet()
                        if (n % 50 == 0 || n == total) {
                            val elapsed = Duration.between(started, Instant.now()).toSeconds()
                            log.info("pages {}/{} ({}%) — elapsed {}s — specimens={}",
                                n, total, n * 100 / total, elapsed, db.countSpecimens())
                        }
                    } catch (t: Throwable) {
                        errored.incrementAndGet()
                        log.error("page {} failed: {}", page, t.toString())
                    }
                }
            }
        }
        jobs.awaitAll()
    }
    log.info("scrape done: {} pages ok, {} errored", done.get(), errored.get())
}