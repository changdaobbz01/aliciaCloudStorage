package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatClientUploadTrackerTest {
    private val request = AiChatClientUploadRequest(
        parentId = 7L,
        targetName = "资料",
    )

    @Test
    fun `only one picker or upload operation can be pending`() {
        val tracker = AiChatClientUploadTracker()
        val first = tracker.start(messageId = 20L, request = request)

        assertNull(tracker.start(messageId = 21L, request = request))
        assertTrue(tracker.cancel(first!!))
        assertEquals(2L, tracker.start(messageId = 21L, request = request)!!.operationId)
    }

    @Test
    fun `selection and completion callbacks are idempotent`() {
        val tracker = AiChatClientUploadTracker()
        val launch = tracker.start(messageId = 20L, request = request)!!

        assertTrue(tracker.markSelected(launch, executionMessageId = 30L))
        assertFalse(tracker.markSelected(launch, executionMessageId = 31L))
        assertEquals(30L, tracker.complete(launch))
        assertNull(tracker.complete(launch))
        assertFalse(tracker.cancel(launch))
    }

    @Test
    fun `stale callback cannot complete a newer operation`() {
        val tracker = AiChatClientUploadTracker()
        val stale = tracker.start(messageId = 20L, request = request)!!
        tracker.clear()
        val current = tracker.start(messageId = 20L, request = request)!!

        assertFalse(tracker.markSelected(stale, executionMessageId = 30L))
        assertTrue(tracker.markSelected(current, executionMessageId = 31L))
        assertEquals(31L, tracker.complete(current))
    }
}
