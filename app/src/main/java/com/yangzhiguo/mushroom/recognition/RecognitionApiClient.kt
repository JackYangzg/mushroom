package com.yangzhiguo.mushroom.recognition

import kotlinx.coroutines.flow.Flow

enum class AiModelProvider(val displayName: String) {
    DOUBAO("豆包"),
    MINIMAX("MiniMax"),
}

interface RecognitionApiClient {
    fun streamRecognize(
        imageDataUrls: List<String>,
        userPrompt: String = "请综合分析这些图片中的同一株蘑菇。",
        systemPrompt: String = MiniMaxApiClient.DEFAULT_SYSTEM_PROMPT,
    ): Flow<StreamEvent>
}
