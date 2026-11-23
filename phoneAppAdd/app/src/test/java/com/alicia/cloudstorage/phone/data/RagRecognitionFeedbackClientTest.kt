package com.alicia.cloudstorage.phone.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagRecognitionFeedbackClientTest {
    private val traceId = "11111111-2222-3333-4444-555555555555"

    @Test
    fun requiresBothCapabilityAndAValidTraceHeader() {
        assertNull(parseFeedbackHeaders(traceId, null))
        assertNull(parseFeedbackHeaders("unknown", "category,candidate"))
        assertNull(parseFeedbackHeaders(traceId, "unknown"))
        assertEquals(traceId, parseFeedbackHeaders(traceId, "category")?.traceId)
        assertFalse(checkNotNull(parseFeedbackHeaders(traceId, "category")).candidateEnabled)
        assertTrue(checkNotNull(parseFeedbackHeaders(traceId, "category,candidate")).candidateEnabled)
    }

    @Test
    fun categoryPayloadHasOnlyTraceIdAndCategory() {
        val gson = Gson()
        val request = RagCategoryFeedbackRequest(traceId, RagFeedbackCategory.WRONG_TARGET.name)
        assertEquals(
            setOf("traceId", "category"),
            gson.toJsonTree(request).asJsonObject.keySet(),
        )
        assertNull(gson.fromJson(
            """{"feedback":{"traceId":"$traceId","candidateEnabled":true}}""",
            RagAssistantPlanResponse::class.java,
        ).feedback)
    }
}
