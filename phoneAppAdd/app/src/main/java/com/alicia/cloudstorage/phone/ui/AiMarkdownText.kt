package com.alicia.cloudstorage.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class AiMarkdownBlockType {
    PARAGRAPH,
    HEADING,
    ORDERED_ITEM,
    BULLET_ITEM,
}

internal data class AiMarkdownBlock(
    val type: AiMarkdownBlockType,
    val content: String,
    val marker: String? = null,
    val headingLevel: Int = 0,
    val spaceBefore: Boolean = false,
)

private val headingPattern = Regex("^(#{1,3})\\s+(.+)$")
private val orderedItemPattern = Regex("^\\s*(\\d+)[.)]\\s+(.+)$")
private val bulletItemPattern = Regex("^\\s*[-+*]\\s+(.+)$")

internal fun parseAiMarkdownBlocks(markdown: String): List<AiMarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()

    val blocks = mutableListOf<AiMarkdownBlock>()
    var pendingSpace = false
    markdown.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
        if (line.isBlank()) {
            pendingSpace = blocks.isNotEmpty()
            return@forEach
        }

        val heading = headingPattern.matchEntire(line)
        val ordered = orderedItemPattern.matchEntire(line)
        val bullet = bulletItemPattern.matchEntire(line)
        val block = when {
            heading != null -> AiMarkdownBlock(
                type = AiMarkdownBlockType.HEADING,
                content = heading.groupValues[2],
                headingLevel = heading.groupValues[1].length,
                spaceBefore = pendingSpace,
            )

            ordered != null -> AiMarkdownBlock(
                type = AiMarkdownBlockType.ORDERED_ITEM,
                content = ordered.groupValues[2],
                marker = "${ordered.groupValues[1]}.",
                spaceBefore = pendingSpace,
            )

            bullet != null -> AiMarkdownBlock(
                type = AiMarkdownBlockType.BULLET_ITEM,
                content = bullet.groupValues[1],
                marker = "\u2022",
                spaceBefore = pendingSpace,
            )

            else -> AiMarkdownBlock(
                type = AiMarkdownBlockType.PARAGRAPH,
                content = line,
                spaceBefore = pendingSpace,
            )
        }
        blocks += block
        pendingSpace = false
    }
    return blocks
}

internal fun parseAiMarkdownInline(source: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < source.length) {
        if (source[index] == '\\' && index + 1 < source.length) {
            append(source[index + 1])
            index += 2
            continue
        }

        val token = inlineTokenAt(source, index)
        if (token == null) {
            append(source[index])
            index++
            continue
        }

        val closingIndex = source.indexOf(token.marker, index + token.marker.length)
        if (closingIndex <= index + token.marker.length) {
            append(token.marker)
            index += token.marker.length
            continue
        }

        val content = source.substring(index + token.marker.length, closingIndex)
        pushStyle(token.style)
        append(content)
        pop()
        index = closingIndex + token.marker.length
    }
}

private data class AiMarkdownInlineToken(
    val marker: String,
    val style: SpanStyle,
)

private fun inlineTokenAt(source: String, index: Int): AiMarkdownInlineToken? = when {
    source.startsWith("**", index) -> AiMarkdownInlineToken("**", SpanStyle(fontWeight = FontWeight.Bold))
    source.startsWith("__", index) -> AiMarkdownInlineToken("__", SpanStyle(fontWeight = FontWeight.Bold))
    source[index] == '`' -> AiMarkdownInlineToken(
        marker = "`",
        style = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = Color(0xFFF0F3F8),
        ),
    )

    source[index] == '*' -> AiMarkdownInlineToken("*", SpanStyle(fontStyle = FontStyle.Italic))
    source[index] == '_' -> AiMarkdownInlineToken("_", SpanStyle(fontStyle = FontStyle.Italic))
    else -> null
}

@Composable
internal fun AiMarkdownText(
    markdown: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseAiMarkdownBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            if (block.spaceBefore) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            when (block.type) {
                AiMarkdownBlockType.PARAGRAPH -> AiMarkdownBlockText(
                    block = block,
                    color = color,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )

                AiMarkdownBlockType.HEADING -> AiMarkdownBlockText(
                    block = block,
                    color = color,
                    fontSize = if (block.headingLevel == 1) 15.sp else fontSize,
                    lineHeight = lineHeight,
                    fontWeight = FontWeight.Bold,
                )

                AiMarkdownBlockType.ORDERED_ITEM,
                AiMarkdownBlockType.BULLET_ITEM,
                -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                ) {
                    Text(
                        text = block.marker.orEmpty(),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.width(22.dp),
                    )
                    AiMarkdownBlockText(
                        block = block,
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiMarkdownBlockText(
    block: AiMarkdownBlock,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = parseAiMarkdownInline(block.content),
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}
