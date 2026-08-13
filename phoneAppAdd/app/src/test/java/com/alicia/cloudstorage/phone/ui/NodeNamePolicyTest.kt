package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeNamePolicyTest {
    @Test
    fun `validation mirrors server name constraints`() {
        assertEquals("文件.txt", validateNodeName("  文件.txt  ").normalizedName)
        assertEquals("名称不能为空。", validateNodeName("   ").errorMessage)
        assertEquals("名称不能包含斜杠。", validateNodeName("目录/文件").errorMessage)
        assertEquals(
            "名称长度不能超过 255 个字符。",
            validateNodeName("a".repeat(MAX_NODE_NAME_LENGTH + 1)).errorMessage,
        )
    }

    @Test
    fun `unchanged name is rejected but case changes remain allowed`() {
        assertFalse(validateNodeName("report.txt", "report.txt").isValid)
        assertTrue(validateNodeName("Report.txt", "report.txt").isValid)
    }

    @Test
    fun `file selection excludes final extension without locking it`() {
        assertEquals(NodeNameSelection(0, 10), initialNodeNameSelection("report.tar.gz", StorageNodeType.FILE))
        assertEquals(NodeNameSelection(0, 4), initialNodeNameSelection(".env", StorageNodeType.FILE))
        assertEquals(NodeNameSelection(0, 6), initialNodeNameSelection("folder", StorageNodeType.FOLDER))
    }
}
