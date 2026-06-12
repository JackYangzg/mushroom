package com.yangzhiguo.mushroom.recognition

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RecognitionHistoryRecord(
    val id: String,
    val createdAt: Long,
    val photoPaths: List<String>,
    val result: RecognitionResult,
)

@Singleton
class RecognitionHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val mutex = Mutex()
    private val historyRoot = File(context.filesDir, "recognition_history")
    private val indexFile = File(historyRoot, "records.json")
    private val _records = MutableStateFlow(loadRecords())

    val records: StateFlow<List<RecognitionHistoryRecord>> = _records.asStateFlow()

    suspend fun save(
        result: RecognitionResult,
        photoUris: List<String>,
    ): RecognitionHistoryRecord = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
            val recordDir = File(historyRoot, id).apply { mkdirs() }
            val photoPaths = photoUris.mapIndexedNotNull { index, rawUri ->
                copyPhoto(rawUri, File(recordDir, "photo_${index + 1}.jpg"))
            }
            val record = RecognitionHistoryRecord(
                id = id,
                createdAt = System.currentTimeMillis(),
                photoPaths = photoPaths,
                result = result,
            )
            val updated = (listOf(record) + _records.value).sortedByDescending { it.createdAt }
            writeRecords(updated)
            _records.value = updated
            record
        }
    }

    fun find(id: String): RecognitionHistoryRecord? =
        _records.value.firstOrNull { it.id == id }

    private fun copyPhoto(rawUri: String, destination: File): String? {
        return runCatching {
            val uri = Uri.parse(rawUri)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use(input::copyTo)
            } ?: return null
            destination.takeIf { it.length() > 0 }?.absolutePath
        }.onFailure {
            destination.delete()
            Log.w(TAG, "Unable to preserve recognition photo: ${it.message}")
        }.getOrNull()
    }

    private fun loadRecords(): List<RecognitionHistoryRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(
                ListSerializer(RecognitionHistoryRecord.serializer()),
                indexFile.readText(),
            ).sortedByDescending { it.createdAt }
        }.onFailure {
            Log.w(TAG, "Unable to read recognition history: ${it.message}")
        }.getOrDefault(emptyList())
    }

    private fun writeRecords(records: List<RecognitionHistoryRecord>) {
        historyRoot.mkdirs()
        val tempFile = File(historyRoot, "records.json.tmp")
        tempFile.writeText(
            json.encodeToString(
                ListSerializer(RecognitionHistoryRecord.serializer()),
                records,
            ),
        )
        check(tempFile.renameTo(indexFile) || run {
            tempFile.copyTo(indexFile, overwrite = true)
            tempFile.delete()
        }) {
            "Unable to persist recognition history"
        }
    }

    private companion object {
        const val TAG = "RecognitionHistory"
    }
}
