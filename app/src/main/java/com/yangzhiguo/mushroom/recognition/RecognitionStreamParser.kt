package com.yangzhiguo.mushroom.recognition

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 流式解析器：把 MiniMax M3 的逐 token delta.content 拆分为「思考过程」与
 * 「最终候选」两类事件。
 *
 * 解析规则（设计文档 §3.2）：
 *  - 累积 buffer 寻找首个能 JSON.parse 成功的 [...] 块
 *  - 该块之前的文本作为「思考过程」（一次 emit，整段）
 *  - 该块本身解析为 List<Candidate>，作为「最终候选」emit
 *  - 解析失败（截断 / 非法 JSON）→ 整段视为思考，不 emit 候选
 *  - 一旦 emit 过 FinalCandidates，parser 进入 no-op 状态
 *
 * 注意：parser 不处理 SSE 帧的拆包（`data: {...}\n\n`），那一步由
 * MiniMaxApiClient 在读取 InputStream 时完成。这里只接收纯文本 delta。
 */
class RecognitionStreamParser(
    private val json: Json = DefaultJson,
) {
    private val buffer = StringBuilder()
    private var emittedFinal = false

    /**
     * 消费一个 delta，返回 0..2 个事件：
     *  - 在未找到完整数组前：返回 [ThinkingChunk(delta)]
     *  - 找到完整数组的当帧：返回 [ThinkingChunk(累积思考), FinalCandidates(列表)]
     *  - emittedFinal 之后 / 空 delta：返回 emptyList()
     */
    fun consume(delta: String): List<StreamEvent> {
        if (emittedFinal || delta.isEmpty()) return emptyList()

        buffer.append(delta)
        val finalEvents = tryEmitFinalArray()
        if (finalEvents != null) return finalEvents

        return listOf(StreamEvent.ThinkingChunk(delta))
    }

    /** 供测试 / 上层收尾时调用：流被中断（无 finish_reason）时尝试一次终态解析。 */
    fun flush(): List<StreamEvent> = consume("")

    private fun tryEmitFinalArray(): List<StreamEvent>? {
        val text = buffer.toString()
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf('[', cursor)
            if (start < 0) return null
            val end = findMatchingBracket(text, start) ?: return null

            val candidateText = text.substring(start, end + 1)
            val parsed = parseCandidateArray(candidateText)
            if (parsed != null) {
                emittedFinal = true
                val events = mutableListOf<StreamEvent>()
                val beforeText = text.substring(0, start).trim()
                if (beforeText.isNotEmpty()) {
                    events.add(StreamEvent.ThinkingChunk(beforeText))
                }
                events.add(StreamEvent.FinalCandidates(parsed))
                return events
            }
            // 这个 [ 不是合法 JSON 数组，继续往后找
            cursor = end + 1
        }
        return null
    }

    /**
     * 从 start 位置开始，寻找与之匹配的 ']'（字符串内不计）。
     * 返回匹配的 ']' 的下标；找不到返回 null。
     */
    private fun findMatchingBracket(text: String, start: Int): Int? {
        require(text[start] == '[')
        var depth = 0
        var inString = false
        var escape = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (escape) { escape = false; i++; continue }
            if (c == '\\' && inString) { escape = true; i++; continue }
            if (c == '"') { inString = !inString; i++; continue }
            if (inString) { i++; continue }
            when (c) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }

    private fun parseCandidateArray(text: String): List<Candidate>? {
        return try {
            json.decodeFromString<List<Candidate>>(text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private val DefaultJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }
    }
}
