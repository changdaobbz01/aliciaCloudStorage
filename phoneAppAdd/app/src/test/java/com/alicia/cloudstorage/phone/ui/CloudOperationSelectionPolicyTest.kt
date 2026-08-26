package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOperationSelectionPolicyTest {
    @Test
    fun `batch node selection deduplicates and rejects server limit overflow`() {
        val normalized = validateBatchNodeIds(listOf(3L, 0L, -1L, 3L, 2L), "empty")

        assertTrue(normalized.isValid)
        assertEquals(listOf(3L, 2L), normalized.nodeIds)
        assertEquals(
            "单次最多处理 500 个项目。",
            validateBatchNodeIds(1L..501L, "empty").errorMessage,
        )
    }

    @Test
    fun `archive selection mirrors server root limit`() {
        assertEquals("empty", validateArchiveNodeIds(emptyList(), "empty").errorMessage)
        assertEquals(
            "单次最多打包下载 100 个项目，请减少选择后重试。",
            validateArchiveNodeIds(1L..101L).errorMessage,
        )
    }

    @Test
    fun `share save selection mirrors server selected item limit`() {
        assertEquals("请先选择要保存的分享内容。", validateShareSaveNodeIds(listOf(0L)).errorMessage)
        assertEquals(
            "单次最多保存 500 个分享项目，请减少选择后重试。",
            validateShareSaveNodeIds(1L..501L).errorMessage,
        )
    }

    @Test
    fun `share targets are deduplicated and capped`() {
        val nodes = listOf(storageNode(1), storageNode(1), storageNode(2))
        val normalized = validateShareNodes(nodes)

        assertTrue(normalized.isValid)
        assertEquals(listOf(1L, 2L), normalized.nodes.map(StorageNode::id))
        assertFalse(validateShareNodes(emptyList()).isValid)
        assertEquals(
            "单个分享最多包含 20 个项目。",
            validateShareNodes((1L..21L).map(::storageNode)).errorMessage,
        )
    }

    private fun storageNode(id: Long): StorageNode =
        StorageNode(
            id = id,
            parentId = null,
            name = "node-$id",
            type = StorageNodeType.FILE,
            size = 0L,
            extension = null,
            mimeType = null,
            updatedAt = "2026-08-26T00:00:00",
            deletedAt = null,
        )
}
