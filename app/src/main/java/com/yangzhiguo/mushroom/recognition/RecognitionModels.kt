package com.yangzhiguo.mushroom.recognition

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单个候选蘑菇（学名 + 可选俗名 + 可选置信度 + 可选理由）。
 *
 * 与 iflora / SpeciesEntity 的差异：这里 confidence 由大模型给出（0..1），
 * 而 SpeciesEntity 没有 confidence 字段（数据库里的是"确定性"事实）。
 */
@Serializable
data class Candidate(
    @SerialName("scientificName") val scientificName: String,
    @SerialName("commonName") val commonName: String? = null,
    @SerialName("confidence") val confidence: Double? = null,
    @SerialName("reason") val reason: String? = null,
)

/**
 * 一次识别会话的完整产物：思考过程 + 候选 list。
 * thinking 是已累积的完整文本（不是 delta），candidates 是末态列表。
 */
@Serializable
data class RecognitionResult(
    val thinking: String,
    val candidates: List<Candidate>,
)

/**
 * 流式解析器产出的逐 token / 逐事件。
 *  - [ThinkingChunk.delta] 追加到 UI 思考区
 *  - [FinalCandidates.candidates] 触发本地检索，停止追加 thinking
 *  - [StreamError] 是终止信号
 */
sealed class StreamEvent {
    data class ThinkingChunk(val delta: String) : StreamEvent()
    data class FinalCandidates(val candidates: List<Candidate>) : StreamEvent()
    data class StreamError(val cause: Throwable) : StreamEvent()
}

/**
 * 大模型返回的 SSE 帧解析后的最小单元。
 *  - content 为 null 时表示该帧是 finish_reason 之类控制信息
 *  - finishReason 为 "stop" 时是正常终止
 */
data class ParsedFrame(
    val content: String? = null,
    val finishReason: String? = null,
)

/**
 * 调用方状态机用：UI 关心的中间态。
 *
 * 状态机详见 design_doc §2.2。
 *  - IDLE: 初始 / 复位
 *  - UPLOADING: 图片压缩上传中
 *  - RECOGNIZING: 正在接收 SSE 流
 *  - RECOGNIZED: 流结束，等待用户选候选
 *  - LOCAL_HIT / LOCAL_MISS / RENDERED / ERROR: 详情页相关
 *  - CANCELED: 用户主动取消
 */
sealed class RecognitionState {
    data object Idle : RecognitionState()
    data object Uploading : RecognitionState()
    data class Recognizing(
        val thinkingSoFar: String = "",
        val isThinkingExpanded: Boolean = true,
    ) : RecognitionState()
    data class Recognized(
        val result: RecognitionResult,
        val photoUri: String? = null,
    ) : RecognitionState()
    data object LocalHit : RecognitionState()
    data object LocalMiss : RecognitionState()
    data object Rendered : RecognitionState()
    data class Error(val message: String, val retryable: Boolean = true) : RecognitionState()
    data object Canceled : RecognitionState()
}
