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

internal enum class AiChatResultMode {
    SEARCH_RESULTS,
    CANDIDATE_SELECTION,
    ACTION_PREVIEW,
}

internal data class AiChatResultSection(
    val mode: AiChatResultMode,
    val title: String,
    val contextLabel: String? = null,
    val totalCount: Long? = null,
    val hasMore: Boolean? = null,
    val sortBy: String? = null,
    val sortDirection: String? = null,
)

internal object AiChatResultDisplayPolicy {
    const val COLLAPSE_THRESHOLD = 5
    const val COLLAPSED_ITEM_COUNT = 3

    fun canCollapse(section: AiChatResultSection, itemCount: Int): Boolean =
        section.mode != AiChatResultMode.CANDIDATE_SELECTION && itemCount >= COLLAPSE_THRESHOLD

    fun visibleItemCount(
        section: AiChatResultSection,
        itemCount: Int,
        expanded: Boolean,
    ): Int = if (canCollapse(section, itemCount) && !expanded) {
        minOf(COLLAPSED_ITEM_COUNT, itemCount)
    } else {
        itemCount
    }

    fun countLabel(section: AiChatResultSection, itemCount: Int): String {
        val count = section.totalCount
            ?.let { "共 $it 项" }
            ?: "已展示 $itemCount 项"
        return listOfNotNull(section.contextLabel?.takeIf(String::isNotBlank), count)
            .joinToString(" · ")
    }

    fun toggleLabel(
        section: AiChatResultSection,
        itemCount: Int,
        expanded: Boolean,
    ): String {
        if (expanded) {
            return "收起"
        }
        val hiddenCount = (itemCount - COLLAPSED_ITEM_COUNT).coerceAtLeast(0)
        val hasCompleteLocalResult = section.totalCount != null &&
            section.hasMore != true &&
            section.totalCount <= itemCount.toLong()
        return if (hasCompleteLocalResult) {
            "展开其余 $hiddenCount 项"
        } else {
            "展开另外 $hiddenCount 项"
        }
    }

    fun partialResultLabel(section: AiChatResultSection, itemCount: Int): String? {
        val totalCount = section.totalCount ?: return null
        if (section.hasMore != true && totalCount <= itemCount.toLong()) {
            return null
        }
        return "当前展示前 $itemCount 项，共 $totalCount 项"
    }
}

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

internal data class AiChatShareResult(
    val shareCode: String,
    val shareUrl: String,
)

internal data class AiChatMessage(
    val id: Long,
    val author: AiChatAuthor,
    val text: String,
    val files: List<AiChatFileResult> = emptyList(),
    val plan: AiChatPlanPreview? = null,
    val resultSection: AiChatResultSection? = null,
    val shareResult: AiChatShareResult? = null,
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
