package com.yangzhiguo.scraper

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

/**
 * 临时爬虫入口（**仅元数据，不下载图片**）。
 *  1. 依次抓取标本检索与物种总目录，均从 page=1 到首个空页
 *  2. 写入 SQLite（data/mushroom.db），每条记录附 source_type 和 source_url
 *  3. 打印统计
 *
 * 用法（项目根目录）：
 *   ./gradlew -p scraper run                              # 更新两个数据源
 *   ./gradlew -p scraper run --args="--fresh"             # 清库重抓
 *   ./gradlew -p scraper run --args="--resume"            # 保留现有数据并更新
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
    log.info("pageSize   : ${opts.pageSize}")
    log.info("fresh      : ${opts.fresh}, resume: ${opts.resume}")

    Database(dbPath).use { db ->
        val api = ApiClient()
        val started = Instant.now()

        scrapeAllSources(api, db, opts)

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
    val pageSize: Int = 6,
    val fresh: Boolean = false,
    val resume: Boolean = false,
    val dataDir: Path? = null,
)

private fun parseArgs(args: Array<String>): Options {
    var o = Options()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--size" -> o = o.copy(pageSize = args[++i].toInt())
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
          ./gradlew -p scraper run --args="[options]"

        Options:
          --size N                 Records per page (default: 6)
          --fresh                  Wipe existing DB before scraping
          --resume                 Skip pages already represented in DB
          --data-dir PATH          Override data output directory

        Output:
          data/mushroom.db   — SQLite with table mushroom_specimen (API fields + source_type + source_url)
        """.trimIndent()
    )
}

private suspend fun scrapeAllSources(api: ApiClient, db: Database, opts: Options) {
    val log = LoggerFactory.getLogger("Scrape")
    val started = Instant.now()
    val seen = mutableSetOf<String>()
    if (!opts.resume) db.deleteAll()

    for (source in DataSource.entries) {
        var page = 1
        var sourceCount = 0
        while (true) {
            val specimens = api.fetchPage(source, page, opts.pageSize)
            if (specimens.isEmpty()) {
                log.info("{} stopped at empty page {}", source.name, page)
                break
            }
            for (specimen in specimens) {
                val record = ScrapedRecord(specimen, source, source.detailUrl(specimen))
                if (seen.add(record.dedupeKey)) {
                    db.upsertSpecimen(record.specimen, record.sourceType, record.sourceUrl)
                    sourceCount++
                }
            }
            db.flush()
            if (page % 50 == 0) {
                log.info("{} page {} — source records={} — database={}",
                    source.name, page, sourceCount, db.countSpecimens())
            }
            page++
            check(page <= 100_000) {
                "${source.name} exceeded pagination safety limit without an empty page"
            }
        }
        log.info("{} complete: {} unique records", source.name, sourceCount)
    }
    log.info("all sources complete in {}s", Duration.between(started, Instant.now()).toSeconds())
}
