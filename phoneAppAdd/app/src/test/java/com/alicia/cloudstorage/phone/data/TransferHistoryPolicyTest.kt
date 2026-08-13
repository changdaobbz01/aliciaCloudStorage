package com.alicia.cloudstorage.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferHistoryPolicyTest {
    @Test
    fun `scope is stable and account isolated`() {
        assertEquals(
            transferHistoryScope("https://example.com/", 7L),
            transferHistoryScope("https://example.com", 7L),
        )
        assertNotEquals(
            transferHistoryScope("https://example.com", 7L),
            transferHistoryScope("https://example.com", 8L),
        )
    }

    @Test
    fun `active transfer becomes failed after process restoration`() {
        val restored = record(status = "RUNNING").sanitizeAfterRestore(nowMillis = 99L)

        requireNotNull(restored)
        assertEquals("FAILED", restored.status)
        assertTrue(restored.errorMessage.orEmpty().contains("应用上次退出"))
    }

    @Test
    fun `unsafe uri and invalid ids are removed`() {
        val restored = record(
            status = "FAILED",
            sourceNodeIds = listOf(-1L, 2L, 2L),
            sourceUri = "file:///private/source",
            destinationUri = "content://downloads/item/1",
        ).sanitizeAfterRestore(nowMillis = 99L)

        requireNotNull(restored)
        assertEquals(listOf(2L), restored.sourceNodeIds)
        assertNull(restored.sourceUri)
        assertEquals("content://downloads/item/1", restored.destinationUri)
    }

    @Test
    fun `unknown transfer values are rejected`() {
        assertNull(record(status = "MAGIC").sanitizeAfterRestore(nowMillis = 99L))
    }

    @Test
    fun `persistence normalization bounds untrusted text without interrupting active work`() {
        val normalized = record(status = "RUNNING").copy(
            title = "x".repeat(400),
            errorMessage = "e".repeat(2_000),
            sourceUri = "file:///private/source",
        ).sanitizeForPersistence(nowMillis = 99L)

        requireNotNull(normalized)
        assertEquals("RUNNING", normalized.status)
        assertEquals(255, normalized.title.length)
        assertEquals(1_024, normalized.errorMessage?.length)
        assertNull(normalized.sourceUri)
    }

    private fun record(
        status: String,
        sourceNodeIds: List<Long> = listOf(1L),
        sourceUri: String? = null,
        destinationUri: String? = null,
    ): StoredTransferRecord =
        StoredTransferRecord(
            scopeKey = "scope",
            taskId = 1L,
            kind = "DOWNLOAD",
            itemKind = "FILE",
            title = "report.pdf",
            status = status,
            sourceNodeIds = sourceNodeIds,
            sourceUri = sourceUri,
            destinationUri = destinationUri,
            transferredBytes = 10L,
            totalBytes = 100L,
            progressPercent = 10,
            locationLabel = "Downloads",
            errorMessage = null,
            createdAtMillis = 1L,
        )
}
