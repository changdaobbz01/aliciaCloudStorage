package com.alicia.cloudstorage.phone.ui

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

internal fun RagAssistantPlanResponse.toNavigationTargetOrNull(): AiChatFileResult? {
    val operation = semanticFrame?.operation?.trim()?.uppercase().orEmpty()
    if (operation !in setOf("NAVIGATE", "OPEN_FILE")) {
        return null
    }
    if (semanticFrame?.scope?.type.equals("ROOT", ignoreCase = true)) {
        return AiChatFileResult(
            id = "root",
            nodeId = null,
            parentId = null,
            name = "根目录",
            detail = "/ · FOLDER",
            type = "FOLDER",
            size = null,
            extension = null,
            mimeType = null,
            updatedAt = null,
            path = "/",
        )
    }
    if (semanticFrame?.scope?.type.equals("PARENT", ignoreCase = true)) {
        return AiChatFileResult(
            id = "parent",
            nodeId = null,
            parentId = null,
            name = "上一级",
            detail = ".. · FOLDER",
            type = "FOLDER",
            size = null,
            extension = null,
            mimeType = null,
            updatedAt = null,
            path = "..",
        )
    }
    val binding = candidateBinding ?: return null
    val candidate = binding.selectedCandidate
        ?: binding.candidates.orEmpty().singleOrNull()
        ?: return null
    val type = candidate.type?.trim()?.uppercase()
    if (operation == "NAVIGATE" && type != "FOLDER") {
        return null
    }
    if (operation == "OPEN_FILE" && type != "FILE") {
        return null
    }
    return AiChatFileResult(
        id = candidate.nodeId?.toString() ?: candidate.path.orEmpty(),
        nodeId = candidate.nodeId,
        parentId = candidate.parentId,
        name = candidate.name.orEmpty(),
        detail = listOfNotNull(candidate.path, candidate.type).joinToString(" · "),
        type = candidate.type,
        size = candidate.size,
        extension = candidate.extension,
        mimeType = candidate.mimeType,
        updatedAt = candidate.updatedAt,
        path = candidate.path,
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
    val scope = AiChatExecutionFeedback.mutationScope(normalizedActionType) ?: return null

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
                    path = candidate.source,
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
        candidateId = candidate.nodeId,
        candidateIndex = oneBasedIndex,
        bindingKey = selectionBindingKey,
        planId = planId,
    )
}

private fun RagReviewPresentation.toAiChatPlanPreview(
    plan: RagActionPlan?,
    backendDraft: RagBackendActionDraft?,
): AiChatPlanPreview =
    AiChatPlanPreview(
        title = title,
        lines = lines,
        planId = planId,
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
    if (plan.isClientUploadPlan()) {
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
    if (kind == RagReviewKind.CANDIDATE_SELECTION || kind == RagReviewKind.BLOCKED) {
        return null
    }

    plan.toUploadTargetRequest()
        ?.let { request ->
            return AiChatPlanClientActionControls(
                label = if (plan.hasMissingUploadInput(backendDraft)) "选择文件并上传" else "确认上传",
                uploadRequest = request,
            )
        }

    plan.toCreateFolderThenUploadRequest()
        ?.let { request ->
            return AiChatPlanClientActionControls(
                label = if (plan.hasMissingUploadInput(backendDraft)) {
                    "新建并选择文件"
                } else {
                    "确认新建并上传"
                },
                uploadRequest = request,
            )
        }

    if (kind == RagReviewKind.CLIENT_INPUT) {
        backendDraft.toUploadClientRequest()?.let { request ->
            return AiChatPlanClientActionControls(
                label = if (backendDraft?.requiredClientFields.orEmpty().isEmpty()) "确认上传" else "选择文件并上传",
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
                status.equals("storage_api_error", ignoreCase = true) ||
                status.equals("unsupported_filter", ignoreCase = true)
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

private fun RagBackendActionDraft?.isReviewableBackendDraft(): Boolean =
    this != null &&
        status.equals("backend_action_ready", ignoreCase = true) &&
        executableByBackend == true &&
        !actionType.equals("none", ignoreCase = true) &&
        requiredClientFields.orEmpty().isEmpty()

private fun RagBackendActionDraft?.isUploadClientActionDraft(): Boolean =
    this != null &&
        actionType.equals("upload_target", ignoreCase = true) &&
        status.equals("client_action_required", ignoreCase = true)

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

private fun RagActionPlan?.toUploadTargetRequest(): AiChatClientUploadRequest? {
    val plan = this ?: return null
    if (!plan.actionType.equals("file.upload", ignoreCase = true) &&
        !plan.actionType.equals("upload_target", ignoreCase = true)
    ) {
        return null
    }

    val targetBinding = plan.bindings
        .orEmpty()
        .get("targetParent")
        ?: return null
    val target = targetBinding.selectedCandidate
        ?: targetBinding.candidates.orEmpty().singleOrNull()
        ?: return null
    val uploadStep = plan.steps
        .orEmpty()
        .firstOrNull { step -> step.action.equals("file.upload", ignoreCase = true) }

    return AiChatClientUploadRequest(
        parentId = uploadStep?.params.longValue("parentId") ?: target.nodeId,
        targetName = target.name?.takeIf { it.isNotBlank() } ?: "根目录",
    )
}

private fun RagActionPlan?.isClientUploadPlan(): Boolean =
    this?.actionType.equals("file.upload", ignoreCase = true) ||
        this?.actionType.equals("upload_target", ignoreCase = true) ||
        this?.actionType.equals("composite.create_folder_then_upload", ignoreCase = true)

private fun RagActionPlan?.hasMissingUploadInput(backendDraft: RagBackendActionDraft?): Boolean {
    val requiredFields = this?.requiredClientFields.orEmpty() + backendDraft?.requiredClientFields.orEmpty()
    return requiredFields.any { field ->
        field.equals("file", ignoreCase = true) ||
            field.equals("files", ignoreCase = true) ||
            field.equals("client_file", ignoreCase = true) ||
            field.equals("client_files", ignoreCase = true)
    }
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
