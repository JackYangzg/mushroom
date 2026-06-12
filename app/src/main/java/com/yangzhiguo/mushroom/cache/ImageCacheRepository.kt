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

@Singleton
class ImageCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiClient,
    private val dao: SpeciesDao,
) {
    private val tag = "ImageCache"
    private val inflight = mutableMapOf<Int, Mutex>()

    suspend fun getOrFetchThumbnail(
        specimenId: Int,
        remoteUrl: String?,
        scientificName: String?,
        sourceUrl: String?,
    ): File? = withSpeciesLock(specimenId) {
        cachedFiles(specimenId).firstOrNull()?.let { return@withSpeciesLock it }
        val urls = resolveRemoteUrls(specimenId, remoteUrl, scientificName, sourceUrl)
        urls.forEachIndexed { index, url ->
            download(specimenId, url, index)?.let { file ->
                dao.updateImageUrl(specimenId, url)
                dao.updateImagePath(specimenId, relativePath(file))
                return@withSpeciesLock file
            }
        }
        null
    }

    suspend fun getOrFetchAll(
        specimenId: Int,
        remoteUrl: String?,
        scientificName: String?,
        sourceUrl: String?,
    ): List<File> = withSpeciesLock(specimenId) {
        val cached = cachedFiles(specimenId)
        if (completionMarker(specimenId).exists() && cached.isNotEmpty()) {
            return@withSpeciesLock cached
        }

        val urls = resolveRemoteUrls(specimenId, remoteUrl, scientificName, sourceUrl)
        if (urls.isEmpty()) return@withSpeciesLock cached

        val downloaded = urls.mapIndexedNotNull { index, url ->
            val target = targetFile(specimenId, url, index)
            val file = when {
                target.exists() && target.length() > 0 -> target
                else -> download(specimenId, url, index)
            }
            file?.let { url to it }
        }
        if (downloaded.size == urls.size && downloaded.isNotEmpty()) {
            completionMarker(specimenId).apply {
                parentFile?.mkdirs()
                writeText(urls.joinToString("\n"))
            }
        } else {
            completionMarker(specimenId).delete()
        }
        if (downloaded.isNotEmpty()) {
            val (successfulUrl, firstFile) = downloaded.first()
            dao.updateImageUrl(specimenId, successfulUrl)
            dao.updateImagePath(specimenId, relativePath(firstFile))
        }
        val files = downloaded.map { it.second }
        files.ifEmpty { cachedFiles(specimenId) }
    }

    /** Compatibility for older single-image callers. */
    suspend fun getOrFetch(
        specimenId: Int,
        remoteUrl: String?,
        scientificName: String? = null,
        sourceUrl: String? = null,
    ): File? = getOrFetchThumbnail(specimenId, remoteUrl, scientificName, sourceUrl)

    private suspend fun resolveRemoteUrls(
        specimenId: Int,
        remoteUrl: String?,
        scientificName: String?,
        sourceUrl: String?,
    ): List<String> {
        val entity = dao.findById(specimenId)
        val known = listOfNotNull(entity?.imageUrl, remoteUrl).filter { it.isNotBlank() }
        val discovered = if (!scientificName.isNullOrBlank()) {
            runCatching { api.findImageUrls(scientificName, sourceUrl) }
                .onFailure { Log.w(tag, "image lookup failed for $scientificName: ${it.message}") }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return (discovered + known).distinct()
    }

    private suspend fun download(specimenId: Int, url: String, index: Int): File? {
        val target = targetFile(specimenId, url, index)
        return try {
            api.downloadBytes(url, target)
            if (target.length() > 0) {
                Log.i(tag, "cached image ${target.name} (${target.length()} bytes)")
                target
            } else {
                target.delete()
                null
            }
        } catch (t: Throwable) {
            target.delete()
            Log.w(tag, "image download failed for specimen $specimenId: ${t.message}")
            null
        }
    }

    private fun cachedFiles(specimenId: Int): List<File> =
        cacheDir(specimenId)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .mapNotNull { file ->
                if (isValidCachedImage(file)) file else {
                    file.delete()
                    null
                }
            }
            .sortedBy { it.name }

    private fun isValidCachedImage(file: File): Boolean {
        if (file.length() <= 0) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(32)
                val count = input.read(header)
                ApiClient.isSupportedImage(if (count > 0) header.copyOf(count) else byteArrayOf())
            }
        }.getOrDefault(false)
    }

    private fun targetFile(specimenId: Int, url: String, index: Int): File {
        val extension = url.substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "jpg")
            .lowercase()
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?: "jpg"
        return File(cacheDir(specimenId), "%03d_%08x.%s".format(index, url.hashCode(), extension))
    }

    private fun cacheDir(specimenId: Int): File =
        File(context.filesDir, "specimen_images/$specimenId").apply { mkdirs() }

    private fun completionMarker(specimenId: Int): File =
        File(cacheDir(specimenId), ".complete")

    private fun relativePath(file: File): String =
        file.relativeTo(context.filesDir).path

    private suspend fun <T> withSpeciesLock(specimenId: Int, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            val mutex = synchronized(inflight) {
                inflight.getOrPut(specimenId) { Mutex() }
            }
            mutex.withLock { block() }
        }
}
