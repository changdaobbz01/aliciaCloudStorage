package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.RagActionPlan
import com.alicia.cloudstorage.phone.data.RagActionPlanBinding
import com.alicia.cloudstorage.phone.data.RagAssistantPlanResponse
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagCandidateBinding
import com.alicia.cloudstorage.phone.data.RagCandidateItem

internal class RagReviewPresenter {
    fun present(response: RagAssistantPlanResponse): RagReviewPresentation? {
        if (response.isNonOperationalReply()) {
            return null
        }

        val plan = response.actionPlan
        val backendDraft = response.backendActionDraft
        val actionType = plan?.actionType ?: backendDraft?.actionType.orEmpty()
        val candidates = response.reviewCandidates().withOperationPreview(response)
        val kind = response.reviewKind(plan, backendDraft, actionType, candidates)
        val lines = response.reviewLines(plan, backendDraft, actionType)

        return RagReviewPresentation(
            kind = kind,
            title = actionType.toPlanTitle(plan?.planKind),
            lines = lines.distinct().ifEmpty { listOf("已生成计划，等待进一步确认或选择。") },
            candidates = candidates,
            risk = plan?.risk.toReviewRisk(),
            requiresFinalConfirmation = response.requiresFinalConfirmation(kind),
            actionType = actionType.ifBlank { null },
        )
    }
}

internal data class RagReviewPresentation(
    val kind: RagReviewKind,
    val title: String,
    val lines: List<String>,
    val candidates: List<RagReviewCandidate> = emptyList(),
    val risk: RagReviewRisk = RagReviewRisk.NONE,
    val requiresFinalConfirmation: Boolean = false,
    val actionType: String? = null,
)

internal enum class RagReviewKind {
    PLAN_PREVIEW,
    SEARCH_RESULTS,
    CANDIDATE_SELECTION,
    COLLECTION_REVIEW,
    FINAL_CONFIRMATION,
    CLIENT_INPUT,
    BLOCKED,
}

internal enum class RagReviewRisk {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN,
}

internal data class RagReviewCandidate(
    val id: String,
    val nodeId: Long?,
    val parentId: Long?,
    val name: String,
    val detail: String,
    val source: String,
    val type: String?,
    val size: Long?,
    val extension: String?,
    val mimeType: String?,
    val updatedAt: String?,
)

internal fun RagAssistantPlanResponse.actionPlanPreview(): AiChatPlanPreview? =
    RagReviewPresenter()
        .present(this)
        ?.let { review ->
            AiChatPlanPreview(
                title = review.title,
                lines = review.lines,
            )
        }

internal fun RagAssistantPlanResponse.isPureRespondOnlyPlan(): Boolean {
    return nextAction.equalsNormalized("respond_only") && isNonOperationalReply()
}

internal fun RagAssistantPlanResponse.isNonOperationalReply(): Boolean {
    val plan = actionPlan
    val isNoopDraft = actionDraft == null || actionDraft.type.isBlankOrNone()
    val draftRequestsBinding = actionDraft?.needsBackendBinding == true
    val isNoopPlan = plan == null || plan.actionType.equalsNormalized("none")
    val isNoRisk = plan?.risk == null || plan.risk.equalsNormalized("none")
    val hasCandidates = candidateBinding.hasDisplayCandidates() ||
        plan?.bindings.orEmpty().values.any(RagActionPlanBinding::hasDisplayCandidates)
    val hasRequiredClientFields = plan?.requiredClientFields.orEmpty().isNotEmpty() ||
        backendActionDraft?.requiredClientFields.orEmpty().isNotEmpty()
    val hasExecutableDraft = backendActionDraft.hasExecutableSignal()
    val hasConfirmationSignal = safety?.requiresConfirmation == true ||
        plan?.confirmationLevel?.let { !it.equalsNormalized("none") } == true

    return isNoopDraft &&
        !draftRequestsBinding &&
        isNoopPlan &&
        isNoRisk &&
        !hasConfirmationSignal &&
        !hasCandidates &&
        !hasRequiredClientFields &&
        !hasExecutableDraft
}

private fun RagAssistantPlanResponse.reviewKind(
    plan: RagActionPlan?,
    backendDraft: RagBackendActionDraft?,
    actionType: String,
    candidates: List<RagReviewCandidate>,
): RagReviewKind {
    val candidateStatus = candidateBinding?.status?.trim()?.lowercase()
    val planStatus = plan?.status?.trim()?.lowercase()
    when (interaction?.stage?.trim()?.uppercase()) {
        "NEED_CANDIDATE_SELECTION" -> return RagReviewKind.CANDIDATE_SELECTION
        "NEED_CONFIRMATION" -> return if (
            planStatus == "collection_review_required" || plan?.planKind.equalsNormalized("collection")
        ) {
            RagReviewKind.COLLECTION_REVIEW
        } else {
            RagReviewKind.FINAL_CONFIRMATION
        }
        "NEED_CLIENT_INPUT" -> return RagReviewKind.CLIENT_INPUT
        "BLOCKED" -> return RagReviewKind.BLOCKED
    }

    if (candidateStatus in blockedCandidateStatuses) {
        return RagReviewKind.BLOCKED
    }
    if (plan?.requiredClientFields.orEmpty().isNotEmpty() ||
        backendDraft?.requiredClientFields.orEmpty().isNotEmpty()
    ) {
        return RagReviewKind.CLIENT_INPUT
    }
    if (
        (planStatus == "collection_review_required" || plan?.planKind.equalsNormalized("collection")) &&
        planStatus != "candidate_selection_required" &&
        candidateStatus != "multiple_candidates"
    ) {
        return RagReviewKind.COLLECTION_REVIEW
    }
    if (planStatus == "candidate_selection_required" ||
        candidateStatus == "multiple_candidates"
    ) {
        return RagReviewKind.CANDIDATE_SELECTION
    }
    if (planStatus == "review_required" ||
        planStatus == "ready_to_execute" ||
        backendDraft.shouldShowDraftLine()
    ) {
        return RagReviewKind.FINAL_CONFIRMATION
    }
    if (actionType.equalsNormalized("search") && candidates.isNotEmpty()) {
        return RagReviewKind.SEARCH_RESULTS
    }
    return RagReviewKind.PLAN_PREVIEW
}

private fun RagAssistantPlanResponse.reviewLines(
    plan: RagActionPlan?,
    backendDraft: RagBackendActionDraft?,
    actionType: String,
): List<String> {
    if (plan == null) {
        return backendDraft.toBackendOnlyLines(actionType)
    }

    val lines = mutableListOf<String>()
    candidateBinding?.status.toCandidateStatusLine()
        ?.let { lines += it }
        ?: plan.status.toPlanStatusLine(actionType)?.let { lines += it }

    lines += plan.collectionReviewLines()

    candidateBinding?.query
        ?.takeIf { it.isNotBlank() }
        ?.let { lines += "线索：$it" }

    val bindingCount = plan.bindings.orEmpty().values.sumOf { binding ->
        binding.count ?: binding.candidates.orEmpty().size
    }
    if (bindingCount > 0) {
        lines += "已找到 $bindingCount 个候选，先核对再继续。"
    }

    val requiredClientFields = plan.requiredClientFields.orEmpty() + backendDraft?.requiredClientFields.orEmpty()
    requiredClientFields
        .map { it.toClientFieldLabel() }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString("、")
        ?.let { lines += "还需要补充：$it" }

    plan.risk.toRiskLine()?.let { lines += it }

    backendDraft?.takeIf(RagBackendActionDraft::shouldShowDraftLine)?.let {
        lines += "已准备受控执行草稿，确认后仍会由云盘后端校验。"
    }

    plan.messages
        .orEmpty()
        .mapNotNull { it.text?.takeIf(String::isNotBlank) }
        .filter { it !in lines }
        .take(2)
        .forEach { lines += it }

    lines += actionType.toSafetyReminder()
    return lines
}

private fun RagAssistantPlanResponse.reviewCandidates(): List<RagReviewCandidate> {
    val directCandidates = candidateBinding?.displayCandidates().orEmpty()
    if (candidateBinding.isSelectableCandidateBinding() && directCandidates.isNotEmpty()) {
        return directCandidates.toReviewCandidates()
    }

    val planBindings = actionPlan
        ?.bindings
        .orEmpty()
        .values
    val planStatus = actionPlan?.status?.trim()?.lowercase()
    val selectablePlanCandidates = if (planStatus == "candidate_selection_required") {
        planBindings
            .filter(RagActionPlanBinding::isSelectableCandidateBinding)
            .flatMap(RagActionPlanBinding::displayCandidates)
    } else {
        emptyList()
    }
    if (selectablePlanCandidates.isNotEmpty()) {
        return selectablePlanCandidates.toReviewCandidates()
    }

    val planCandidates = planBindings.flatMap(RagActionPlanBinding::displayCandidates)

    return (directCandidates + planCandidates).toReviewCandidates()
}

private fun List<RagCandidateItem>.toReviewCandidates(): List<RagReviewCandidate> =
    distinctBy { candidate ->
        candidate.nodeId?.toString()
            ?: candidate.path
            ?: candidate.name
            ?: ""
    }
        .take(20)
        .map { candidate ->
            RagReviewCandidate(
                id = candidate.nodeId?.toString() ?: candidate.name.orEmpty(),
                nodeId = candidate.nodeId,
                parentId = candidate.parentId,
                name = candidate.name.orEmpty().ifBlank { "未命名文件" },
                detail = candidate.detailLabel(),
                source = candidate.path.orEmpty(),
                type = candidate.type,
                size = candidate.size,
                extension = candidate.extension,
                mimeType = candidate.mimeType,
                updatedAt = candidate.updatedAt,
            )
        }

private fun List<RagReviewCandidate>.withOperationPreview(
    response: RagAssistantPlanResponse,
): List<RagReviewCandidate> {
    val actionType = response.actionPlan?.actionType ?: response.actionDraft?.type
    if (!actionType.equalsNormalized("collection.rename_add_prefix")) {
        return this
    }
    val prefix = response.entities
        ?.get("rename_prefix")
        ?.toString()
        ?.trim()
        .orEmpty()
    if (prefix.isBlank()) {
        return this
    }
    return map { candidate ->
        candidate.copy(detail = "原名称：${candidate.name}  ->  新名称：$prefix${candidate.name}")
    }
}

private fun RagAssistantPlanResponse.requiresFinalConfirmation(kind: RagReviewKind): Boolean {
    if (kind == RagReviewKind.FINAL_CONFIRMATION || kind == RagReviewKind.COLLECTION_REVIEW) {
        return true
    }
    if (safety?.requiresConfirmation == true) {
        return true
    }
    val plan = actionPlan
    return plan?.let { !it.risk.equalsNormalized("none") || !it.confirmationLevel.equalsNormalized("none") } == true ||
        backendActionDraft.shouldShowDraftLine()
}

private fun RagCandidateBinding.displayCandidates(): List<RagCandidateItem> =
    if (status.equalsNormalized("selected_candidate") && selectedCandidate != null) {
        listOf(selectedCandidate)
    } else {
        listOfNotNull(selectedCandidate) + candidates.orEmpty()
    }

private fun RagActionPlanBinding.displayCandidates(): List<RagCandidateItem> =
    if (status.equalsNormalized("selected_candidate") && selectedCandidate != null) {
        listOf(selectedCandidate)
    } else {
        listOfNotNull(selectedCandidate) + candidates.orEmpty()
    }

private fun RagCandidateBinding?.isSelectableCandidateBinding(): Boolean =
    this?.status?.trim()?.lowercase() in selectableCandidateStatuses

private fun RagActionPlanBinding.isSelectableCandidateBinding(): Boolean {
    if (isPreviewOnlyBinding()) {
        return false
    }

    val normalizedStatus = status?.trim()?.lowercase()
    return normalizedStatus in selectableCandidateStatuses ||
        normalizedStatus == "unresolved" && candidates.orEmpty().isNotEmpty()
}

private fun RagActionPlanBinding.isPreviewOnlyBinding(): Boolean =
    kind.equalsNormalized("source_collection") ||
        key.equalsNormalized("sourceCollection") ||
        key.equalsNormalized("searchResults")

private fun RagCandidateBinding?.hasDisplayCandidates(): Boolean =
    this?.selectedCandidate != null || this?.candidates.orEmpty().isNotEmpty()

private fun RagActionPlanBinding.hasDisplayCandidates(): Boolean =
    selectedCandidate != null || candidates.orEmpty().isNotEmpty() || (count ?: 0) > 0

private fun RagActionPlan.collectionReviewLines(): List<String> {
    if (!planKind.equalsNormalized("collection") && actionType?.startsWith("collection.") != true) {
        return emptyList()
    }

    val sourceCollection = bindings
        .orEmpty()
        .values
        .firstOrNull { binding ->
            binding.kind.equalsNormalized("source_collection") ||
                binding.key.equalsNormalized("sourceCollection")
        }
    val targetParent = bindings
        .orEmpty()
        .values
        .firstOrNull { binding ->
            binding.kind.equalsNormalized("target_folder") ||
                binding.key.equalsNormalized("targetParent")
        }
    val lines = mutableListOf<String>()

    sourceCollection?.let { binding ->
        val totalCount = binding.count ?: binding.candidates.orEmpty().size
        val previewCount = binding.candidates.orEmpty().size
        if (totalCount > 0) {
            lines += if (previewCount > 0 && previewCount < totalCount) {
                "影响范围：共 $totalCount 个项目，当前预览 $previewCount 个。"
            } else {
                "影响范围：共 $totalCount 个项目。"
            }
        }

        binding.filter.toFilterLine()?.let { lines += it }

        val previewIncomplete = binding.status.equalsNormalized("unresolved") ||
            messages.orEmpty().any { message -> message.code.equalsNormalized("preview_incomplete") } ||
            (binding.count ?: 0) > binding.candidates.orEmpty().size &&
            binding.candidates.orEmpty().isNotEmpty()
        if (previewIncomplete) {
            lines += "预览不完整，暂不允许提交批量操作。"
        }
    }

    targetParent
        ?.selectedCandidate
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?.let { lines += "目标目录：$it" }

    return lines
}

private fun Map<String, Any>?.toFilterLine(): String? {
    val filter = this.orEmpty()
    if (filter.isEmpty()) {
        return null
    }

    val pieces = listOfNotNull(
        filter.readableValue("nameContains")?.let { "名称包含“$it”" },
        filter.readableValue("extension")?.let { "后缀为 $it" },
        filter.readableValue("category")?.let { "类型为 $it" },
        filter.readableValue("timeRange")?.let { "时间范围 $it" },
    )

    return pieces
        .takeIf { it.isNotEmpty() }
        ?.joinToString("，")
        ?.let { "筛选条件：$it。" }
}

private fun Map<String, Any>.readableValue(key: String): String? =
    get(key)
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "null" && !it.startsWith("$") }

private fun RagCandidateItem.detailLabel(): String {
    val pathText = path?.takeIf { it.isNotBlank() }
    val typeText = type?.takeIf { it.isNotBlank() }
    val updatedText = updatedAt?.takeIf { it.isNotBlank() }

    return listOfNotNull(pathText, typeText, updatedText)
        .joinToString(" · ")
        .ifBlank { "RAG 候选结果" }
}

private fun String?.toPlanStatusLine(actionType: String): String? =
    when (this?.trim()?.lowercase()) {
        "clarification_required" -> "还需要补充一点信息，我会先问清楚。"
        "binding_required" -> if (actionType.equalsNormalized("search")) {
            "正在根据你的线索匹配文件或目录。"
        } else {
            "需要先匹配目标文件或目录。"
        }
        "candidate_selection_required" -> "匹配到多个候选，请先选择一个。"
        "collection_review_required" -> "已整理出批量操作范围，请先核对。"
        "review_required" -> "计划已准备好，确认后才会继续。"
        "client_input_required" -> "需要你在手机上补充文件或目标位置。"
        "ready_to_execute" -> "已准备好，等待你确认。"
        "completed" -> "已完成本轮处理。"
        else -> null
    }

private fun String?.toCandidateStatusLine(): String? =
    when (this?.trim()?.lowercase()) {
        "no_candidates" -> "没有找到匹配的文件或目录。"
        "missing_authorization" -> "登录状态不可用，请重新登录后再试。"
        "storage_api_not_configured" -> "文件索引服务还没有配置完成。"
        "storage_api_error" -> "候选查询暂时不可用，请稍后再试。"
        "multiple_candidates" -> "匹配到多个候选，请先选择一个。"
        "single_candidate" -> "已匹配到候选，确认后再继续。"
        "search_results_ready" -> "已完成文件检索，可展示候选结果。"
        else -> null
    }

private fun String?.toRiskLine(): String? =
    when (this?.trim()?.lowercase()) {
        null, "", "none" -> null
        "low" -> "风险较低，但仍会等你确认。"
        "medium" -> "会影响文件状态，请确认后再继续。"
        "high" -> "高风险操作，需要你仔细核对后确认。"
        else -> "需要你确认后再继续。"
    }

private fun String?.toReviewRisk(): RagReviewRisk =
    when (this?.trim()?.lowercase()) {
        null, "", "none" -> RagReviewRisk.NONE
        "low" -> RagReviewRisk.LOW
        "medium" -> RagReviewRisk.MEDIUM
        "high" -> RagReviewRisk.HIGH
        else -> RagReviewRisk.UNKNOWN
    }

private fun String.toPlanTitle(planKind: String?): String =
    when {
        equalsNormalized("search") -> "文件搜索计划"
        equalsNormalized("rename") -> "重命名计划"
        equalsNormalized("share") -> "分享计划"
        equalsNormalized("delete") || contains("trash", ignoreCase = true) -> "删除前确认"
        equalsNormalized("upload_target") -> "上传准备"
        contains("move", ignoreCase = true) -> "移动计划"
        contains("rename_add_prefix", ignoreCase = true) -> "批量重命名计划"
        planKind.equalsNormalized("collection") -> "批量整理计划"
        planKind.equalsNormalized("composite") -> "组合操作计划"
        else -> "操作计划"
    }

private fun String.toSafetyReminder(): String =
    when {
        equalsNormalized("search") -> "这一步只做查找，不会改动文件。"
        equalsNormalized("upload_target") -> "选择本地文件后才会上传，不会把本地文件交给 RAG。"
        equalsNormalized("composite.create_folder_then_upload") -> {
            "选择本地文件后，移动端会先创建文件夹再上传。"
        }
        startsWith("collection.trash") -> "确认后会批量移入回收站，后端仍会重新鉴权校验。"
        startsWith("collection.move") -> "确认后会批量移动到目标目录，后端仍会重新鉴权校验。"
        startsWith("collection.") -> "批量操作必须先核对范围，后端仍会重新鉴权校验。"
        else -> "第一阶段仅展示计划，不执行真实文件操作。"
    }

private fun String.toClientFieldLabel(): String =
    when (trim()) {
        "client_file", "file" -> "要上传的本地文件"
        "target_folder" -> "目标文件夹"
        "target_name" -> "目标名称"
        else -> this
    }

private fun RagBackendActionDraft?.toBackendOnlyLines(actionType: String): List<String> =
    listOfNotNull(
        this?.message?.takeIf { it.isNotBlank() },
        this?.takeIf(RagBackendActionDraft::shouldShowDraftLine)
            ?.let { "已准备受控执行草稿，确认后仍会由云盘后端校验。" },
        actionType.toSafetyReminder(),
    )

private fun RagBackendActionDraft?.hasExecutableSignal(): Boolean {
    if (this == null) {
        return false
    }

    val actionIsExecutable = actionType
        ?.takeIf { it.isNotBlank() }
        ?.let { !it.equalsNormalized("none") }
        ?: false

    return executableByBackend == true ||
        method?.isNotBlank() == true ||
        path?.isNotBlank() == true ||
        pathTemplate?.isNotBlank() == true ||
        actionIsExecutable
}

private fun RagBackendActionDraft?.shouldShowDraftLine(): Boolean =
    this != null && !status.equalsNormalized("not_requested") && hasExecutableSignal()

private fun String?.equalsNormalized(expected: String): Boolean =
    this?.trim()?.equals(expected, ignoreCase = true) == true

private fun String?.isBlankOrNone(): Boolean =
    this == null || trim().isBlank() || equalsNormalized("none")

private val blockedCandidateStatuses = setOf(
    "no_candidates",
    "missing_authorization",
    "storage_api_not_configured",
    "storage_api_error",
)

private val selectableCandidateStatuses = setOf(
    "multiple_candidates",
    "candidate_selection_required",
    "candidate_selection_out_of_range",
)
