package com.alicia.cloudstorage.phone.ui

internal enum class AiChatAuthor {
    ASSISTANT,
    USER,
}

internal data class AiChatFileResult(
    val id: String,
    val nodeId: Long?,
    val parentId: Long?,
    val name: String,
    val detail: String,
    val type: String?,
    val size: Long?,
    val extension: String?,
    val mimeType: String?,
    val updatedAt: String?,
    val path: String? = null,
    val selectionAction: AiChatCandidateSelectionAction? = null,
)

internal data class AiChatFolderOpenTarget(
    val nodeId: Long?,
    val name: String,
)

internal fun AiChatFileResult.toFolderOpenTargetOrNull(): AiChatFolderOpenTarget? {
    if (!type.equals("FOLDER", ignoreCase = true)) {
        return null
    }
    if (nodeId != null) {
        return AiChatFolderOpenTarget(nodeId = nodeId, name = name)
    }
    return path
        ?.trim()
        ?.takeIf { it == "/" }
        ?.let { AiChatFolderOpenTarget(nodeId = null, name = name.ifBlank { "根目录" }) }
}

internal data class AiChatCandidateSelectionAction(
    val label: String,
    val requestMessage: String,
    val displayText: String,
    val candidateId: Long? = null,
    val candidateIndex: Int? = null,
    val bindingKey: String? = null,
    val planId: String? = null,
)

internal data class AiChatPlanPreview(
    val title: String,
    val lines: List<String>,
    val actionControls: AiChatPlanActionControls? = null,
    val clientActionControls: AiChatPlanClientActionControls? = null,
    val planId: String? = null,
)

internal data class AiChatPlanActionControls(
    val confirmLabel: String,
    val cancelLabel: String,
    val destructive: Boolean,
)

internal data class AiChatPlanClientActionControls(
    val label: String,
    val uploadRequest: AiChatClientUploadRequest,
)

internal data class AiChatPendingAttachment(
    val id: String,
    val name: String,
    val isFolder: Boolean = false,
)

internal data class AiChatFileMutationSignal(
    val actionType: String,
    val affectedNodeIds: List<Long>,
    val scope: AiChatFileMutationScope,
)

internal enum class AiChatFileMutationScope {
    FILES_ONLY,
    FILES_AND_TRASH,
}

internal data class AiChatMessage(
    val id: Long,
    val author: AiChatAuthor,
    val text: String,
    val files: List<AiChatFileResult> = emptyList(),
    val plan: AiChatPlanPreview? = null,
)

internal data class AiChatUiState(
    val messages: List<AiChatMessage>,
    val draft: String,
    val online: Boolean,
    val sending: Boolean = false,
    val pendingAttachments: List<AiChatPendingAttachment> = emptyList(),
)

internal sealed interface AiChatAction {
    data object Back : AiChatAction
    data object NewConversation : AiChatAction
    data object Attach : AiChatAction
    data object ClearAttachments : AiChatAction
    data object Send : AiChatAction
    data class DraftChanged(val value: String) : AiChatAction
    data class OpenFile(val file: AiChatFileResult) : AiChatAction
    data class SelectCandidate(val messageId: Long, val file: AiChatFileResult) : AiChatAction
    data class RunClientUpload(val messageId: Long, val request: AiChatClientUploadRequest) : AiChatAction
    data class ConfirmReview(val messageId: Long) : AiChatAction
    data class CancelReview(val messageId: Long) : AiChatAction
}
