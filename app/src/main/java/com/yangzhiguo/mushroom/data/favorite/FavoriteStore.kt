package com.yangzhiguo.mushroom.data.favorite

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏独立于 Room 主库保存，主库升级或 destructive rebuild 不会清空收藏。
 */
@Singleton
class FavoriteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private val initialData = readData()
    private val mutableIds = MutableStateFlow(initialData.ids)

    val ids: StateFlow<Set<Int>> = mutableIds.asStateFlow()

    fun isFavorite(mushroomId: Int): Boolean = mushroomId in mutableIds.value

    suspend fun toggle(mushroomId: Int): Boolean = lock.withLock {
        val updated = mutableIds.value.toMutableSet()
        val isFavorite = if (mushroomId in updated) {
            updated.remove(mushroomId)
            false
        } else {
            updated.add(mushroomId)
            true
        }
        withContext(Dispatchers.IO) {
            writeData(FavoriteData(updated, legacyImported = true))
        }
        mutableIds.value = updated
        isFavorite
    }

    /**
     * Room 打开前调用，把旧主库的 `is_favorite` 数据一次性迁入 JSON。
     */
    fun importLegacyFavorites(mushroomIds: Set<Int>) {
        synchronized(this) {
            val current = readData()
            if (current.legacyImported) return

            val merged = current.ids + mushroomIds
            writeData(FavoriteData(merged, legacyImported = true))
            mutableIds.value = merged
        }
    }

    private fun readData(): FavoriteData {
        if (!file.isFile) return FavoriteData()
        return runCatching {
            val json = JSONObject(file.readText())
            val values = json.optJSONArray(KEY_MUSHROOM_IDS) ?: JSONArray()
            val ids = buildSet {
                for (index in 0 until values.length()) {
                    val id = values.optInt(index, INVALID_ID)
                    if (id != INVALID_ID) add(id)
                }
            }
            FavoriteData(
                ids = ids,
                legacyImported = json.optBoolean(KEY_LEGACY_IMPORTED, false),
            )
        }.getOrDefault(FavoriteData())
    }

    private fun writeData(data: FavoriteData) {
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_LEGACY_IMPORTED, data.legacyImported)
            .put(KEY_MUSHROOM_IDS, JSONArray(data.ids.sorted()))

        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeText(json.toString())
        check(temporary.renameTo(file) || run {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }) {
            "Unable to persist favorites"
        }
    }

    private data class FavoriteData(
        val ids: Set<Int> = emptySet(),
        val legacyImported: Boolean = false,
    )

    companion object {
        internal const val FILE_NAME = "favorites.json"
        private const val FORMAT_VERSION = 1
        private const val INVALID_ID = Int.MIN_VALUE
        private const val KEY_VERSION = "version"
        private const val KEY_LEGACY_IMPORTED = "legacyImported"
        private const val KEY_MUSHROOM_IDS = "mushroomIds"
    }
}
