package com.yangzhiguo.mushroom.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `parses markdown table as a table block`() {
        val blocks = parseMarkdownBlocks(
            """
            ## 观察结果
            | 特征 | 观察 |
            | --- | --- |
            | 菌盖 | **褐色** |
            | 菌褶 | 白色 |
            """.trimIndent(),
        )

        assertEquals(MarkdownBlock.Line("## 观察结果"), blocks[0])
        val table = blocks[1] as MarkdownBlock.Table
        assertEquals(listOf("特征", "观察"), table.headers)
        assertEquals(
            listOf(
                listOf("菌盖", "**褐色**"),
                listOf("菌褶", "白色"),
            ),
            table.rows,
        )
    }

    @Test
    fun `pads short table rows to header width`() {
        val table = parseMarkdownBlocks(
            """
            | 特征 | 观察 | 证据 |
            | :--- | ---: | :---: |
            | 菌盖 | 褐色 |
            """.trimIndent(),
        ).single() as MarkdownBlock.Table

        assertEquals(listOf("菌盖", "褐色", ""), table.rows.single())
    }

    @Test
    fun `plain text containing pipes stays a line`() {
        val blocks = parseMarkdownBlocks("置信度 A | B")

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is MarkdownBlock.Line)
    }
}
