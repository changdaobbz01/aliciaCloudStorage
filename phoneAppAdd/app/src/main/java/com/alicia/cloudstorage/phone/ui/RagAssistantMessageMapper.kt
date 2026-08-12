package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.ApiException
import com.alicia.cloudstorage.phone.data.RagActionExecutionResult
import com.alicia.cloudstorage.phone.data.RagActionExecutionStatus
import com.alicia.cloudstorage.phone.data.RagActionPlan
import com.alicia.cloudstorage.phone.data.RagAssistantPlanResponse
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft

private val ragReviewPresenter = RagReviewPresenter()

internal fun initialRagMessages(): List<AiChatMessage> = listOf(
    AiChatMessage(
        id = 1L,
        author = AiChatAuthor.ASSISTANT,
        text = "你好，我是安安。你可以问我找文件、整理思路，或者先和我聊聊。",
    ),
)

internal fun RagAssistantPlanResponse.toAssistantMessage(id: Long): AiChatMessage {
    val review = ragReviewPresenter.present(this)

    return AiChatMessage(
        id = id,
        author = AiChatAuthor.ASSISTANT,
        text = assistantDisplayText(),
        files = review.toAiChatFileResults(),
        plan = review?.toAiChatPlanPreview(actionPlan, backendActionDraft),
    )
}

internal fun RagActionExecutionResult.toAssistantMessage(id: Long): AiChatMessage =
    AiChatMessage(
        id = id,
        author = AiChatAuthor.ASSISTANT,
        text = executionDisplayText(),
    )

internal fun RagActionExecutionResult.toFileMutationSignal(): AiChatFileMutationSignal? {
    if (!succeeded) {
        return null
    }

    val normalizedActionType = actionType?.trim().orEmpty()
    val scope = when (normalizedActionType) {
        "rename",
        "collection.move_by_extension",
        "collection.move_by_name_contains",
        -> AiChatFileMutationScope.FILES_ONLY

        "delete",
        "collection.trash_by_name_contains",
        "collection.trash_by_category",
        -> AiChatFileMutationScope.FILES_AND_TRASH

        else -> return null
    }

    return AiChatFileMutationSignal(
        actionType = normalizedActionType,
        affectedNodeIds = affectedNodeIds.distinct(),
        scope = scope,
    )
}

private fun RagReviewPresentation?.toAiChatFileResults(): List<AiChatFileResult> =
    this?.let { review ->
        review.candidates
            .take(6)
            .mapIndexed { index, candidate ->
                AiChatFileResult(
                    id = candidate.id,
                    nodeId = candidate.nodeId,
                    parentId = candidate.parentId,
                    name = candidate.name,
                    detail = candidate.detail,
                    type = candidate.type,
                    size = candidate.size,
                    extension = candidate.extension,
                    mimeType = candidate.mimeType,
                    updatedAt = candidate.updatedAt,
                    selectionAction = review.toCandidateSelectionAction(index, candidate),
                )
            }
    }.orEmpty()

private fun RagReviewPresentation.toCandidateSelectionAction(
    index: Int,
    candidate: RagReviewCandidate,
): AiChatCandidateSelectionAction? {
    if (kind != RagReviewKind.CANDIDATE_SELECTION) {
        return null
    }

    val oneBasedIndex = index + 1
    return AiChatCandidateSelectionAction(
        label = "选择",
        requestMessage = "选第${oneBasedIndex}个",
        displayText = "选择第 $oneBasedIndex 个：${candidate.name}",
    )
}

private fun RagReviewPresentation.toAiChatPlanPreview(
    plan: RagActionPlan?,
    backendDraft: RagBackendActionDraft?,
): AiChatPlanPreview =
    AiChatPlanPreview(
        title = title,
        lines = lines,
        actionControls = toAiChatPlanActionControls(plan, backendDraft),
        clientActionControls = toAiChatPlanClientActionControls(plan, backendDraft),
    )

private fun RagReviewPresentation.toAiChatPlanActionControls(
    plan: RagActionPlan?,
    backendDraft: RagBackendActionDraft?,
): AiChatPlanActionControls? {
    if (!requiresFinalConfirmation) {
        return null
    }

    val hasExecutableDraft = backendDraft.isReviewableBackendDraft()
    if (!hasExecutableDraft && !plan.canRequestBackendDraft(kind)) {
        return null
    }

    return AiChatPlanActionControls(
        confirmLabel = when {
            hasExecutableDraft -> "确认执行"
            kind == RagReviewKind.COLLECTION_REVIEW -> "确认范围"
            else -> "确认计划"
        },
        cancelLabel = "取消",
        destructive = risk == RagReviewRisk.HIGH ||
            actionType.equals("delete", ignoreCase = true) ||
            actionType?.contains("trash", ignoreCase = true) == true,
    )
}

private fun RagActionPlan?.canRequestBackendDraft(kind: RagReviewKind): Boolean {
    val plan = this ?: return false
    if (kind == RagReviewKind.CANDIDATE_SELECTION ||
        kind == RagReviewKind.SEARCH_RESULTS ||
        kind == RagReviewKind.CLIENT_INPUT ||
        kind == RagReviewKind.BLOCKED
    ) {
        return false
    }
    if (plan.actionType?.trim()?.equals("composite.create_folder_then_upload", ignoreCase = true) == true) {
        return false
    }

    return plan.status?.trim()?.lowercase() in setOf(
        "review_required",
        "collection_review_required",
        "ready_to_execute",
    )
}

private fun RagReviewPresentation.toAiChatPlanClientActionControls(
    plan: RagActionPlan?,
    backendDraft: RagBackendActionDraft?,
): AiChatPlanClientActionControls? {
    if (kind == RagReviewKind.CLIENT_INPUT) {
        backendDraft.toUploadClientRequest()?.let { request ->
            return AiChatPlanClientActionControls(
                label = "选择文件",
                uploadRequest = request,
            )
        }
    }

    if (kind == RagReviewKind.FINAL_CONFIRMATION || kind == RagReviewKind.CLIENT_INPUT) {
        plan.toCreateFolderThenUploadRequest()?.let { request ->
            return AiChatPlanClientActionControls(
                label = "新建并选择文件",
                uploadRequest = request,
            )
        }
    }

    return null
}

private fun RagAssistantPlanResponse.assistantDisplayText(): String {
    val bindingMessage = candidateBinding
        ?.message
        ?.takeIf { it.isNotBlank() }
    val shouldPreferBindingMessage = candidateBinding
        ?.status
        ?.let { status ->
            status.equals("no_candidates", ignoreCase = true) ||
                status.equals("missing_authorization", ignoreCase = true) ||
                status.equals("storage_api_not_configured", ignoreCase = true) ||
                status.equals("storage_api_error", ignoreCase = true)
        }
        ?: false
    val pieces = listOfNotNull(
        bindingMessage?.takeIf { shouldPreferBindingMessage },
        assistantText
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !shouldPreferBindingMessage },
        clarificationQuestion
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != assistantText },
        bindingMessage
            ?.takeIf { assistantText.isNullOrBlank() && clarificationQuestion.isNullOrBlank() },
    )

    return pieces
        .joinToString(separator = "\n")
        .ifBlank { "我已经收到你的请求，但这次没有可展示的回复内容。" }
}

internal fun Throwable.readableRagMessage(): String =
    when (this) {
        is ApiException -> message
        else -> message?.takeIf { it.isNotBlank() } ?: "安安暂时没有回应，请稍后再试。"
    }

private fun RagBackendActionDraft?.isReviewableBackendDraft(): Boolean =
    this != null &&
        status.equals("backend_action_ready", ignoreCase = true) &&
        executableByBackend == true &&
        !actionType.equals("none", ignoreCase = true) &&
        requiredClientFields.orEmpty().isEmpty()

private fun RagBackendActionDraft?.isUploadClientActionDraft(): Boolean =
    this != null &&
        actionType.equals("upload_target", ignoreCase = true) &&
        requiredClientFields.orEmpty().any { field ->
            field.equals("file", ignoreCase = true) ||
                field.equals("files", ignoreCase = true) ||
                field.equals("client_file", ignoreCase = true)
        }

private fun RagBackendActionDraft?.toUploadClientRequest(): AiChatClientUploadRequest? {
    val draft = this ?: return null
    if (!draft.isUploadClientActionDraft()) {
        return null
    }

    return AiChatClientUploadRequest(
        parentId = draft.queryParameters.longValue("parentId") ?: draft.targetCandidate?.nodeId,
        targetName = draft.targetCandidate?.name,
    )
}

private fun RagActionPlan?.toCreateFolderThenUploadRequest(): AiChatClientUploadRequest? {
    val plan = this ?: return null
    if (!plan.actionType.equals("composite.create_folder_then_upload", ignoreCase = true)) {
        return null
    }

    val createFolderStep = plan.steps
        .orEmpty()
        .firstOrNull { step -> step.action.equals("folder.create", ignoreCase = true) }
        ?: return null
    val parentId = createFolderStep.params.longValue("parentId")
    val folderName = createFolderStep.params.stringValue("folderName")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val parentName = plan.bindings
        .orEmpty()
        .get("targetParent")
        ?.selectedCandidate
        ?.name
        ?.takeIf { it.isNotBlank() }

    return AiChatClientUploadRequest(
        parentId = parentId,
        targetName = listOfNotNull(parentName, folderName).joinToString("/"),
        createFolderName = folderName,
    )
}

private fun Map<String, Any>?.longValue(key: String): Long? =
    this?.get(key).toLongOrNullValue()

private fun Map<String, Any>?.stringValue(key: String): String? =
    when (val value = this?.get(key)) {
        is String -> value
        is Number -> value.toString()
        else -> null
    }

private fun Any?.toLongOrNullValue(): Long? =
    when (this) {
        is Number -> toLong()
        is String -> trim().toLongOrNull()
        else -> null
    }

private fun RagActionExecutionResult.executionDisplayText(): String =
    when (status) {
        RagActionExecutionStatus.DISABLED -> {
            "我已完成本地校验，但当前 AI 文件操作执行入口默认关闭，所以不会改动文件。"
        }
        RagActionExecutionStatus.VALIDATION_REJECTED -> "这次计划没有通过本地校验：$message"
        RagActionExecutionStatus.INVALID_DRAFT -> "这次计划还缺少必要信息：$message"
        RagActionExecutionStatus.COMPLETED -> {
            listOfNotNull(
                message,
                shareCode?.let { "分享码：$it" },
            ).joinToString("\n")
        }
        RagActionExecutionStatus.FAILED -> "提交文件操作失败：$message"
    }
