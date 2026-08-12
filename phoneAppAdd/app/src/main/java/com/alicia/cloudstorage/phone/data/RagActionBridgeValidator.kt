package com.alicia.cloudstorage.phone.data

internal class RagActionBridgeValidator(
    private val allowedRoutes: Map<String, RagAllowedBridgeRoute> = defaultAllowedRoutes,
) {
    fun validate(draft: RagBackendActionDraft?): RagActionBridgeValidation {
        if (draft == null || draft.status.equalsNormalized("not_requested")) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.MISSING_DRAFT,
                message = "没有可校验的执行草稿。",
            )
        }

        if (draft.requiredClientFields.orEmpty().isNotEmpty()) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.CLIENT_INPUT_REQUIRED,
                message = "需要客户端补充文件或目标信息后才能继续。",
            )
        }

        val actionType = draft.actionType?.trim().orEmpty()
        val allowedRoute = allowedRoutes[actionType] ?: return RagActionBridgeValidation.rejected(
            code = RagActionBridgeValidationCode.UNSUPPORTED_ACTION,
            message = "移动端暂不支持该执行动作。",
        )

        if (draft.containsSensitiveFields()) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.SENSITIVE_FIELD_PRESENT,
                message = "执行草稿包含移动端不应接收的敏感字段。",
            )
        }

        if (!draft.status.equalsNormalized("backend_action_ready")) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.NOT_READY,
                message = "执行草稿尚未进入可提交状态。",
            )
        }

        if (draft.confirmedByUser != true) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.CONFIRMATION_REQUIRED,
                message = "需要用户最终确认后才能提交。",
            )
        }

        if (draft.executableByBackend != true) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.NOT_BACKEND_EXECUTABLE,
                message = "该动作不能作为后端直执行请求提交。",
            )
        }

        if (draft.authorizationRequired == false) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.AUTHORIZATION_REQUIRED,
                message = "受控文件操作必须保留用户鉴权。",
            )
        }

        val method = draft.method?.trim()?.uppercase().orEmpty()
        val path = (draft.pathTemplate ?: draft.path).normalizedBridgePath()
        if (method != allowedRoute.method || path != allowedRoute.pathTemplate) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.ROUTE_NOT_ALLOWED,
                message = "执行草稿的请求方法或路径不在移动端 allowlist 中。",
            )
        }

        val unexpectedField = draft.unexpectedBridgeField(allowedRoute)
        if (unexpectedField != null) {
            return RagActionBridgeValidation.rejected(
                code = RagActionBridgeValidationCode.PARAMETER_NOT_ALLOWED,
                message = "执行草稿包含该动作不允许提交的字段：$unexpectedField。",
            )
        }

        return RagActionBridgeValidation(
            allowed = true,
            code = RagActionBridgeValidationCode.ALLOWED,
            message = "执行草稿已通过移动端 allowlist 校验。",
        )
    }

    private fun RagBackendActionDraft.containsSensitiveFields(): Boolean =
        listOf(pathVariables, queryParameters, body).any(::containsSensitiveFieldKey)

    private fun containsSensitiveFieldKey(value: Any?): Boolean =
        when (value) {
            is Map<*, *> -> value.any { (key, childValue) ->
                key?.toString()?.trim()?.lowercase() in sensitiveFieldKeys ||
                    containsSensitiveFieldKey(childValue)
            }
            is Iterable<*> -> value.any(::containsSensitiveFieldKey)
            is Array<*> -> value.any(::containsSensitiveFieldKey)
            else -> false
        }

    private fun RagBackendActionDraft.unexpectedBridgeField(route: RagAllowedBridgeRoute): String? =
        pathVariables.unexpectedKey(route.pathVariableKeys)?.let { "pathVariables.$it" }
            ?: queryParameters.unexpectedKey(route.queryParameterKeys)?.let { "queryParameters.$it" }
            ?: body.unexpectedKey(route.bodyKeys)?.let { "body.$it" }

    private fun Map<String, Any>?.unexpectedKey(allowedKeys: Set<String>): String? =
        this
            .orEmpty()
            .keys
            .map(String::trim)
            .firstOrNull { key -> key.isNotBlank() && key !in allowedKeys }

    private fun String?.normalizedBridgePath(): String =
        this
            ?.trim()
            ?.substringBefore("?")
            ?.replace(Regex("/nodes/\\d+(?=/|$)"), "/nodes/{nodeId}")
            .orEmpty()

    private fun String?.equalsNormalized(expected: String): Boolean =
        this?.trim()?.equals(expected, ignoreCase = true) == true

    private companion object {
        private val sensitiveFieldKeys = setOf(
            "ownerid",
            "userid",
            "storagepath",
            "objectkey",
            "coskey",
        )

        private val defaultAllowedRoutes = mapOf(
            "rename" to RagAllowedBridgeRoute(
                method = "PUT",
                pathTemplate = "/api/storage/nodes/{nodeId}/rename",
                pathVariableKeys = setOf("nodeId"),
                bodyKeys = setOf("name"),
            ),
            "delete" to RagAllowedBridgeRoute(
                method = "DELETE",
                pathTemplate = "/api/storage/nodes/{nodeId}",
                pathVariableKeys = setOf("nodeId"),
            ),
            "share" to RagAllowedBridgeRoute(
                method = "POST",
                pathTemplate = "/api/share-links",
                bodyKeys = setOf(
                    "nodeIds",
                    "title",
                    "password",
                    "expiresInDays",
                    "allowDownload",
                    "allowSave",
                ),
            ),
            "collection.trash_by_name_contains" to RagAllowedBridgeRoute(
                method = "POST",
                pathTemplate = "/api/storage/nodes/batch/trash",
                bodyKeys = setOf("nodeIds"),
            ),
            "collection.trash_by_category" to RagAllowedBridgeRoute(
                method = "POST",
                pathTemplate = "/api/storage/nodes/batch/trash",
                bodyKeys = setOf("nodeIds"),
            ),
            "collection.move_by_category" to RagAllowedBridgeRoute(
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/move",
                bodyKeys = setOf("nodeIds", "parentId"),
            ),
            "collection.move_by_extension" to RagAllowedBridgeRoute(
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/move",
                bodyKeys = setOf("nodeIds", "parentId"),
            ),
            "collection.move_exact" to RagAllowedBridgeRoute(
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/move",
                bodyKeys = setOf("nodeIds", "parentId"),
            ),
            "collection.move_by_name_contains" to RagAllowedBridgeRoute(
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/move",
                bodyKeys = setOf("nodeIds", "parentId"),
            ),
            "collection.rename_add_prefix" to RagAllowedBridgeRoute(
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/rename",
                bodyKeys = setOf("items"),
            ),
        )
    }
}

internal data class RagActionBridgeValidation(
    val allowed: Boolean,
    val code: RagActionBridgeValidationCode,
    val message: String,
) {
    companion object {
        fun rejected(
            code: RagActionBridgeValidationCode,
            message: String,
        ): RagActionBridgeValidation =
            RagActionBridgeValidation(
                allowed = false,
                code = code,
                message = message,
            )
    }
}

internal enum class RagActionBridgeValidationCode {
    ALLOWED,
    MISSING_DRAFT,
    CLIENT_INPUT_REQUIRED,
    UNSUPPORTED_ACTION,
    SENSITIVE_FIELD_PRESENT,
    NOT_READY,
    CONFIRMATION_REQUIRED,
    NOT_BACKEND_EXECUTABLE,
    AUTHORIZATION_REQUIRED,
    ROUTE_NOT_ALLOWED,
    PARAMETER_NOT_ALLOWED,
}

internal data class RagAllowedBridgeRoute(
    val method: String,
    val pathTemplate: String,
    val pathVariableKeys: Set<String> = emptySet(),
    val queryParameterKeys: Set<String> = emptySet(),
    val bodyKeys: Set<String> = emptySet(),
)
