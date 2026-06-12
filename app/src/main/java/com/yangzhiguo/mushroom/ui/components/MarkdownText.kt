package com.yangzhiguo.mushroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val blocks = markdown.trim().lines()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { rawLine ->
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
    }
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
