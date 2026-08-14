package com.alicia.cloudstorage.phone.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMarkdownTextTest {

    @Test
    fun `parses headings lists and paragraph spacing`() {
        val blocks = parseAiMarkdownBlocks(
            """
            ## 能力说明

            1. **文件管理**：搜索和移动
            - 批量操作
            普通说明
            """.trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertEquals(AiMarkdownBlockType.HEADING, blocks[0].type)
        assertEquals(2, blocks[0].headingLevel)
        assertEquals(AiMarkdownBlockType.ORDERED_ITEM, blocks[1].type)
        assertEquals("1.", blocks[1].marker)
        assertTrue(blocks[1].spaceBefore)
        assertEquals(AiMarkdownBlockType.BULLET_ITEM, blocks[2].type)
        assertEquals(AiMarkdownBlockType.PARAGRAPH, blocks[3].type)
    }

    @Test
    fun `applies supported inline styles without leaving markers`() {
        val rendered = parseAiMarkdownInline("**粗体**、*斜体* 和 `code`")

        assertEquals("粗体、斜体 和 code", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(rendered.spanStyles.any { it.item.fontFamily != null })
    }

    @Test
    fun `keeps incomplete streaming markers literal`() {
        val rendered = parseAiMarkdownInline("正在生成 **文件管理")

        assertEquals("正在生成 **文件管理", rendered.text)
        assertFalse(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `does not interpret html as executable markup`() {
        val rendered = parseAiMarkdownInline("<b>文件</b> [打开](https://example.com)")

        assertEquals("<b>文件</b> [打开](https://example.com)", rendered.text)
        assertTrue(rendered.spanStyles.isEmpty())
    }
}
