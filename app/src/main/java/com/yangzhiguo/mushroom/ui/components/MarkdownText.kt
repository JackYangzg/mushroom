package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseMarkdownBlocks(markdown)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Line -> MarkdownLine(block.text)
                is MarkdownBlock.Table -> MarkdownTable(block)
            }
        }
    }
}

@Composable
private fun MarkdownLine(rawLine: String) {
    val line = rawLine.trimEnd()
    when {
        line.isBlank() -> Unit
        line.startsWith("### ") -> Text(
            inlineMarkdown(line.removePrefix("### ")),
            style = MaterialTheme.typography.titleMedium,
        )
        line.startsWith("## ") -> Text(
            inlineMarkdown(line.removePrefix("## ")),
            style = MaterialTheme.typography.titleLarge,
        )
        line.startsWith("# ") -> Text(
            inlineMarkdown(line.removePrefix("# ")),
            style = MaterialTheme.typography.headlineSmall,
        )
        line.startsWith("- ") || line.startsWith("* ") -> Text(
            buildAnnotatedString {
                append("• ")
                append(inlineMarkdown(line.drop(2)))
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        line.matches(Regex("""\d+\.\s+.*""")) -> Text(
            inlineMarkdown(line),
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> Text(
            inlineMarkdown(line),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        MarkdownTableRow(table.headers, borderColor, isHeader = true)
        table.rows.forEach { MarkdownTableRow(it, borderColor, isHeader = false) }
    }
}

@Composable
private fun MarkdownTableRow(cells: List<String>, borderColor: Color, isHeader: Boolean) {
    Row {
        cells.forEach { cell ->
            Text(
                text = inlineMarkdown(cell),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .width(160.dp)
                    .border(0.5.dp, borderColor)
                    .background(
                        if (isHeader) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Line(val text: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.trim().lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val headers = parseTableCells(lines[index])
        if (headers.isNotEmpty() &&
            index + 1 < lines.size &&
            isTableSeparator(lines[index + 1])
        ) {
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                val cells = parseTableCells(lines[index])
                rows += List(headers.size) { column -> cells.getOrElse(column) { "" } }
                index++
            }
            blocks += MarkdownBlock.Table(headers, rows)
        } else {
            blocks += MarkdownBlock.Line(lines[index])
            index++
        }
    }
    return blocks
}

private fun isTableSeparator(line: String): Boolean {
    val cells = parseTableCells(line)
    return cells.isNotEmpty() && cells.all { it.matches(Regex(""":?-{3,}:?""")) }
}

private fun parseTableCells(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    if (!trimmed.contains('|')) return emptyList()
    return trimmed.split('|').map(String::trim)
}

internal fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val token = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)""")
    var cursor = 0
    token.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val value = match.value
        when {
            value.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(value.removeSurrounding("**"))
            }
            value.startsWith("`") -> withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Medium,
                    background = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.07f),
                ),
            ) {
                append(value.removeSurrounding("`"))
            }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(value.removeSurrounding("*"))
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
