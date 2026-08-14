package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.RagActionBridgeValidation
import com.alicia.cloudstorage.phone.data.RagActionBridgeValidationCode
import com.alicia.cloudstorage.phone.data.RagActionDraft
import com.alicia.cloudstorage.phone.data.RagActionExecutionResult
import com.alicia.cloudstorage.phone.data.RagActionExecutionStatus
import com.alicia.cloudstorage.phone.data.RagActionPlan
import com.alicia.cloudstorage.phone.data.RagActionPlanBinding
import com.alicia.cloudstorage.phone.data.RagActionPlanStep
import com.alicia.cloudstorage.phone.data.RagAssistantPlanResponse
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagCandidateBinding
import com.alicia.cloudstorage.phone.data.RagCandidateItem
import com.alicia.cloudstorage.phone.data.RagCandidateResultPage
import com.alicia.cloudstorage.phone.data.RagSemanticFrame
import com.alicia.cloudstorage.phone.data.RagSemanticQuery
import com.alicia.cloudstorage.phone.data.RagSemanticScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagAssistantMessageMapperTest {

    @Test
    fun `respond only message has no review controls`() {
        val message = response().toAssistantMessage(id = 20L)

        assertNull(message.plan)
    }

    @Test
    fun `fallback clarification message has no review controls`() {
        val message = response(
            intentId = "fallback",
            nextAction = "ask_clarification",
            actionPlan = plan(
                status = "clarification_required",
                actionType = "none",
                risk = "none",
            ),
        ).toAssistantMessage(id = 23L)

        assertNull(message.plan)
        assertTrue(message.files.isEmpty())
    }

    @Test
    fun `reviewable backend draft exposes confirm controls`() {
        val message = response(
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
            ),
        ).toAssistantMessage(id = 21L)

        val controls = message.plan?.actionControls
        assertNotNull(controls)
        assertEquals("确认执行", controls!!.confirmLabel)
        assertEquals("取消", controls.cancelLabel)
        assertFalse(controls.destructive)
    }

    @Test
    fun `review plan without backend draft asks rag for draft first`() {
        val message = response(
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
            backendActionDraft = null,
        ).toAssistantMessage(id = 22L)

        assertNotNull(message.plan)
        assertEquals("确认计划", message.plan!!.actionControls?.confirmLabel)
    }

    @Test
    fun `upload target exposes client upload action only`() {
        val message = response(
            intentId = "file_upload",
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
                requiredClientFields = listOf("files"),
            ),
            backendActionDraft = backendDraft(
                actionType = "upload_target",
                method = "POST",
                pathTemplate = "/api/storage/files",
                status = "client_action_required",
                nextAction = "handoff_to_client_upload",
                executableByBackend = false,
                contentType = "multipart/form-data",
                queryParameters = mapOf("parentId" to 501L),
                requiredClientFields = listOf("file"),
                targetCandidate = candidate(501L, "项目资料", type = "FOLDER"),
            ),
        ).toAssistantMessage(id = 27L)

        val plan = message.plan
        assertNotNull(plan)
        assertNull(plan!!.actionControls)
        val clientControls = plan.clientActionControls ?: error("Expected client upload controls.")
        assertEquals("选择文件并上传", clientControls.label)
        assertEquals(501L, clientControls.uploadRequest.parentId)
        assertEquals("项目资料", clientControls.uploadRequest.targetName)
    }

    @Test
    fun `prepared upload target exposes confirmation and keeps virtual root navigable`() {
        val root = RagCandidateItem(
            nodeId = null,
            parentId = null,
            name = "根目录",
            type = "FOLDER",
            size = 0L,
            extension = null,
            mimeType = null,
            updatedAt = null,
            path = "/",
            breadcrumbs = emptyList(),
        )
        val message = response(
            intentId = "file_upload",
            nextAction = "wait_for_user_confirmation",
            actionDraft = RagActionDraft(
                type = "upload_target",
                parameters = mapOf("target_folder" to "根目录"),
                needsBackendBinding = false,
            ),
            actionPlan = plan(
                status = "review_required",
                actionType = "file.upload",
                risk = "low",
                bindings = mapOf(
                    "targetParent" to RagActionPlanBinding(
                        key = "targetParent",
                        kind = "target_folder",
                        status = "single_candidate",
                        query = "根目录",
                        selectedCandidate = root,
                        candidates = listOf(root),
                        count = 1,
                        filter = emptyMap(),
                    ),
                ),
                requiredClientFields = emptyList(),
            ),
            backendActionDraft = null,
        ).toAssistantMessage(id = 31L)

        val controls = message.plan?.clientActionControls ?: error("Expected upload confirmation.")
        assertNull(message.plan.actionControls)
        assertEquals("确认上传", controls.label)
        assertNull(controls.uploadRequest.parentId)
        assertEquals("根目录", controls.uploadRequest.targetName)
        assertEquals("/", message.files.single().path)
        assertEquals(
            AiChatFolderOpenTarget(nodeId = null, name = "根目录"),
            message.files.single().toFolderOpenTargetOrNull(),
        )
    }

    @Test
    fun `composer attachments stay available while upload target is unresolved`() {
        val response = response(
            intentId = "file_upload",
            nextAction = "wait_for_backend_binding",
            actionDraft = RagActionDraft(
                type = "upload_target",
                parameters = mapOf("target_folder" to "项目资料"),
                needsBackendBinding = true,
            ),
            actionPlan = plan(
                status = "binding_required",
                actionType = "upload_target",
                risk = "low",
            ),
        )

        assertTrue(response.shouldRetainComposerAttachments())
    }

    @Test
    fun `composer attachments clear after non upload intent response`() {
        val response = response(
            intentId = "file_delete",
            nextAction = "wait_for_user_confirmation",
            actionDraft = RagActionDraft(
                type = "delete",
                parameters = mapOf("target_name" to "旅行照片"),
                needsBackendBinding = true,
            ),
            actionPlan = plan(
                status = "review_required",
                actionType = "delete",
                risk = "high",
            ),
        )

        assertFalse(response.shouldRetainComposerAttachments())
    }

    @Test
    fun `create folder then upload exposes client orchestration action only`() {
        val message = response(
            intentId = "folder_create_then_upload",
            nextAction = "wait_for_user_confirmation",
            actionDraft = RagActionDraft(
                type = "composite.create_folder_then_upload",
                parameters = mapOf(
                    "target_folder" to "项目资料",
                    "new_folder_name" to "归档",
                ),
                needsBackendBinding = false,
            ),
            actionPlan = plan(
                status = "review_required",
                actionType = "composite.create_folder_then_upload",
                risk = "medium",
                planKind = "composite",
                bindings = mapOf(
                    "targetParent" to RagActionPlanBinding(
                        key = "targetParent",
                        kind = "target_folder",
                        status = "resolved",
                        query = "项目资料",
                        selectedCandidate = candidate(901L, "项目资料", type = "FOLDER"),
                        candidates = emptyList(),
                        count = 1,
                        filter = emptyMap(),
                    ),
                ),
                steps = listOf(
                    RagActionPlanStep(
                        stepId = "create_folder",
                        action = "folder.create",
                        status = "ready",
                        params = mapOf(
                            "parentId" to 901L,
                            "folderName" to "归档",
                        ),
                        dependsOn = emptyList(),
                        requiredClientFields = emptyList(),
                        outputKey = "createdFolder",
                    ),
                    RagActionPlanStep(
                        stepId = "upload_files",
                        action = "file.upload",
                        status = "blocked",
                        params = mapOf("parentId" to "\$steps.create_folder.outputs.nodeId"),
                        dependsOn = listOf("create_folder"),
                        requiredClientFields = listOf("files"),
                        outputKey = null,
                    ),
                ),
                requiredClientFields = listOf("files"),
            ),
            backendActionDraft = null,
        ).toAssistantMessage(id = 28L)

        val plan = message.plan
        assertNotNull(plan)
        assertNull(plan!!.actionControls)
        val clientControls = plan.clientActionControls ?: error("Expected create-then-upload controls.")
        assertEquals("新建并选择文件", clientControls.label)
        assertEquals(901L, clientControls.uploadRequest.parentId)
        assertEquals("项目资料/归档", clientControls.uploadRequest.targetName)
        assertEquals("归档", clientControls.uploadRequest.createFolderName)
    }

    @Test
    fun `candidate selection rows send ordinal choice back to rag`() {
        val message = response(
            nextAction = "wait_for_candidate_selection",
            actionDraft = RagActionDraft(
                type = "delete",
                parameters = emptyMap(),
                needsBackendBinding = true,
            ),
            actionPlan = plan(
                status = "candidate_selection_required",
                actionType = "delete",
                risk = "high",
            ),
            candidateBinding = RagCandidateBinding(
                status = "multiple_candidates",
                source = "cloud-storage-api:/api/storage/nodes",
                query = "临时截图",
                candidateType = "FILE",
                candidates = listOf(
                    candidate(101L, "临时截图-old.png"),
                    candidate(102L, "临时截图-new.png"),
                ),
                message = "匹配到多个候选，需要用户选择。",
                selectedCandidate = null,
                selectedIndex = null,
            ),
        ).toAssistantMessage(id = 25L)

        assertEquals(2, message.files.size)
        assertEquals("选第1个", message.files[0].selectionAction!!.requestMessage)
        assertEquals("选择第 2 个：临时截图-new.png", message.files[1].selectionAction!!.displayText)
        assertEquals(AiChatResultMode.CANDIDATE_SELECTION, message.resultSection?.mode)
        assertFalse(
            AiChatResultDisplayPolicy.canCollapse(
                message.resultSection ?: error("Expected candidate result section."),
                message.files.size,
            ),
        )
    }

    @Test
    fun `collection target folder selection rows exclude source preview files`() {
        val message = response(
            nextAction = "wait_for_candidate_selection",
            actionDraft = RagActionDraft(
                type = "collection.move_by_name_contains",
                parameters = emptyMap(),
                needsBackendBinding = true,
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
                        candidates = listOf(candidate(401L, "方案.pdf")),
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
                            candidate(501L, "归档", type = "FOLDER"),
                            candidate(502L, "归档备份", type = "FOLDER"),
                        ),
                        count = 2,
                        filter = null,
                    ),
                ),
            ),
            candidateBinding = RagCandidateBinding(
                status = "multiple_candidates",
                source = "cloud-storage-api:/api/storage/nodes",
                query = "归档",
                candidateType = "FOLDER",
                candidates = listOf(
                    candidate(501L, "归档", type = "FOLDER"),
                    candidate(502L, "归档备份", type = "FOLDER"),
                ),
                message = "匹配到多个目标目录，需要用户选择。",
                selectedCandidate = null,
                selectedIndex = null,
            ),
        ).toAssistantMessage(id = 29L)

        assertEquals(listOf("归档", "归档备份"), message.files.map { it.name })
        assertEquals("选第1个", message.files[0].selectionAction!!.requestMessage)
        assertEquals("选第2个", message.files[1].selectionAction!!.requestMessage)
    }

    @Test
    fun `search result rows stay read only`() {
        val message = response(
            nextAction = "show_search_results",
            actionDraft = RagActionDraft(
                type = "search",
                parameters = emptyMap(),
                needsBackendBinding = true,
            ),
            actionPlan = plan(
                status = "completed",
                actionType = "search",
                risk = "none",
            ),
            candidateBinding = RagCandidateBinding(
                status = "search_results_ready",
                source = "cloud-storage-api:/api/storage/nodes",
                query = "合同",
                candidateType = "ANY",
                candidates = listOf(candidate(201L, "合同-2026.docx")),
                message = "已匹配到 1 个候选，可展示给用户。",
                selectedCandidate = null,
                selectedIndex = null,
                pageInfo = RagCandidateResultPage(
                    totalCount = 12,
                    returnedCount = 1,
                    hasMore = true,
                    sortBy = "updatedAt",
                    sortDirection = "desc",
                ),
            ),
        ).toAssistantMessage(id = 26L)

        assertEquals(1, message.files.size)
        assertNull(message.files[0].selectionAction)
        assertNull(message.plan)
        assertEquals(AiChatResultMode.SEARCH_RESULTS, message.resultSection?.mode)
        assertEquals(12L, message.resultSection?.totalCount)
        assertTrue(message.resultSection?.hasMore == true)
    }

    @Test
    fun `search results keep every returned candidate for local expansion`() {
        val candidates = (1L..13L).map { index ->
            candidate(index, "文档-$index.pdf")
        }
        val message = response(
            nextAction = "show_search_results",
            actionDraft = RagActionDraft(
                type = "search",
                parameters = emptyMap(),
                needsBackendBinding = true,
            ),
            actionPlan = plan(
                status = "completed",
                actionType = "search",
                risk = "none",
            ),
            candidateBinding = RagCandidateBinding(
                status = "search_results_ready",
                source = "cloud-storage-api:/api/storage/nodes",
                query = "全部云盘",
                candidateType = "FILE",
                candidates = candidates,
                message = "已列出 13 个文件。",
                selectedCandidate = null,
                selectedIndex = null,
                pageInfo = RagCandidateResultPage(
                    totalCount = 13,
                    returnedCount = 13,
                    hasMore = false,
                    sortBy = "updatedAt",
                    sortDirection = "desc",
                ),
            ),
            semanticFrame = RagSemanticFrame(
                schemaVersion = "semantic_frame_v2",
                relation = "NEW_TASK",
                operation = "SEARCH",
                query = RagSemanticQuery(
                    mode = "DIRECTORY_LIST",
                    resultType = "FILE",
                    nameSurface = null,
                    nameNormalized = null,
                    filters = mapOf("category" to "DOCUMENT"),
                ),
                scope = null,
                reference = null,
                confidence = 1.0,
                ambiguities = emptyList(),
                clarification = null,
            ),
        ).toAssistantMessage(id = 32L)

        assertEquals(13, message.files.size)
        assertEquals(13L, message.resultSection?.totalCount)
        assertEquals("全部云盘", message.resultSection?.contextLabel)
        assertEquals("文档结果", message.resultSection?.title)
    }

    @Test
    fun `incomplete collection preview stays read only`() {
        val message = response(
            nextAction = "wait_for_backend_binding",
            actionDraft = RagActionDraft(
                type = "collection.trash_by_category",
                parameters = emptyMap(),
                needsBackendBinding = false,
            ),
            actionPlan = plan(
                status = "binding_required",
                actionType = "collection.trash_by_category",
                risk = "high",
                planKind = "collection",
                bindings = mapOf(
                    "sourceCollection" to RagActionPlanBinding(
                        key = "sourceCollection",
                        kind = "source_collection",
                        status = "unresolved",
                        query = "图片",
                        selectedCandidate = null,
                        candidates = listOf(candidate(301L, "照片.png")),
                        count = 50,
                        filter = mapOf("category" to "image"),
                    ),
                ),
            ),
        ).toAssistantMessage(id = 30L)

        assertNotNull(message.plan)
        assertNull(message.plan!!.actionControls)
    }

    @Test
    fun `destructive draft marks confirm control`() {
        val message = response(
            nextAction = "wait_for_user_confirmation",
            actionDraft = RagActionDraft(
                type = "delete",
                parameters = emptyMap(),
                needsBackendBinding = false,
            ),
            actionPlan = plan(
                status = "review_required",
                actionType = "delete",
                risk = "high",
            ),
            backendActionDraft = backendDraft(
                actionType = "delete",
                method = "DELETE",
                pathTemplate = "/api/storage/nodes/{nodeId}",
            ),
        ).toAssistantMessage(id = 23L)

        assertTrue(message.plan!!.actionControls!!.destructive)
    }

    @Test
    fun `disabled executor result explains no file mutation`() {
        val message = RagActionExecutionResult(
            status = RagActionExecutionStatus.DISABLED,
            actionType = "rename",
            message = "AI 文件操作执行入口尚未启用。",
            validation = RagActionBridgeValidation(
                allowed = true,
                code = RagActionBridgeValidationCode.ALLOWED,
                message = "ok",
            ),
        ).toAssistantMessage(id = 24L)

        assertTrue(message.text.contains("默认关闭"))
        assertTrue(message.text.contains("不会改动文件"))
    }

    @Test
    fun `completed delete result emits files and trash refresh signal`() {
        val signal = completedResult(
            actionType = "delete",
            affectedNodeIds = listOf(42L, 42L),
        ).toFileMutationSignal()

        assertNotNull(signal)
        assertEquals("delete", signal!!.actionType)
        assertEquals(listOf(42L), signal.affectedNodeIds)
        assertEquals(AiChatFileMutationScope.FILES_AND_TRASH, signal.scope)
    }

    @Test
    fun `completed share result does not emit file refresh signal`() {
        val signal = completedResult(
            actionType = "share",
            affectedNodeIds = listOf(42L),
        ).toFileMutationSignal()

        assertNull(signal)
    }

    @Test
    fun `navigation semantic frame opens selected folder and virtual root`() {
        val selectedFolder = candidate(700L, "测试目录", type = "FOLDER")
        val folderTarget = response(
            candidateBinding = RagCandidateBinding(
                status = "selected_candidate",
                source = "test",
                query = "测试目录",
                candidateType = "FOLDER",
                candidates = listOf(selectedFolder),
                message = "已锁定目标。",
                selectedCandidate = selectedFolder,
                selectedIndex = 1,
            ),
            semanticFrame = semanticFrame(operation = "NAVIGATE", scope = "PREVIOUS_RESULTS"),
        ).toNavigationTargetOrNull()
        val rootTarget = response(
            semanticFrame = semanticFrame(operation = "NAVIGATE", scope = "ROOT"),
        ).toNavigationTargetOrNull()

        assertEquals(700L, folderTarget?.nodeId)
        assertEquals("FOLDER", folderTarget?.type)
        assertEquals("/", rootTarget?.path)
    }

    private fun response(
        intentId: String = "assistant_social",
        nextAction: String = "respond_only",
        actionDraft: RagActionDraft? = RagActionDraft(
            type = "none",
            parameters = emptyMap(),
            needsBackendBinding = false,
        ),
        actionPlan: RagActionPlan? = null,
        backendActionDraft: RagBackendActionDraft? = RagBackendActionDraft(
            status = "not_requested",
            bridgeVersion = null,
            actionType = "none",
            nextAction = null,
            confirmedByUser = false,
            executableByBackend = false,
            authorizationRequired = null,
            method = null,
            pathTemplate = null,
            path = null,
            contentType = null,
            pathVariables = null,
            queryParameters = null,
            body = null,
            requiredClientFields = null,
            targetCandidate = null,
            message = null,
        ),
        candidateBinding: RagCandidateBinding? = null,
        semanticFrame: RagSemanticFrame? = null,
    ) = RagAssistantPlanResponse(
        id = "response-1",
        schemaVersion = "intent_recognition_v1",
        templateId = "local",
        provider = "local",
        model = "rules",
        message = "hello",
        intentId = intentId,
        intentName = "assistant_social",
        taskType = null,
        confidence = 1.0,
        userGoal = null,
        normalizedQuery = null,
        entities = emptyMap(),
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
    )

    private fun semanticFrame(operation: String, scope: String) = RagSemanticFrame(
        schemaVersion = "semantic_frame_v2",
        relation = "FOLLOW_UP",
        operation = operation,
        query = null,
        scope = RagSemanticScope(type = scope, folderSurface = "", folderNormalized = ""),
        reference = null,
        confidence = 1.0,
        ambiguities = emptyList(),
        clarification = null,
    )

    private fun plan(
        status: String,
        actionType: String,
        risk: String,
        planKind: String = "atomic",
        bindings: Map<String, RagActionPlanBinding> = emptyMap(),
        steps: List<RagActionPlanStep> = emptyList(),
        requiredClientFields: List<String> = emptyList(),
    ) = RagActionPlan(
        version = "1",
        planId = "plan-1",
        status = status,
        planKind = planKind,
        actionType = actionType,
        risk = risk,
        confirmationLevel = if (risk == "none") "none" else "final",
        locale = "zh-CN",
        bindings = bindings,
        steps = steps,
        requiredClientFields = requiredClientFields,
        summary = "hello",
        messages = emptyList(),
    )

    private fun backendDraft(
        actionType: String,
        method: String,
        pathTemplate: String,
        status: String = "backend_action_ready",
        nextAction: String = "handoff_to_backend",
        executableByBackend: Boolean = true,
        contentType: String = "application/json",
        queryParameters: Map<String, Any> = emptyMap(),
        requiredClientFields: List<String> = emptyList(),
        targetCandidate: RagCandidateItem? = null,
    ) = RagBackendActionDraft(
        status = status,
        bridgeVersion = "action_bridge_v2",
        actionType = actionType,
        nextAction = nextAction,
        confirmedByUser = false,
        executableByBackend = executableByBackend,
        authorizationRequired = true,
        method = method,
        pathTemplate = pathTemplate,
        path = null,
        contentType = contentType,
        pathVariables = mapOf("nodeId" to 1L),
        queryParameters = queryParameters,
        body = mapOf("name" to "final.docx"),
        requiredClientFields = requiredClientFields,
        targetCandidate = targetCandidate,
        message = null,
    )

    private fun completedResult(
        actionType: String,
        affectedNodeIds: List<Long>,
    ) = RagActionExecutionResult(
        status = RagActionExecutionStatus.COMPLETED,
        actionType = actionType,
        message = "ok",
        validation = RagActionBridgeValidation(
            allowed = true,
            code = RagActionBridgeValidationCode.ALLOWED,
            message = "ok",
        ),
        affectedNodeIds = affectedNodeIds,
    )

    private fun candidate(nodeId: Long, name: String, type: String = "FILE") =
        RagCandidateItem(
            nodeId = nodeId,
            parentId = null,
            name = name,
            type = type,
            size = 1L,
            extension = name.substringAfterLast('.', ""),
            mimeType = null,
            updatedAt = "2026-08-11T10:00:00",
            path = "/测试/$name",
            breadcrumbs = emptyList(),
        )
}
