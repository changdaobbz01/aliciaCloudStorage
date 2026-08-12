package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.RagActionDraft
import com.alicia.cloudstorage.phone.data.RagActionPlan
import com.alicia.cloudstorage.phone.data.RagActionPlanBinding
import com.alicia.cloudstorage.phone.data.RagAssistantPlanResponse
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagCandidateBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagAssistantPlanPreviewPolicyTest {

    @Test
    fun `hides pure respond only plans`() {
        assertTrue(respondOnlyResponse().isPureRespondOnlyPlan())
    }

    @Test
    fun `hides respond only not requested drafts without action plan`() {
        assertTrue(respondOnlyResponse(actionPlan = null).isPureRespondOnlyPlan())
    }

    @Test
    fun `hides non operational fallback clarification plans`() {
        val response = respondOnlyResponse(
            nextAction = "ask_clarification",
            actionPlan = plan(
                status = "clarification_required",
                actionType = "none",
                risk = "none",
            ),
        )

        assertFalse(response.isPureRespondOnlyPlan())
        assertTrue(response.isNonOperationalReply())
        assertNull(response.actionPlanPreview())
    }

    @Test
    fun `keeps file operation plans visible`() {
        val response = respondOnlyResponse(
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
        )

        assertFalse(response.isPureRespondOnlyPlan())
    }

    @Test
    fun `renders file search plans with user friendly wording`() {
        val response = respondOnlyResponse(
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
        )

        val preview = response.actionPlanPreview()
        assertNotNull(preview)
        assertEquals("文件搜索计划", preview!!.title)
        assertTrue(preview.lines.contains("正在根据你的线索匹配文件或目录。"))
        assertTrue(preview.lines.contains("这一步只做查找，不会改动文件。"))
        assertFalse(preview.lines.joinToString().contains("binding_required"))
        assertFalse(preview.lines.joinToString().contains("not_requested"))
    }

    @Test
    fun `renders empty search result plans honestly`() {
        val response = respondOnlyResponse(
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
                message = "未匹配到候选文件或目录，可调整线索后重新检索。",
                selectedCandidate = null,
                selectedIndex = null,
            ),
        )

        val preview = response.actionPlanPreview()
        assertNotNull(preview)
        assertTrue(preview!!.lines.contains("没有找到匹配的文件或目录。"))
        assertFalse(preview.lines.contains("正在根据你的线索匹配文件或目录。"))
    }

    @Test
    fun `keeps plans visible when candidates are present`() {
        val response = respondOnlyResponse(
            actionPlan = plan(
                actionType = "none",
                bindings = mapOf(
                    "source" to RagActionPlanBinding(
                        key = "source",
                        kind = "candidate",
                        status = "candidate_selection_required",
                        query = "project",
                        selectedCandidate = null,
                        candidates = emptyList(),
                        count = 2,
                        filter = null,
                    ),
                ),
            ),
        )

        assertFalse(response.isPureRespondOnlyPlan())
    }

    private fun respondOnlyResponse(
        nextAction: String = "respond_only",
        actionDraft: RagActionDraft? = RagActionDraft(
            type = "none",
            parameters = emptyMap(),
            needsBackendBinding = false,
        ),
        actionPlan: RagActionPlan? = plan(),
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
    )

    private fun plan(
        status: String = "completed",
        actionType: String = "none",
        risk: String = "none",
        bindings: Map<String, RagActionPlanBinding>? = emptyMap(),
    ) = RagActionPlan(
        version = "1",
        planId = "plan-1",
        status = status,
        planKind = "atomic",
        actionType = actionType,
        risk = risk,
        confirmationLevel = "none",
        locale = "zh-CN",
        bindings = bindings,
        steps = emptyList(),
        requiredClientFields = emptyList(),
        summary = "hello",
        messages = emptyList(),
    )
}
