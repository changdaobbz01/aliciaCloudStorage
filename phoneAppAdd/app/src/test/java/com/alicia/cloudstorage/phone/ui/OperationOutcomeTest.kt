package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationOutcomeTest {

    @Test
    fun `factories preserve success partial and failure semantics`() {
        assertEquals(OperationOutcomeStatus.SUCCEEDED, OperationOutcome.succeeded("完成").status)
        assertEquals(
            OperationOutcomeStatus.PARTIALLY_SUCCEEDED,
            OperationOutcome.partiallySucceeded("部分完成").status,
        )
        assertEquals(OperationOutcomeStatus.FAILED, OperationOutcome.failed("失败").status)
    }

    @Test
    fun `blank outcome messages are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationOutcome.failed("  ")
        }
    }
}
