package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalUploadOutcomeTest {

    @Test
    fun `all uploaded files produce successful outcome`() {
        val summary = UploadBatchSummary(totalFiles = 3, successCount = 3)

        val outcome = summary.toOutcome()

        assertEquals(OperationOutcomeStatus.SUCCEEDED, outcome.status)
        assertEquals("已逐项上传 3 个文件。", outcome.message)
        assertTrue(summary.changedStorage)
    }

    @Test
    fun `mixed upload result preserves partial success`() {
        val summary = UploadBatchSummary(
            totalFiles = 4,
            successCount = 2,
            firstError = IllegalStateException("network"),
        )

        val outcome = summary.toOutcome("网络连接中断")

        assertEquals(OperationOutcomeStatus.PARTIALLY_SUCCEEDED, outcome.status)
        assertEquals("已上传 2 个文件，2 个未完成。\n网络连接中断", outcome.message)
        assertTrue(summary.changedStorage)
    }

    @Test
    fun `failed upload has no storage mutation`() {
        val summary = UploadBatchSummary(
            totalFiles = 1,
            firstError = IllegalStateException("network"),
        )

        val outcome = summary.toOutcome("网络连接中断")

        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals("上传没有完成：网络连接中断", outcome.message)
        assertFalse(summary.changedStorage)
    }

    @Test
    fun `empty folder tree creation is a successful mutation`() {
        val summary = UploadBatchSummary(createdFolderCount = 2)

        val outcome = summary.toOutcome()

        assertEquals(OperationOutcomeStatus.SUCCEEDED, outcome.status)
        assertEquals("文件夹结构已创建完成。", outcome.message)
        assertTrue(summary.changedStorage)
    }

    @Test
    fun `zero work never reports a successful upload`() {
        val outcome = UploadBatchSummary().toOutcome()

        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertEquals("上传失败。", outcome.message)
    }
}
