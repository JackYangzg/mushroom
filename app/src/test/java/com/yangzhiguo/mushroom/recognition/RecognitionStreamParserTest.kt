package com.yangzhiguo.mushroom.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单元测试：RecognitionStreamParser
 *
 * 覆盖场景（设计文档 §3.2）：
 * 1. 纯思考过程（无候选）→ 仅 ThinkingChunk
 * 2. 思考 + 完整 JSON 数组（一次性）→ 拆分为 thinking + FinalCandidates
 * 3. 思考 + JSON 数组跨多个 delta 流式到达 → 全部累积后正确切分
 * 4. 截断的 JSON（流中断）→ 终态降级为 thinking，无 FinalCandidates
 * 5. 嵌套 JSON / 数组内含特殊字符 → 正确解析
 * 6. 重复 emit 同一 delta → 幂等（emittedFinal 后 no-op）
 * 7. 空 delta → 无事件
 * 8. 多次匹配首个完整数组 → 只 emit 第一个
 */
class RecognitionStreamParserTest {

    // --- 1. 纯思考 ---

    @Test
    fun `pure thinking emits incremental chunks`() {
        val parser = RecognitionStreamParser()

        val e1 = parser.consume("先看")
        val e2 = parser.consume("菌盖颜色")
        val e3 = parser.consume("，是深褐色。")

        assertEquals(listOf(StreamEvent.ThinkingChunk("先看")), e1)
        assertEquals(listOf(StreamEvent.ThinkingChunk("菌盖颜色")), e2)
        assertEquals(listOf(StreamEvent.ThinkingChunk("，是深褐色。")), e3)
    }

    // --- 2. 思考 + 数组一次性 ---

    @Test
    fun `thinking then array in single delta is split`() {
        val parser = RecognitionStreamParser()
        val delta = """
            初步判断为丝膜菌属。下面给出三个候选：
            [{"scientificName":"Cortinarius rubellus","confidence":0.42}]
        """.trimIndent()

        val events = parser.consume(delta)
        // 期待：先 emit 一段 thinking，最后 emit FinalCandidates（1 个）
        assertEquals(2, events.size)
        val first = events[0] as StreamEvent.ThinkingChunk
        assertTrue("应包含思考前缀", first.delta.contains("丝膜菌属"))
        val second = events[1] as StreamEvent.FinalCandidates
        assertEquals(1, second.candidates.size)
        assertEquals("Cortinarius rubellus", second.candidates[0].scientificName)
        assertEquals(0.42, second.candidates[0].confidence!!, 0.001)
    }

    // --- 3. 跨多个 delta 流式到达 ---

    @Test
    fun `array split across multiple deltas is assembled`() {
        val parser = RecognitionStreamParser()

        val d1 = parser.consume("分析菌褶排列，")
        val d2 = parser.consume("再观察菌柄。候选：")
        // 数组分两段到
        val d3 = parser.consume("""[{"scientificName":"Amanita""")
        val d4 = parser.consume(""" muscaria","commonName":"毒蝇伞"}]""")

        // 前两段是 thinking
        assertEquals(listOf(StreamEvent.ThinkingChunk("分析菌褶排列，")), d1)
        assertEquals(listOf(StreamEvent.ThinkingChunk("再观察菌柄。候选：")), d2)
        // d3 是 partial，没有完整数组，继续 thinking
        assertEquals(listOf(StreamEvent.ThinkingChunk("""[{"scientificName":"Amanita""")), d3)

        // d4 完成数组：emit accumulated thinking + FinalCandidates
        val finalEvents = d4
        assertEquals(2, finalEvents.size)
        val thinking = finalEvents[0] as StreamEvent.ThinkingChunk
        // 累积的 thinking 包含前面所有
        assertTrue(thinking.delta.contains("分析菌褶排列"))
        assertTrue(thinking.delta.contains("再观察菌柄"))
        val final = finalEvents[1] as StreamEvent.FinalCandidates
        assertEquals(1, final.candidates.size)
        assertEquals("Amanita muscaria", final.candidates[0].scientificName)
        assertEquals("毒蝇伞", final.candidates[0].commonName)
    }

    // --- 4. 截断 / 异常 JSON ---

    @Test
    fun `truncated array degrades to thinking only`() {
        val parser = RecognitionStreamParser()
        val delta = """某品种，[{"scientificName":"Boletus edulis"""  // 缺 ]

        val events = parser.consume(delta)
        // 找不到完整数组 → 整体作为 thinking
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.ThinkingChunk)
    }

    @Test
    fun `malformed array degrades to thinking only`() {
        val parser = RecognitionStreamParser()
        val delta = """候选 [this is not json] 完毕"""

        val events = parser.consume(delta)
        // 解析失败 → 整体作为 thinking
        assertEquals(1, events.size)
        assertTrue(events[0] is StreamEvent.ThinkingChunk)
    }

    // --- 5. 嵌套 / 特殊字符 ---

    @Test
    fun `array with multiple candidates and nested fields`() {
        val parser = RecognitionStreamParser()
        val delta = """
            最终判断：
            [
              {"scientificName":"Russula virescens","commonName":"绿菇","confidence":0.6,"reason":"菌盖有裂纹"},
              {"scientificName":"Russula cyanoxantha","commonName":"蓝黄红菇","confidence":0.3}
            ]
        """.trimIndent()

        val events = parser.consume(delta)
        val final = events.last { it is StreamEvent.FinalCandidates } as StreamEvent.FinalCandidates
        assertEquals(2, final.candidates.size)
        assertEquals("Russula virescens", final.candidates[0].scientificName)
        assertEquals("绿菇", final.candidates[0].commonName)
        assertEquals("菌盖有裂纹", final.candidates[0].reason)
    }

    // --- 6. 幂等 ---

    @Test
    fun `parser becomes no-op after FinalCandidates emitted`() {
        val parser = RecognitionStreamParser()
        parser.consume("""[{"scientificName":"X"}]""")
        val later = parser.consume("more text after candidates")
        assertTrue("emittedFinal 后应不再 emit 任何事件", later.isEmpty())
    }

    // --- 7. 空 delta ---

    @Test
    fun `empty delta emits nothing`() {
        val parser = RecognitionStreamParser()
        assertTrue(parser.consume("").isEmpty())
    }

    // --- 8. 多个数组时只取第一个 ---

    @Test
    fun `only the first complete array is emitted`() {
        val parser = RecognitionStreamParser()
        val delta = """
            [一阶段：快速看] 实际候选：
            [{"scientificName":"First"}]
            后置说明 [{"scientificName":"ShouldBeIgnored"}]
        """.trimIndent()

        val events = parser.consume(delta)
        val finals = events.filterIsInstance<StreamEvent.FinalCandidates>()
        assertEquals(1, finals.size)
        assertEquals("First", finals[0].candidates.single().scientificName)
    }
}
