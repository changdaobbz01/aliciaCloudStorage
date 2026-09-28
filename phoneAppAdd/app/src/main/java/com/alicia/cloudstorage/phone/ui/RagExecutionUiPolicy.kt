package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.AliciaShareLinks
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagExecutionResponse
import com.alicia.cloudstorage.phone.data.RagExecutionStepResponse

internal enum class RagExecutionRoute {
    CLOUD,
    LEGACY_LOCAL,
    CONTINUE_PLAN,
    BLOCKED,
}

internal object RagExecutionClientPolicy {
    fun route(
        executionReference: AiChatExecutionReference?,
        backendDraft: RagBackendActionDraft?,
        cloudExecutionEnabled: Boolean,
        legacyLocalExecutionEnabled: Boolean,
    ): RagExecutionRoute = when {
        executionReference != null && cloudExecutionEnabled -> RagExecutionRoute.CLOUD
        executionReference != null -> RagExecutionRoute.BLOCKED
        backendDraft == null -> RagExecutionRoute.CONTINUE_PLAN
        cloudExecutionEnabled -> RagExecutionRoute.BLOCKED
        legacyLocalExecutionEnabled -> RagExecutionRoute.LEGACY_LOCAL
        else -> RagExecutionRoute.BLOCKED
    }
}

internal data class RagWaitingUploadInput(
    val executionId: String,
    val expectedVersion: Long,
    val stepId: String,
    val parentId: Long,
)

internal fun RagExecutionResponse.waitingUploadInput(): RagWaitingUploadInput? {
    if (normalizedStatus() != "WAITING_CLIENT_INPUT") {
        return null
    }
    val safeExecutionId = executionId?.trim()?.takeIf(String::isNotBlank) ?: return null
    val safeVersion = version?.takeIf { it >= 0L } ?: return null
    val waitingSteps = steps.orEmpty().filter { step ->
        step.actionType.equals("UPLOAD_FILES", ignoreCase = true) &&
            step.status.equals("WAITING_CLIENT_INPUT", ignoreCase = true)
    }
    val step = waitingSteps.singleOrNull() ?: return null
    val safeStepId = step.stepId?.trim()?.takeIf(String::isNotBlank) ?: return null
    val parentId = step.result.longValue("parentId")?.takeIf { it > 0L } ?: return null
    val requiredFields = (step.result?.get("requiredFields") as? List<*>)
        .orEmpty()
        .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
    if (requiredFields != listOf("files")) {
        return null
    }
    return RagWaitingUploadInput(safeExecutionId, safeVersion, safeStepId, parentId)
}

internal fun RagExecutionResponse.isTerminal(): Boolean =
    normalizedStatus() in setOf(
        "SUCCEEDED",
        "PARTIALLY_SUCCEEDED",
        "FAILED",
        "CANCELLED",
        "EXPIRED",
    )

internal fun RagExecutionResponse.toProgressMessage(): String =
    when (normalizedStatus()) {
        "PENDING_CONFIRMATION" -> "计划正在等待确认。"
        "QUEUED" -> "计划已确认，正在云端排队。离开页面也会继续执行。"
        "RUNNING" -> "云端正在执行，请稍等..."
        "RETRY_WAIT" -> "云端暂时没有完成，正在进行安全重试..."
        "WAITING_CLIENT_INPUT" -> "任务正在等待客户端补充输入。"
        "SUCCEEDED" -> successMessage()
        "PARTIALLY_SUCCEEDED" -> "云端任务已结束，但只有部分步骤成功。"
        "CANCELLED" -> "已取消这次云端任务，没有继续提交变更。"
        "EXPIRED" -> "这次云端任务已过期，请重新生成计划。"
        "FAILED" -> "云端任务未能完成（${safeErrorCode()}）。"
        else -> "云端任务状态正在更新..."
    }

internal fun RagExecutionResponse.toAssistantMessage(
    id: Long,
    apiBaseUrl: String,
): AiChatMessage {
    val step = primaryStep()
    val shareCode = step?.result.stringValue("shareCode")
    val shareResult = shareCode
        ?.let { code -> AliciaShareLinks.build(apiBaseUrl, code)?.let { url -> AiChatShareResult(code, url) } }
    return AiChatMessage(
        id = id,
        author = AiChatAuthor.ASSISTANT,
        text = toProgressMessage(),
        shareResult = shareResult,
    )
}

internal fun RagExecutionResponse.toFileMutationSignal(): AiChatFileMutationSignal? {
    if (normalizedStatus() != "SUCCEEDED") {
        return null
    }
    val step = primaryStep() ?: return null
    val actionType = step.actionType.toClientActionType() ?: return null
    val scope = AiChatExecutionFeedback.mutationScope(actionType) ?: return null
    val nodeIds = buildList {
        step.result.longValue("nodeId")?.let(::add)
        addAll(step.result.batchNodeIds())
    }.distinct()
    return AiChatFileMutationSignal(actionType, nodeIds, scope)
}

private fun RagExecutionResponse.normalizedStatus(): String = status?.trim()?.uppercase().orEmpty()

private fun RagExecutionResponse.successMessage(): String =
    when (resultCode?.trim()?.uppercase()) {
        "NODE_RENAMED" -> "已在云端完成重命名。"
        "NODE_TRASHED" -> "已在云端移入回收站。"
        "NODE_MOVED" -> "已在云端完成移动。"
        "FOLDER_CREATED" -> "已在云端创建文件夹。"
        "SHARE_CREATED" -> "已在云端创建分享。"
        "NODES_RENAMED" -> "已在云端完成批量重命名。"
        "NODES_TRASHED" -> "已在云端将所选项目移入回收站。"
        "NODES_MOVED" -> "已在云端完成批量移动。"
        else -> "云端任务已完成。"
    }

private fun RagExecutionResponse.safeErrorCode(): String =
    errorCode?.trim()?.takeIf(String::isNotBlank) ?: "unknown_error"

private fun RagExecutionResponse.primaryStep(): RagExecutionStepResponse? = steps.orEmpty().firstOrNull()

private fun String?.toClientActionType(): String? =
    when (this?.trim()?.uppercase()) {
        "NODE_RENAME" -> "rename"
        "NODE_TRASH" -> "delete"
        "NODE_MOVE" -> "move"
        "FOLDER_CREATE" -> "folder.create"
        "SHARE_CREATE" -> "share"
        "NODE_BATCH_RENAME" -> "collection.rename_add_prefix"
        "NODE_BATCH_TRASH" -> "collection.trash"
        "NODE_BATCH_MOVE" -> "collection.move"
        else -> null
    }

private fun Map<String, Any>?.stringValue(key: String): String? =
    this?.get(key)?.toString()?.trim()?.takeIf(String::isNotBlank)

private fun Map<String, Any>?.longValue(key: String): Long? =
    when (val value = this?.get(key)) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }

private fun Map<String, Any>?.batchNodeIds(): List<Long> =
    (this?.get("items") as? List<*>)
        .orEmpty()
        .mapNotNull { item ->
            val values = item as? Map<*, *> ?: return@mapNotNull null
            when (val value = values["nodeId"]) {
                is Number -> value.toLong()
                is String -> value.trim().toLongOrNull()
                else -> null
            }
        }
