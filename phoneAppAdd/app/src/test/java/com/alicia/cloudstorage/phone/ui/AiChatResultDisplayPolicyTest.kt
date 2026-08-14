package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatResultDisplayPolicyTest {

    @Test
    fun `ordinary results collapse only from five items`() {
        val section = section(mode = AiChatResultMode.SEARCH_RESULTS)

        assertFalse(AiChatResultDisplayPolicy.canCollapse(section, 4))
        assertTrue(AiChatResultDisplayPolicy.canCollapse(section, 5))
        assertEquals(3, AiChatResultDisplayPolicy.visibleItemCount(section, 12, expanded = false))
        assertEquals(12, AiChatResultDisplayPolicy.visibleItemCount(section, 12, expanded = true))
    }

    @Test
    fun `candidate selection never hides choices`() {
        val section = section(mode = AiChatResultMode.CANDIDATE_SELECTION)

        assertFalse(AiChatResultDisplayPolicy.canCollapse(section, 8))
        assertEquals(8, AiChatResultDisplayPolicy.visibleItemCount(section, 8, expanded = false))
    }

    @Test
    fun `exact complete result uses remaining wording`() {
        val section = section(totalCount = 12, hasMore = false)

        assertEquals("全部云盘 · 共 12 项", AiChatResultDisplayPolicy.countLabel(section, 12))
        assertEquals("展开其余 9 项", AiChatResultDisplayPolicy.toggleLabel(section, 12, expanded = false))
        assertEquals("收起", AiChatResultDisplayPolicy.toggleLabel(section, 12, expanded = true))
        assertNull(AiChatResultDisplayPolicy.partialResultLabel(section, 12))
    }

    @Test
    fun `partial result never claims all hidden items are local`() {
        val section = section(totalCount = 80, hasMore = true)

        assertEquals("全部云盘 · 共 80 项", AiChatResultDisplayPolicy.countLabel(section, 50))
        assertEquals("展开另外 47 项", AiChatResultDisplayPolicy.toggleLabel(section, 50, expanded = false))
        assertEquals("当前展示前 50 项，共 80 项", AiChatResultDisplayPolicy.partialResultLabel(section, 50))
    }

    @Test
    fun `unknown total reports only displayed items`() {
        val section = section(totalCount = null, hasMore = null)

        assertEquals("全部云盘 · 已展示 6 项", AiChatResultDisplayPolicy.countLabel(section, 6))
        assertEquals("展开另外 3 项", AiChatResultDisplayPolicy.toggleLabel(section, 6, expanded = false))
    }

    private fun section(
        mode: AiChatResultMode = AiChatResultMode.SEARCH_RESULTS,
        totalCount: Long? = null,
        hasMore: Boolean? = null,
    ) = AiChatResultSection(
        mode = mode,
        title = "文档结果",
        contextLabel = "全部云盘",
        totalCount = totalCount,
        hasMore = hasMore,
    )
}
