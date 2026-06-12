package com.yangzhiguo.mushroom.recognition

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionHistoryRecordTest {

    @Test
    fun recordRoundTripPreservesPhotosCandidatesAndThinking() {
        val original = RecognitionHistoryRecord(
            id = "history-1",
            createdAt = 1_718_000_000_000,
            photoPaths = listOf("/files/history/photo_1.jpg"),
            result = RecognitionResult(
                thinking = "观察到菌盖和菌褶特征。",
                candidates = listOf(
                    Candidate(
                        scientificName = "Amanita muscaria",
                        commonName = "毒蝇伞",
                        confidence = 0.82,
                        reason = "红色菌盖带白色鳞片",
                    ),
                ),
            ),
        )

        val restored = Json.decodeFromString<RecognitionHistoryRecord>(
            Json.encodeToString(RecognitionHistoryRecord.serializer(), original),
        )

        assertEquals(original, restored)
    }
}
