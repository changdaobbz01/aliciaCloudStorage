package com.alicia.cloudstorage.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagActionBridgeValidatorTest {
    private val validator = RagActionBridgeValidator()

    @Test
    fun `allows confirmed rename draft on known route`() {
        val validation = validator.validate(
            draft(
                actionType = "rename",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/{nodeId}/rename",
                body = mapOf("name" to "report-final.pdf"),
            ),
        )

        assertTrue(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.ALLOWED, validation.code)
    }

    @Test
    fun `normalizes concrete node paths before allowlist matching`() {
        val validation = validator.validate(
            draft(
                actionType = "delete",
                method = "DELETE",
                pathTemplate = null,
                path = "/api/storage/nodes/42",
                body = emptyMap(),
            ),
        )

        assertTrue(validation.allowed)
    }

    @Test
    fun `requires user confirmation before backend execution`() {
        val validation = validator.validate(
            draft(
                actionType = "delete",
                method = "DELETE",
                pathTemplate = "/api/storage/nodes/{nodeId}",
                confirmedByUser = false,
            ),
        )

        assertFalse(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.CONFIRMATION_REQUIRED, validation.code)
    }

    @Test
    fun `rejects route mismatches for supported actions`() {
        val validation = validator.validate(
            draft(
                actionType = "rename",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/move",
                body = mapOf("name" to "report-final.pdf"),
            ),
        )

        assertFalse(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.ROUTE_NOT_ALLOWED, validation.code)
    }

    @Test
    fun `rejects unsupported action types`() {
        val validation = validator.validate(
            draft(
                actionType = "collection.rename_add_prefix",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/rename",
            ),
        )

        assertFalse(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.UNSUPPORTED_ACTION, validation.code)
    }

    @Test
    fun `rejects sensitive backend fields`() {
        val validation = validator.validate(
            draft(
                actionType = "share",
                method = "POST",
                pathTemplate = "/api/share-links",
                body = mapOf(
                    "nodeIds" to listOf(1L),
                    "storagePath" to "/private/object-key",
                ),
            ),
        )

        assertFalse(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.SENSITIVE_FIELD_PRESENT, validation.code)
    }

    @Test
    fun `rejects unexpected body fields for allowed action`() {
        val validation = validator.validate(
            draft(
                actionType = "rename",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/{nodeId}/rename",
                body = mapOf(
                    "name" to "report-final.pdf",
                    "nodeIds" to listOf(42L),
                ),
            ),
        )

        assertFalse(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.PARAMETER_NOT_ALLOWED, validation.code)
    }

    @Test
    fun `rejects unexpected query parameters for allowed action`() {
        val validation = validator.validate(
            draft(
                actionType = "delete",
                method = "DELETE",
                pathTemplate = "/api/storage/nodes/{nodeId}",
                queryParameters = mapOf("parentId" to 9L),
            ),
        )

        assertFalse(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.PARAMETER_NOT_ALLOWED, validation.code)
    }

    @Test
    fun `reports upload target as client input required`() {
        val validation = validator.validate(
            draft(
                actionType = "upload_target",
                status = "client_action_required",
                executableByBackend = false,
                method = "POST",
                pathTemplate = "/api/storage/files",
                requiredClientFields = listOf("file"),
            ),
        )

        assertFalse(validation.allowed)
        assertEquals(RagActionBridgeValidationCode.CLIENT_INPUT_REQUIRED, validation.code)
    }

    private fun draft(
        actionType: String,
        method: String,
        pathTemplate: String?,
        path: String? = null,
        status: String = "backend_action_ready",
        confirmedByUser: Boolean = true,
        executableByBackend: Boolean = true,
        requiredClientFields: List<String>? = emptyList(),
        body: Map<String, Any>? = emptyMap(),
        pathVariables: Map<String, Any>? = null,
        queryParameters: Map<String, Any>? = emptyMap(),
    ): RagBackendActionDraft {
        val resolvedPathVariables = pathVariables ?: when (actionType) {
            "rename",
            "delete",
            -> mapOf("nodeId" to 42L)
            else -> emptyMap()
        }

        return RagBackendActionDraft(
            status = status,
            bridgeVersion = "action_bridge_v1",
            actionType = actionType,
            nextAction = "handoff_to_backend",
            confirmedByUser = confirmedByUser,
            executableByBackend = executableByBackend,
            authorizationRequired = true,
            method = method,
            pathTemplate = pathTemplate,
            path = path,
            contentType = "application/json",
            pathVariables = resolvedPathVariables,
            queryParameters = queryParameters,
            body = body,
            requiredClientFields = requiredClientFields,
            targetCandidate = null,
            message = null,
        )
    }
}
