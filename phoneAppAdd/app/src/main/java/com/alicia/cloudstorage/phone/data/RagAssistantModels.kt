package com.alicia.cloudstorage.phone.data

data class RagAssistantPlanRequest(
    val message: String,
    val conversationId: String? = null,
    val clientContext: RagAssistantClientContext? = null,
    val clientEvent: RagAssistantClientEvent? = null,
)

data class RagAssistantClientEvent(
    val type: String,
    val candidateId: Long? = null,
    val candidateIndex: Int? = null,
    val bindingKey: String? = null,
    val planId: String? = null,
    val outcome: String? = null,
)

data class RagAssistantClientContext(
    val currentFolderId: Long?,
    val currentFolderPath: String,
    val availableClientInputs: Map<String, Int> = emptyMap(),
    val actionContractVersion: String = "action_bridge_v2",
    val supportedActionTypes: List<String> = listOf(
        "rename",
        "delete",
        "share",
        "upload_target",
        "composite.create_folder_then_upload",
        "collection.trash_by_name_contains",
        "collection.trash_by_category",
        "collection.trash",
        "collection.move_by_category",
        "collection.move_by_extension",
        "collection.move_exact",
        "collection.move_by_name_contains",
        "collection.move",
        "collection.rename_add_prefix",
    ),
)

data class RagAssistantPlanResponse(
    val id: String?,
    val schemaVersion: String?,
    val templateId: String?,
    val provider: String?,
    val model: String?,
    val message: String?,
    val intentId: String?,
    val intentName: String?,
    val taskType: String?,
    val confidence: Double?,
    val userGoal: String?,
    val normalizedQuery: String?,
    val entities: Map<String, Any>?,
    val requiredSlots: List<String>?,
    val missingSlots: List<String>?,
    val nextAction: String?,
    val safety: RagSafetyDecision?,
    val actionDraft: RagActionDraft?,
    val backendActionDraft: RagBackendActionDraft?,
    val actionPlan: RagActionPlan?,
    val assistantText: String?,
    val clarificationQuestion: String?,
    val reason: String?,
    val fallbackReason: String?,
    val candidateBinding: RagCandidateBinding?,
    val conversation: RagConversationSnapshot?,
    val semanticFrame: RagSemanticFrame? = null,
    val interaction: RagAssistantInteraction? = null,
)

data class RagSemanticFrame(
    val schemaVersion: String?,
    val relation: String?,
    val operation: String?,
    val query: RagSemanticQuery?,
    val scope: RagSemanticScope?,
    val reference: RagSemanticReference?,
    val confidence: Double?,
    val ambiguities: List<String>?,
    val clarification: RagSemanticClarification?,
)

data class RagSemanticQuery(
    val mode: String?,
    val resultType: String?,
    val nameSurface: String?,
    val nameNormalized: String?,
    val filters: Map<String, Any>?,
)

data class RagSemanticScope(
    val type: String?,
    val folderSurface: String?,
    val folderNormalized: String?,
)

data class RagSemanticReference(
    val type: String?,
    val candidateId: Long?,
    val candidateIndex: Int?,
)

data class RagSemanticClarification(
    val reason: String?,
    val question: String?,
    val suggestions: List<String>?,
)

data class RagAssistantInteraction(
    val stage: String?,
    val allowedActions: List<RagAllowedAction>?,
    val clarification: RagSemanticClarification?,
)

data class RagAllowedAction(
    val type: String?,
    val label: String?,
    val payload: Map<String, Any>?,
)

data class RagAssistantStreamEvent(
    val type: String?,
    val text: String?,
    val response: RagAssistantPlanResponse?,
)

data class RagSafetyDecision(
    val risk: String?,
    val requiresConfirmation: Boolean?,
    val allowedToExecute: Boolean?,
    val reason: String?,
)

data class RagActionDraft(
    val type: String?,
    val parameters: Map<String, Any>?,
    val needsBackendBinding: Boolean?,
)

data class RagCandidateBinding(
    val status: String?,
    val source: String?,
    val query: String?,
    val candidateType: String?,
    val candidates: List<RagCandidateItem>?,
    val message: String?,
    val selectedCandidate: RagCandidateItem?,
    val selectedIndex: Int?,
)

data class RagCandidateItem(
    val nodeId: Long?,
    val parentId: Long?,
    val name: String?,
    val type: String?,
    val size: Long?,
    val extension: String?,
    val mimeType: String?,
    val updatedAt: String?,
    val path: String?,
    val breadcrumbs: List<RagCandidateBreadcrumb>?,
)

data class RagCandidateBreadcrumb(
    val nodeId: Long?,
    val name: String?,
)

data class RagActionPlan(
    val version: String?,
    val planId: String?,
    val status: String?,
    val planKind: String?,
    val actionType: String?,
    val risk: String?,
    val confirmationLevel: String?,
    val locale: String?,
    val bindings: Map<String, RagActionPlanBinding>?,
    val steps: List<RagActionPlanStep>?,
    val requiredClientFields: List<String>?,
    val summary: String?,
    val messages: List<RagActionPlanMessage>?,
)

data class RagActionPlanBinding(
    val key: String?,
    val kind: String?,
    val status: String?,
    val query: String?,
    val selectedCandidate: RagCandidateItem?,
    val candidates: List<RagCandidateItem>?,
    val count: Int?,
    val filter: Map<String, Any>?,
)

data class RagActionPlanStep(
    val stepId: String?,
    val action: String?,
    val status: String?,
    val params: Map<String, Any>?,
    val dependsOn: List<String>?,
    val requiredClientFields: List<String>?,
    val outputKey: String?,
)

data class RagActionPlanMessage(
    val level: String?,
    val code: String?,
    val text: String?,
)

data class RagBackendActionDraft(
    val status: String?,
    val bridgeVersion: String?,
    val actionType: String?,
    val nextAction: String?,
    val confirmedByUser: Boolean?,
    val executableByBackend: Boolean?,
    val authorizationRequired: Boolean?,
    val method: String?,
    val pathTemplate: String?,
    val path: String?,
    val contentType: String?,
    val pathVariables: Map<String, Any>?,
    val queryParameters: Map<String, Any>?,
    val body: Map<String, Any>?,
    val requiredClientFields: List<String>?,
    val targetCandidate: RagCandidateItem?,
    val message: String?,
)

data class RagConversationSnapshot(
    val conversationId: String?,
    val turnIndex: Int?,
    val status: String?,
    val pendingIntentId: String?,
    val pendingSlots: List<String>?,
    val hasPendingAction: Boolean?,
    val candidateBindingStatus: String?,
    val candidateCount: Int?,
    val expiresAt: String?,
)
