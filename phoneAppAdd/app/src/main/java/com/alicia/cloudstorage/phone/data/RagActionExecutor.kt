package com.alicia.cloudstorage.phone.data

internal class RagActionExecutor(
    private val repositoryPort: RagActionRepositoryPort = AliciaRagActionRepositoryPort(),
    private val validator: RagActionBridgeValidator = RagActionBridgeValidator(),
    private val executionEnabled: Boolean = false,
) {
    suspend fun execute(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft?,
    ): RagActionExecutionResult {
        val validation = validator.validate(draft)
        if (!validation.allowed) {
            return RagActionExecutionResult.rejected(validation)
        }
        if (!executionEnabled) {
            return RagActionExecutionResult(
                status = RagActionExecutionStatus.DISABLED,
                actionType = draft?.actionType,
                message = "AI 文件操作执行入口尚未启用。",
                validation = validation,
            )
        }

        val safeDraft = draft ?: return RagActionExecutionResult.invalidDraft("缺少执行草稿。", validation)
        return runCatching {
            when (safeDraft.actionType?.trim()) {
                "rename" -> executeRename(context, safeDraft, validation)
                "delete" -> executeDelete(context, safeDraft, validation)
                "share" -> executeShare(context, safeDraft, validation)
                "collection.trash_by_name_contains",
                "collection.trash_by_category",
                "collection.trash",
                -> executeBatchTrash(context, safeDraft, validation)
                "collection.trash_scoped" -> executeScopedTrash(context, safeDraft, validation)
                "collection.move_by_category",
                "collection.move_by_extension",
                "collection.move_exact",
                "collection.move_by_name_contains",
                "collection.move",
                -> executeBatchMove(context, safeDraft, validation)
                "collection.rename_add_prefix" -> executeBatchRename(context, safeDraft, validation)
                else -> RagActionExecutionResult.invalidDraft("移动端暂不支持该执行动作。", validation)
            }
        }.getOrElse { error ->
            RagActionExecutionResult(
                status = RagActionExecutionStatus.FAILED,
                actionType = safeDraft.actionType,
                message = error.message?.takeIf { it.isNotBlank() } ?: "AI 文件操作执行失败。",
                validation = validation,
            )
        }
    }

    private suspend fun executeRename(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft,
        validation: RagActionBridgeValidation,
    ): RagActionExecutionResult {
        val nodeId = draft.nodeId() ?: return RagActionExecutionResult.invalidDraft("缺少重命名目标。", validation)
        val name = draft.body.stringValue("name")
            ?: return RagActionExecutionResult.invalidDraft("缺少新的文件名。", validation)
        repositoryPort.renameNode(context.baseUrl, context.token, nodeId, name)
        return RagActionExecutionResult.completed(
            actionType = draft.actionType,
            message = "已提交重命名操作。",
            validation = validation,
            affectedNodeIds = listOf(nodeId),
        )
    }

    private suspend fun executeDelete(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft,
        validation: RagActionBridgeValidation,
    ): RagActionExecutionResult {
        val nodeId = draft.nodeId() ?: return RagActionExecutionResult.invalidDraft("缺少删除目标。", validation)
        repositoryPort.moveNodeToTrash(context.baseUrl, context.token, nodeId)
        return RagActionExecutionResult.completed(
            actionType = draft.actionType,
            message = "已提交移入回收站操作。",
            validation = validation,
            affectedNodeIds = listOf(nodeId),
        )
    }

    private suspend fun executeShare(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft,
        validation: RagActionBridgeValidation,
    ): RagActionExecutionResult {
        val nodeIds = draft.body.longListValue("nodeIds")
            .ifEmpty { draft.nodeId()?.let(::listOf).orEmpty() }
        if (nodeIds.isEmpty()) {
            return RagActionExecutionResult.invalidDraft("缺少分享目标。", validation)
        }

        val share = repositoryPort.createShareLink(
            baseUrl = context.baseUrl,
            token = context.token,
            nodeIds = nodeIds,
            title = draft.body.stringValue("title"),
            password = draft.body.stringValue("password"),
            expiresInDays = draft.body.intValue("expiresInDays"),
            allowDownload = draft.body.booleanValue("allowDownload") ?: true,
            allowSave = draft.body.booleanValue("allowSave") ?: true,
        )
        return RagActionExecutionResult.completed(
            actionType = draft.actionType,
            message = "已提交分享创建操作。",
            validation = validation,
            affectedNodeIds = nodeIds,
            shareCode = share.shareCode,
        )
    }

    private suspend fun executeBatchTrash(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft,
        validation: RagActionBridgeValidation,
    ): RagActionExecutionResult {
        val nodeIds = draft.body.longListValue("nodeIds")
        if (nodeIds.isEmpty()) {
            return RagActionExecutionResult.invalidDraft("缺少批量删除目标。", validation)
        }

        repositoryPort.moveNodesToTrash(context.baseUrl, context.token, nodeIds)
        return RagActionExecutionResult.completed(
            actionType = draft.actionType,
            message = "已提交批量移入回收站操作。",
            validation = validation,
            affectedNodeIds = nodeIds,
        )
    }

    private suspend fun executeBatchMove(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft,
        validation: RagActionBridgeValidation,
    ): RagActionExecutionResult {
        val nodeIds = draft.body.longListValue("nodeIds")
        if (nodeIds.isEmpty()) {
            return RagActionExecutionResult.invalidDraft("缺少批量移动目标。", validation)
        }

        val parentId = draft.body.nullableLongValue("parentId")
        repositoryPort.moveNodes(context.baseUrl, context.token, nodeIds, parentId)
        return RagActionExecutionResult.completed(
            actionType = draft.actionType,
            message = "已提交批量移动操作。",
            validation = validation,
            affectedNodeIds = nodeIds,
        )
    }

    private suspend fun executeScopedTrash(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft,
        validation: RagActionBridgeValidation,
    ): RagActionExecutionResult {
        val nodeIds = draft.body.longListValue("nodeIds")
        val selectorVersion = draft.body.stringValue("selectorVersion")
        val nodeTypes = draft.body.stringListValue("nodeTypes")
        val scopeFingerprint = draft.body.stringValue("scopeFingerprint")
        val impactFingerprint = draft.body.stringValue("impactFingerprint")
        val expectedImpactCount = draft.body.intValue("expectedImpactCount")
        val root = draft.body.booleanValue("root")
        if (nodeIds.isEmpty() ||
            selectorVersion != "source_selector_v2" ||
            nodeTypes.isEmpty() ||
            scopeFingerprint == null ||
            impactFingerprint == null ||
            expectedImpactCount == null ||
            root == null
        ) {
            return RagActionExecutionResult.invalidDraft("受控批量删除草稿缺少范围校验信息。", validation)
        }

        repositoryPort.moveScopedNodesToTrash(
            baseUrl = context.baseUrl,
            token = context.token,
            payload = ScopedTrashPayload(
                selectorVersion = selectorVersion,
                sourceParentId = draft.body.nullableLongValue("sourceParentId"),
                root = root,
                nodeTypes = nodeTypes,
                nodeIds = nodeIds,
                scopeFingerprint = scopeFingerprint,
                impactFingerprint = impactFingerprint,
                expectedImpactCount = expectedImpactCount,
            ),
        )
        return RagActionExecutionResult.completed(
            actionType = draft.actionType,
            message = "已提交受控批量移入回收站操作。",
            validation = validation,
            affectedNodeIds = nodeIds,
        )
    }

    private suspend fun executeBatchRename(
        context: RagActionExecutionContext,
        draft: RagBackendActionDraft,
        validation: RagActionBridgeValidation,
    ): RagActionExecutionResult {
        val items = draft.body.renameItemsValue("items")
        if (items.isEmpty()) {
            return RagActionExecutionResult.invalidDraft("缺少批量重命名目标。", validation)
        }
        repositoryPort.renameNodes(context.baseUrl, context.token, items)
        return RagActionExecutionResult.completed(
            actionType = draft.actionType,
            message = "已完成批量重命名操作。",
            validation = validation,
            affectedNodeIds = items.map(BatchRenameNodeItemPayload::nodeId),
        )
    }
}

internal interface RagActionRepositoryPort {
    suspend fun renameNode(baseUrl: String, token: String, nodeId: Long, name: String): StorageNode

    suspend fun renameNodes(
        baseUrl: String,
        token: String,
        items: List<BatchRenameNodeItemPayload>,
    ): List<StorageNode>

    suspend fun moveNodeToTrash(baseUrl: String, token: String, nodeId: Long): ApiMessageResponse

    suspend fun moveNodesToTrash(baseUrl: String, token: String, nodeIds: List<Long>): ApiMessageResponse

    suspend fun moveScopedNodesToTrash(
        baseUrl: String,
        token: String,
        payload: ScopedTrashPayload,
    ): ApiMessageResponse

    suspend fun moveNodes(baseUrl: String, token: String, nodeIds: List<Long>, parentId: Long?): List<StorageNode>

    suspend fun createShareLink(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        title: String?,
        password: String?,
        expiresInDays: Int?,
        allowDownload: Boolean,
        allowSave: Boolean,
    ): ShareLinkSummaryResponse
}

internal class AliciaRagActionRepositoryPort(
    private val repository: AliciaRepository = AliciaRepository(),
) : RagActionRepositoryPort {
    override suspend fun renameNode(baseUrl: String, token: String, nodeId: Long, name: String): StorageNode =
        repository.renameNode(baseUrl, token, nodeId, name)

    override suspend fun renameNodes(
        baseUrl: String,
        token: String,
        items: List<BatchRenameNodeItemPayload>,
    ): List<StorageNode> = repository.renameNodes(baseUrl, token, items)

    override suspend fun moveNodeToTrash(baseUrl: String, token: String, nodeId: Long): ApiMessageResponse =
        repository.moveNodeToTrash(baseUrl, token, nodeId)

    override suspend fun moveNodesToTrash(baseUrl: String, token: String, nodeIds: List<Long>): ApiMessageResponse =
        repository.moveNodesToTrash(baseUrl, token, nodeIds)

    override suspend fun moveScopedNodesToTrash(
        baseUrl: String,
        token: String,
        payload: ScopedTrashPayload,
    ): ApiMessageResponse = repository.moveScopedNodesToTrash(baseUrl, token, payload)

    override suspend fun moveNodes(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        parentId: Long?,
    ): List<StorageNode> =
        repository.moveNodes(baseUrl, token, nodeIds, parentId)

    override suspend fun createShareLink(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        title: String?,
        password: String?,
        expiresInDays: Int?,
        allowDownload: Boolean,
        allowSave: Boolean,
    ): ShareLinkSummaryResponse =
        repository.createShareLink(
            baseUrl = baseUrl,
            token = token,
            nodeIds = nodeIds,
            title = title,
            password = password,
            expiresInDays = expiresInDays,
            allowDownload = allowDownload,
            allowSave = allowSave,
        )
}

internal data class RagActionExecutionContext(
    val baseUrl: String,
    val token: String,
)

internal data class RagActionExecutionResult(
    val status: RagActionExecutionStatus,
    val actionType: String?,
    val message: String,
    val validation: RagActionBridgeValidation?,
    val affectedNodeIds: List<Long> = emptyList(),
    val shareCode: String? = null,
) {
    val succeeded: Boolean
        get() = status == RagActionExecutionStatus.COMPLETED

    companion object {
        fun rejected(validation: RagActionBridgeValidation): RagActionExecutionResult =
            RagActionExecutionResult(
                status = RagActionExecutionStatus.VALIDATION_REJECTED,
                actionType = null,
                message = validation.message,
                validation = validation,
            )

        fun invalidDraft(
            message: String,
            validation: RagActionBridgeValidation?,
        ): RagActionExecutionResult =
            RagActionExecutionResult(
                status = RagActionExecutionStatus.INVALID_DRAFT,
                actionType = null,
                message = message,
                validation = validation,
            )

        fun completed(
            actionType: String?,
            message: String,
            validation: RagActionBridgeValidation,
            affectedNodeIds: List<Long>,
            shareCode: String? = null,
        ): RagActionExecutionResult =
            RagActionExecutionResult(
                status = RagActionExecutionStatus.COMPLETED,
                actionType = actionType,
                message = message,
                validation = validation,
                affectedNodeIds = affectedNodeIds,
                shareCode = shareCode,
            )
    }
}

internal enum class RagActionExecutionStatus {
    DISABLED,
    VALIDATION_REJECTED,
    INVALID_DRAFT,
    COMPLETED,
    FAILED,
}

private fun RagBackendActionDraft.nodeId(): Long? =
    pathVariables.nullableLongValue("nodeId")
        ?: targetCandidate?.nodeId
        ?: path?.let { Regex("/nodes/(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() }

private fun Map<String, Any>?.stringValue(key: String): String? =
    this?.get(key)?.let { value ->
        when (value) {
            is String -> value.trim().takeIf { it.isNotBlank() }
            else -> value.toString().trim().takeIf { it.isNotBlank() && it != "null" }
        }
    }

private fun Map<String, Any>?.nullableLongValue(key: String): Long? =
    this?.get(key)?.toLongOrNullValue()

private fun Map<String, Any>?.intValue(key: String): Int? =
    this?.get(key)?.let { value ->
        when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

private fun Map<String, Any>?.booleanValue(key: String): Boolean? =
    this?.get(key)?.let { value ->
        when (value) {
            is Boolean -> value
            is String -> value.trim().toBooleanStrictOrNull()
            else -> null
        }
    }

private fun Map<String, Any>?.longListValue(key: String): List<Long> =
    when (val value = this?.get(key)) {
        is Iterable<*> -> value.mapNotNull { it.toLongOrNullValue() }
        is Array<*> -> value.mapNotNull { it.toLongOrNullValue() }
        else -> value?.toLongOrNullValue()?.let(::listOf).orEmpty()
    }

private fun Map<String, Any>?.stringListValue(key: String): List<String> =
    when (val value = this?.get(key)) {
        is Iterable<*> -> value.mapNotNull { item -> item?.toString()?.trim()?.takeIf(String::isNotBlank) }
        is Array<*> -> value.mapNotNull { item -> item?.toString()?.trim()?.takeIf(String::isNotBlank) }
        else -> value?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
    }

private fun Map<String, Any>?.renameItemsValue(key: String): List<BatchRenameNodeItemPayload> =
    ((this?.get(key) as? Iterable<*>) ?: emptyList<Any?>())
        .mapNotNull { rawItem ->
            val item = rawItem as? Map<*, *> ?: return@mapNotNull null
            val nodeId = item["nodeId"].toLongOrNullValue() ?: return@mapNotNull null
            val name = item["name"]?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            BatchRenameNodeItemPayload(nodeId = nodeId, name = name)
        }

private fun Any?.toLongOrNullValue(): Long? =
    when (this) {
        is Number -> toLong()
        is String -> trim().toLongOrNull()
        else -> null
    }
