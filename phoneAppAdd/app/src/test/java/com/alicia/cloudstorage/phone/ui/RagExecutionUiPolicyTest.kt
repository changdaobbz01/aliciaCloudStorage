package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagExecutionResponse
import com.alicia.cloudstorage.phone.data.RagExecutionStepResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagExecutionUiPolicyTest {
    private val reference = AiChatExecutionReference(
        executionId = "execution-1",
        version = 0,
        status = "PENDING_CONFIRMATION",
        expiresAt = "2026-09-21T12:00:00Z",
    )
    private val draft = RagBackendActionDraft(
        status = "backend_action_ready",
        bridgeVersion = "action_bridge_v2",
        actionType = "rename",
        nextAction = "execute_backend_action",
        confirmedByUser = false,
        executableByBackend = true,
        authorizationRequired = true,
        method = "PUT",
        pathTemplate = "/api/storage/nodes/{nodeId}/rename",
        path = null,
        contentType = "application/json",
        pathVariables = mapOf("nodeId" to 7L),
        queryParameters = emptyMap(),
        body = mapOf("name" to "final.txt"),
        requiredClientFields = emptyList(),
        targetCandidate = null,
        message = null,
    )

    @Test
    fun `cloud reference never falls back to legacy local execution`() {
        assertEquals(
            RagExecutionRoute.CLOUD,
            RagExecutionClientPolicy.route(reference, draft, true, true),
        )
        assertEquals(
            RagExecutionRoute.BLOCKED,
            RagExecutionClientPolicy.route(reference, draft, false, true),
        )
    }

    @Test
    fun `legacy path requires explicit flag and absence of cloud reference`() {
        assertEquals(
            RagExecutionRoute.BLOCKED,
            RagExecutionClientPolicy.route(null, draft, true, true),
        )
        assertEquals(
            RagExecutionRoute.LEGACY_LOCAL,
            RagExecutionClientPolicy.route(null, draft, false, true),
        )
        assertEquals(
            RagExecutionRoute.BLOCKED,
            RagExecutionClientPolicy.route(null, draft, false, false),
        )
        assertEquals(
            RagExecutionRoute.CONTINUE_PLAN,
            RagExecutionClientPolicy.route(null, null, false, false),
        )
    }

    @Test
    fun `successful rename produces mutation signal without action payload`() {
        val response = execution(
            status = "SUCCEEDED",
            resultCode = "NODE_RENAMED",
            actionType = "NODE_RENAME",
            stepResult = mapOf("nodeId" to 7.0, "entityVersion" to 1.0),
        )

        assertTrue(response.isTerminal())
        assertEquals("已在云端完成重命名。", response.toProgressMessage())
        val signal = response.toFileMutationSignal()
        assertEquals("rename", signal?.actionType)
        assertEquals(listOf(7L), signal?.affectedNodeIds)
        assertEquals(AiChatFileMutationScope.FILES_ONLY, signal?.scope)
    }

    @Test
    fun `non terminal and failed responses do not emit mutation`() {
        val retrying = execution("RETRY_WAIT", null, "NODE_TRASH", null)
        assertFalse(retrying.isTerminal())
        assertNull(retrying.toFileMutationSignal())
        assertEquals("云端暂时没有完成，正在进行安全重试...", retrying.toProgressMessage())

        val failed = retrying.copy(status = "FAILED", errorCode = "resource_version_conflict")
        assertTrue(failed.isTerminal())
        assertNull(failed.toFileMutationSignal())
        assertEquals("云端任务未能完成（resource_version_conflict）。", failed.toProgressMessage())
    }

    @Test
    fun `successful batch move exposes all affected nodes and refreshes files`() {
        val response = execution(
            status = "SUCCEEDED",
            resultCode = "NODES_MOVED",
            actionType = "NODE_BATCH_MOVE",
            stepResult = mapOf(
                "items" to listOf(
                    mapOf("nodeId" to 7.0, "status" to "SUCCEEDED"),
                    mapOf("nodeId" to 8.0, "status" to "SUCCEEDED"),
                ),
            ),
        )

        assertEquals("已在云端完成批量移动。", response.toProgressMessage())
        val signal = response.toFileMutationSignal()
        assertEquals("collection.move", signal?.actionType)
        assertEquals(listOf(7L, 8L), signal?.affectedNodeIds)
        assertEquals(AiChatFileMutationScope.FILES_ONLY, signal?.scope)
    }

    @Test
    fun `waiting upload accepts only the typed client input contract`() {
        val waiting = execution(
            status = "WAITING_CLIENT_INPUT",
            resultCode = null,
            actionType = "UPLOAD_FILES",
            stepResult = mapOf(
                "inputType" to "UPLOAD_FILES",
                "parentId" to 77.0,
                "requiredFields" to listOf("files"),
            ),
        ).copy(version = 4)

        assertEquals(
            RagWaitingUploadInput("execution-1", 4, "step-1", 77),
            waiting.waitingUploadInput(),
        )
        assertNull(
            waiting.copy(
                steps = waiting.steps?.map { it.copy(result = mapOf("parentId" to 77.0)) },
            ).waitingUploadInput(),
        )
    }

    private fun execution(
        status: String,
        resultCode: String?,
        actionType: String,
        stepResult: Map<String, Any>?,
    ) = RagExecutionResponse(
        executionId = "execution-1",
        status = status,
        version = 3,
        summary = "safe summary",
        risk = "MEDIUM",
        expiresAt = null,
        confirmedAt = null,
        queuedAt = null,
        startedAt = null,
        finishedAt = null,
        resultCode = resultCode,
        result = null,
        errorCode = null,
        steps = listOf(
            RagExecutionStepResponse(
                stepId = "step-1",
                index = 0,
                actionType = actionType,
                status = status,
                attempts = 1,
                startedAt = null,
                finishedAt = null,
                errorCode = null,
                result = stepResult,
            ),
        ),
    )
}
