package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.RagActionDraft
import com.alicia.cloudstorage.phone.data.RagActionPlan
import com.alicia.cloudstorage.phone.data.RagActionPlanBinding
import com.alicia.cloudstorage.phone.data.RagActionPlanMessage
import com.alicia.cloudstorage.phone.data.RagAssistantPlanResponse
import com.alicia.cloudstorage.phone.data.RagAssistantInteraction
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagCandidateBinding
import com.alicia.cloudstorage.phone.data.RagCandidateItem
import com.alicia.cloudstorage.phone.data.RagSemanticClarification
import com.alicia.cloudstorage.phone.data.RagSemanticFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagReviewPresenterTest {
    private val presenter = RagReviewPresenter()

    @Test
    fun `does not present pure respond only replies`() {
        assertNull(presenter.present(response(actionPlan = plan())))
    }

    @Test
    fun `does not present non operational fallback clarification replies`() {
        val review = presenter.present(
            response(
                nextAction = "ask_clarification",
                actionPlan = plan(
                    status = "clarification_required",
                    actionType = "none",
                    risk = "none",
                ),
            ),
        )

        assertNull(review)
    }

    @Test
    fun `semantic ambiguity suppresses stale operational plan`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_backend_binding",
                actionDraft = RagActionDraft(
                    type = "share",
                    parameters = mapOf("target_name" to "分享根目录下所有文件"),
                    needsBackendBinding = true,
                ),
                actionPlan = plan(
                    status = "binding_required",
                    actionType = "node.share",
                    risk = "medium",
                ),
                semanticFrame = RagSemanticFrame(
                    schemaVersion = "semantic_frame_v2",
                    relation = "NEW_TASK",
                    operation = "SHARE",
                    query = null,
                    scope = null,
                    reference = null,
                    confidence = 1.0,
                    ambiguities = listOf("batch_share_unsupported"),
                    clarification = RagSemanticClarification(
                        reason = "batch_share_unsupported",
                        question = "目前一次只能分享一个文件。",
                        suggestions = emptyList(),
                    ),
                ),
            ),
        )

        assertNull(review)
    }

    @Test
    fun `presents multiple candidates as candidate selection`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_candidate_selection",
                actionDraft = RagActionDraft(
                    type = "rename",
                    parameters = emptyMap(),
                    needsBackendBinding = true,
                ),
                actionPlan = plan(
                    status = "candidate_selection_required",
                    actionType = "rename",
                    risk = "medium",
                    bindings = mapOf(
                        "source" to RagActionPlanBinding(
                            key = "source",
                            kind = "candidate",
                            status = "candidate_selection_required",
                            query = "项目",
                            selectedCandidate = null,
                            candidates = listOf(candidate(1L, "项目.docx"), candidate(2L, "项目备份.docx")),
                            count = 2,
                            filter = null,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(RagReviewKind.CANDIDATE_SELECTION, review!!.kind)
        assertEquals(2, review.candidates.size)
        assertEquals(RagReviewRisk.MEDIUM, review.risk)
        assertTrue(review.requiresFinalConfirmation)
        assertTrue(review.lines.contains("匹配到多个候选，请先选择一个。"))
    }

    @Test
    fun `server confirmation stage wins over historical candidate count`() {
        val selected = candidate(1L, "测试目录", "FOLDER")
        val review = presenter.present(
            response(
                nextAction = "wait_for_user_confirmation",
                actionDraft = RagActionDraft(
                    type = "upload_target",
                    parameters = mapOf("target_folder" to "测试目录"),
                    needsBackendBinding = true,
                ),
                actionPlan = plan(
                    status = "review_required",
                    actionType = "file.upload",
                    risk = "low",
                ),
                candidateBinding = RagCandidateBinding(
                    status = "selected_candidate",
                    source = "storage_api",
                    query = "测试目录",
                    candidateType = "FOLDER",
                    candidates = listOf(selected, candidate(2L, "测试目录2", "FOLDER")),
                    message = "已选择测试目录",
                    selectedCandidate = selected,
                    selectedIndex = 1,
                ),
                interaction = RagAssistantInteraction(
                    stage = "NEED_CONFIRMATION",
                    allowedActions = emptyList(),
                    clarification = null,
                ),
            ),
        )

        assertEquals(RagReviewKind.FINAL_CONFIRMATION, review!!.kind)
        assertEquals(listOf("测试目录"), review.candidates.map { it.name })
        assertTrue(review.requiresFinalConfirmation)
    }

    @Test
    fun `presents rename review as final confirmation`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_user_confirmation",
                actionDraft = RagActionDraft(
                    type = "rename",
                    parameters = mapOf("new_name" to "final.docx"),
                    needsBackendBinding = false,
                ),
                actionPlan = plan(
                    status = "review_required",
                    actionType = "rename",
                    risk = "medium",
                ),
                backendActionDraft = backendDraft(
                    actionType = "rename",
                    method = "PUT",
                    pathTemplate = "/api/storage/nodes/{nodeId}/rename",
                    confirmedByUser = false,
                ),
            ),
        )

        assertEquals(RagReviewKind.FINAL_CONFIRMATION, review!!.kind)
        assertEquals("重命名计划", review.title)
        assertTrue(review.requiresFinalConfirmation)
        assertTrue(review.lines.contains("会影响文件状态，请确认后再继续。"))
        assertTrue(review.lines.contains("已准备受控执行草稿，确认后仍会由云盘后端校验。"))
    }

    @Test
    fun `presents upload target as client input`() {
        val review = presenter.present(
            response(
                nextAction = "handoff_to_client_upload",
                actionDraft = RagActionDraft(
                    type = "upload_target",
                    parameters = emptyMap(),
                    needsBackendBinding = false,
                ),
                actionPlan = plan(
                    status = "client_input_required",
                    actionType = "upload_target",
                    risk = "low",
                    requiredClientFields = listOf("client_file"),
                ),
                backendActionDraft = backendDraft(
                    actionType = "upload_target",
                    method = "POST",
                    pathTemplate = "/api/storage/files",
                    executableByBackend = false,
                    requiredClientFields = listOf("file"),
                    status = "client_action_required",
                ),
            ),
        )

        assertEquals(RagReviewKind.CLIENT_INPUT, review!!.kind)
        assertTrue(review.requiresFinalConfirmation)
        assertTrue(review.lines.contains("还需要补充：要上传的本地文件"))
    }

    @Test
    fun `presents collection review with count filter preview and target folder`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_user_confirmation",
                actionDraft = RagActionDraft(
                    type = "collection.move_by_extension",
                    parameters = emptyMap(),
                    needsBackendBinding = false,
                ),
                actionPlan = plan(
                    status = "collection_review_required",
                    actionType = "collection.move_by_extension",
                    risk = "medium",
                    planKind = "collection",
                    bindings = mapOf(
                        "sourceCollection" to RagActionPlanBinding(
                            key = "sourceCollection",
                            kind = "source_collection",
                            status = "resolved",
                            query = "pdf",
                            selectedCandidate = null,
                            candidates = listOf(candidate(11L, "方案.pdf"), candidate(12L, "合同.pdf")),
                            count = 2,
                            filter = mapOf("extension" to "PDF"),
                        ),
                        "targetParent" to RagActionPlanBinding(
                            key = "targetParent",
                            kind = "target_folder",
                            status = "resolved",
                            query = "归档",
                            selectedCandidate = candidate(99L, "归档", type = "FOLDER"),
                            candidates = emptyList(),
                            count = 1,
                            filter = null,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(RagReviewKind.COLLECTION_REVIEW, review!!.kind)
        assertTrue(review.requiresFinalConfirmation)
        assertTrue(review.lines.contains("影响范围：共 2 个项目。"))
        assertTrue(review.lines.contains("筛选条件：后缀为 PDF。"))
        assertTrue(review.lines.contains("目标目录：归档"))
        assertTrue(review.lines.contains("确认后会批量移动到目标目录，后端仍会重新鉴权校验。"))
    }

    @Test
    fun `presents direct and subtree impact for scoped folder deletion`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_user_confirmation",
                actionDraft = RagActionDraft(
                    type = "collection.trash_scoped",
                    parameters = emptyMap(),
                    needsBackendBinding = false,
                ),
                actionPlan = plan(
                    status = "collection_review_required",
                    actionType = "collection.trash_scoped",
                    risk = "high",
                    planKind = "collection",
                    bindings = mapOf(
                        "sourceCollection" to RagActionPlanBinding(
                            key = "sourceCollection",
                            kind = "source_collection",
                            status = "resolved",
                            query = "/",
                            selectedCandidate = null,
                            candidates = listOf(
                                candidate(11L, "说明.txt"),
                                candidate(12L, "资料", type = "FOLDER"),
                            ),
                            count = 2,
                            filter = mapOf(
                                "snapshotId" to "cs-1",
                                "selectedFileCount" to 1,
                                "selectedFolderCount" to 1,
                                "descendantCount" to 3,
                                "impactCount" to 5,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(review!!.lines.contains("直属范围：1 个文件、1 个文件夹。"))
        assertTrue(review.lines.contains("实际影响：共 5 个节点，其中包含 3 个文件夹内部节点。"))
        assertTrue(review.lines.contains("确认后会批量移入回收站，后端仍会重新鉴权校验。"))
    }

    @Test
    fun `candidate selection for collection target folder excludes source preview`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_candidate_selection",
                actionDraft = RagActionDraft(
                    type = "collection.move_by_name_contains",
                    parameters = emptyMap(),
                    needsBackendBinding = true,
                ),
                candidateBinding = RagCandidateBinding(
                    status = "multiple_candidates",
                    source = "cloud-storage-api:/api/storage/nodes",
                    query = "归档",
                    candidateType = "FOLDER",
                    candidates = listOf(
                        candidate(301L, "归档", type = "FOLDER"),
                        candidate(302L, "归档备份", type = "FOLDER"),
                    ),
                    message = "匹配到多个目标目录，需要用户选择。",
                    selectedCandidate = null,
                    selectedIndex = null,
                ),
                actionPlan = plan(
                    status = "candidate_selection_required",
                    actionType = "collection.move_by_name_contains",
                    risk = "medium",
                    planKind = "collection",
                    bindings = mapOf(
                        "sourceCollection" to RagActionPlanBinding(
                            key = "sourceCollection",
                            kind = "source_collection",
                            status = "resolved",
                            query = "方案",
                            selectedCandidate = null,
                            candidates = listOf(candidate(11L, "方案.pdf")),
                            count = 1,
                            filter = mapOf("nameContains" to "方案"),
                        ),
                        "targetParent" to RagActionPlanBinding(
                            key = "targetParent",
                            kind = "target_folder",
                            status = "multiple_candidates",
                            query = "归档",
                            selectedCandidate = null,
                            candidates = listOf(
                                candidate(301L, "归档", type = "FOLDER"),
                                candidate(302L, "归档备份", type = "FOLDER"),
                            ),
                            count = 2,
                            filter = null,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(RagReviewKind.CANDIDATE_SELECTION, review!!.kind)
        assertEquals(listOf("归档", "归档备份"), review.candidates.map { it.name })
        assertFalse(review.candidates.any { it.name == "方案.pdf" })
    }

    @Test
    fun `presents incomplete collection preview as not submittable`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_backend_binding",
                actionDraft = RagActionDraft(
                    type = "collection.trash_by_name_contains",
                    parameters = emptyMap(),
                    needsBackendBinding = false,
                ),
                actionPlan = plan(
                    status = "binding_required",
                    actionType = "collection.trash_by_name_contains",
                    risk = "high",
                    planKind = "collection",
                    bindings = mapOf(
                        "sourceCollection" to RagActionPlanBinding(
                            key = "sourceCollection",
                            kind = "source_collection",
                            status = "unresolved",
                            query = "测试",
                            selectedCandidate = null,
                            candidates = listOf(candidate(21L, "测试-扫描内.txt")),
                            count = 50,
                            filter = mapOf("nameContains" to "测试", "includeFolders" to false),
                        ),
                    ),
                    messages = listOf(
                        RagActionPlanMessage(
                            level = "warning",
                            code = "preview_incomplete",
                            text = "预览不完整，需要缩小范围。",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(RagReviewKind.COLLECTION_REVIEW, review!!.kind)
        assertTrue(review.lines.contains("影响范围：共 50 个项目，当前预览 1 个。"))
        assertTrue(review.lines.contains("筛选条件：名称包含“测试”。"))
        assertTrue(review.lines.contains("预览不完整，暂不允许提交批量操作。"))
    }

    @Test
    fun `presents storage lookup failures as blocked review`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_backend_binding",
                actionDraft = RagActionDraft(
                    type = "search",
                    parameters = emptyMap(),
                    needsBackendBinding = true,
                ),
                actionPlan = plan(
                    status = "binding_required",
                    actionType = "search",
                    risk = "none",
                ),
                candidateBinding = RagCandidateBinding(
                    status = "no_candidates",
                    source = "cloud-storage-api:/api/storage/nodes",
                    query = "project",
                    candidateType = "NODE",
                    candidates = emptyList(),
                    message = "未匹配到候选文件或目录。",
                    selectedCandidate = null,
                    selectedIndex = null,
                ),
            ),
        )

        assertEquals(RagReviewKind.BLOCKED, review!!.kind)
        assertTrue(review.lines.contains("没有找到匹配的文件或目录。"))
        assertFalse(review.requiresFinalConfirmation)
    }

    @Test
    fun `search count follows the deduplicated candidates shown to the user`() {
        val candidates = listOf(
            candidate(31L, "封面.png"),
            candidate(32L, "海报.jpg"),
            candidate(33L, "头像.webp"),
        )
        val review = presenter.present(
            response(
                nextAction = "show_search_results",
                actionDraft = RagActionDraft(
                    type = "search",
                    parameters = emptyMap(),
                    needsBackendBinding = true,
                ),
                actionPlan = plan(
                    status = "completed",
                    actionType = "search",
                    bindings = mapOf(
                        "searchResults" to RagActionPlanBinding(
                            key = "searchResults",
                            kind = "candidate",
                            status = "resolved",
                            query = "全部云盘",
                            selectedCandidate = null,
                            candidates = candidates.take(1),
                            count = 1,
                            filter = null,
                        ),
                    ),
                ),
                candidateBinding = RagCandidateBinding(
                    status = "search_results_ready",
                    source = "cloud-storage-api:/api/storage/nodes",
                    query = "全部云盘",
                    candidateType = "FILE",
                    candidates = candidates,
                    message = "已列出 3 个图片文件。",
                    selectedCandidate = null,
                    selectedIndex = null,
                ),
            ),
        )

        assertEquals(RagReviewKind.SEARCH_RESULTS, review!!.kind)
        assertEquals(3, review.candidates.size)
        assertTrue(review.lines.contains("已找到 3 个候选，先核对再继续。"))
        assertFalse(review.lines.contains("已找到 1 个候选，先核对再继续。"))
    }

    @Test
    fun `batch rename review shows old and new names`() {
        val review = presenter.present(
            response(
                nextAction = "wait_for_user_confirmation",
                actionDraft = RagActionDraft(
                    type = "collection.rename_add_prefix",
                    parameters = emptyMap(),
                    needsBackendBinding = true,
                ),
                actionPlan = plan(
                    status = "collection_review_required",
                    actionType = "collection.rename_add_prefix",
                    risk = "high",
                    planKind = "collection",
                    bindings = mapOf(
                        "sourceCollection" to RagActionPlanBinding(
                            key = "sourceCollection",
                            kind = "source_collection",
                            status = "resolved",
                            query = "合同",
                            selectedCandidate = null,
                            candidates = listOf(candidate(1L, "合同.pdf")),
                            count = 1,
                            filter = mapOf("nameContains" to "合同"),
                        ),
                    ),
                ),
                entities = mapOf("rename_prefix" to "归档-"),
            ),
        )

        assertEquals("原名称：合同.pdf  ->  新名称：归档-合同.pdf", review!!.candidates.single().detail)
    }

    private fun response(
        nextAction: String = "respond_only",
        actionDraft: RagActionDraft? = RagActionDraft(
            type = "none",
            parameters = emptyMap(),
            needsBackendBinding = false,
        ),
        actionPlan: RagActionPlan? = null,
        backendActionDraft: RagBackendActionDraft? = backendDraft(
            actionType = "none",
            method = null,
            pathTemplate = null,
            status = "not_requested",
            executableByBackend = false,
            confirmedByUser = false,
        ),
        candidateBinding: RagCandidateBinding? = null,
        interaction: RagAssistantInteraction? = null,
        entities: Map<String, Any>? = emptyMap(),
        semanticFrame: RagSemanticFrame? = null,
    ) = RagAssistantPlanResponse(
        id = "response-1",
        schemaVersion = "intent_recognition_v1",
        templateId = "local",
        provider = "local",
        model = "rules",
        message = "hello",
        intentId = "assistant_social",
        intentName = "assistant_social",
        taskType = null,
        confidence = 1.0,
        userGoal = null,
        normalizedQuery = null,
        entities = entities,
        requiredSlots = emptyList(),
        missingSlots = emptyList(),
        nextAction = nextAction,
        safety = null,
        actionDraft = actionDraft,
        backendActionDraft = backendActionDraft,
        actionPlan = actionPlan,
        assistantText = "hello",
        clarificationQuestion = null,
        reason = null,
        fallbackReason = null,
        candidateBinding = candidateBinding,
        conversation = null,
        semanticFrame = semanticFrame,
        interaction = interaction,
    )

    private fun plan(
        status: String = "completed",
        actionType: String = "none",
        risk: String = "none",
        planKind: String = "atomic",
        bindings: Map<String, RagActionPlanBinding>? = emptyMap(),
        requiredClientFields: List<String>? = emptyList(),
        messages: List<RagActionPlanMessage>? = emptyList(),
    ) = RagActionPlan(
        version = "1",
        planId = "plan-1",
        status = status,
        planKind = planKind,
        actionType = actionType,
        risk = risk,
        confirmationLevel = "none",
        locale = "zh-CN",
        bindings = bindings,
        steps = emptyList(),
        requiredClientFields = requiredClientFields,
        summary = "hello",
        messages = messages,
    )

    private fun candidate(nodeId: Long, name: String, type: String = "FILE") =
        RagCandidateItem(
            nodeId = nodeId,
            parentId = null,
            name = name,
            type = type,
            size = 1L,
            extension = "docx",
            mimeType = "application/docx",
            updatedAt = "2026-08-11T10:00:00",
            path = "/文档/$name",
            breadcrumbs = emptyList(),
        )

    private fun backendDraft(
        actionType: String,
        method: String?,
        pathTemplate: String?,
        status: String = "backend_action_ready",
        executableByBackend: Boolean = true,
        confirmedByUser: Boolean = true,
        requiredClientFields: List<String>? = emptyList(),
    ) = RagBackendActionDraft(
        status = status,
        bridgeVersion = "action_bridge_v2",
        actionType = actionType,
        nextAction = "handoff_to_backend",
        confirmedByUser = confirmedByUser,
        executableByBackend = executableByBackend,
        authorizationRequired = true,
        method = method,
        pathTemplate = pathTemplate,
        path = null,
        contentType = "application/json",
        pathVariables = mapOf("nodeId" to 1L),
        queryParameters = emptyMap(),
        body = emptyMap(),
        requiredClientFields = requiredClientFields,
        targetCandidate = null,
        message = null,
    )
}
