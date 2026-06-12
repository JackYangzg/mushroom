package com.yangzhiguo.mushroom.cache

import android.content.Context
import android.util.Log
import com.yangzhiguo.mushroom.data.local.SpeciesDao
import com.yangzhiguo.mushroom.scraper.ApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片懒加载缓存。
 *
 * 触发：用户在 SpeciesDetailScreen 查看某个 specimen。
 * 流程：
 *  1. 读 DB imageLocalPath 字段
 *   - 存在 & 文件还在 → 直接返回本地 File（缓存命中）
 *   - 存在但文件丢失 → 删字段、转下载
 *   - 不存在 & remoteUrl 有效 → 走远端
 *  2. 远端下载到 filesDir/specimen_images/{id}/{filename}
 *  3. 更新 DB imageLocalPath（写入相对 filesDir 的路径）
 *  4. 返回 File
 *
 * 单 specimen 内部用 Mutex 防并发同 ID 重复下载。
 */
@Singleton
class ImageCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiClient,
    private val dao: SpeciesDao,
) {
    private val tag = "ImageCache"
    private val inflight = mutableMapOf<Int, Mutex>()

    suspend fun getOrFetch(
        specimenId: Int,
        remoteUrl: String?,
    ): File? = withContext(Dispatchers.IO) {
        val mutex = synchronized(inflight) {
            inflight.getOrPut(specimenId) { Mutex() }
        }
        mutex.withLock {
            val entity = dao.findById(specimenId)
            val localRel = entity?.imageLocalPath
            val remote = entity?.imageUrl ?: remoteUrl

            // 1) 本地命中
            if (!localRel.isNullOrEmpty()) {
                val localFile = File(context.filesDir, localRel)
                if (localFile.exists() && localFile.length() > 0) {
                    Log.d(tag, "cache hit: $localRel")
                    return@withLock localFile
                }
                Log.w(tag, "local path set but file missing; will re-fetch")
            }

            // 2) 远端拉取
            if (remote.isNullOrBlank()) {
                Log.w(tag, "no remote url for specimen $specimenId")
                return@withLock null
            }

            val filename = deriveFilename(remote)
            val relPath = "specimen_images/$specimenId/$filename"
            val target = File(context.filesDir, relPath)
            try {
                api.downloadBytes(remote, target)
                dao.updateImagePath(specimenId, relPath)
                Log.i(tag, "fetched & cached $relPath (${target.length()} bytes)")
                target
            } catch (t: Throwable) {
                Log.w(tag, "fetch failed for specimen $specimenId: ${t.message}")
                null
            }
        }
    }

    private fun deriveFilename(url: String): String {
        val cleaned = url.substringBefore('?').substringBefore('#')
        return cleaned.substringAfterLast('/').ifBlank { "${url.hashCode()}.img" }
    }
}
