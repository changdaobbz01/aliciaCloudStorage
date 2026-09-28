package com.alicia.cloudstorage.phone.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alicia.cloudstorage.phone.data.RagActionExecutionContext
import com.alicia.cloudstorage.phone.data.RagActionExecutor
import com.alicia.cloudstorage.phone.data.RagAssistantClient
import com.alicia.cloudstorage.phone.data.RagAssistantClientContext
import com.alicia.cloudstorage.phone.data.RagAssistantClientEvent
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagConversationStore
import com.alicia.cloudstorage.phone.data.ApiException
import com.alicia.cloudstorage.phone.data.RagExecutionClient
import com.alicia.cloudstorage.phone.data.RagExecutionGateway
import com.alicia.cloudstorage.phone.data.RagExecutionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class RagAssistantViewModel(
    private val client: RagAssistantClient,
    private val conversationStore: RagConversationStore,
    private val actionExecutor: RagActionExecutor,
    private val executionGateway: RagExecutionGateway,
    private val ragBaseUrl: String,
    private val ragExecutionBaseUrl: String,
    private val apiBaseUrl: String,
    private val cloudExecutionEnabled: Boolean,
    private val legacyActionExecutionEnabled: Boolean,
    private val confirmationMessage: String,
    private val authToken: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AiChatUiState(
            messages = initialRagMessages(),
            draft = "",
            online = true,
        ),
    )
    val uiState = _uiState.asStateFlow()
    private val _fileMutationSignals = MutableSharedFlow<AiChatFileMutationSignal>(extraBufferCapacity = 1)
    val fileMutationSignals = _fileMutationSignals.asSharedFlow()
    private val clientUploadRequestChannel = Channel<AiChatClientUploadLaunch>(capacity = Channel.BUFFERED)
    val clientUploadRequests = clientUploadRequestChannel.receiveAsFlow()
    private val _composerAttachmentClearRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val composerAttachmentClearRequests = _composerAttachmentClearRequests.asSharedFlow()
    private val _navigationRequests = MutableSharedFlow<AiChatFileResult>(extraBufferCapacity = 1)
    val navigationRequests = _navigationRequests.asSharedFlow()

    private var conversationId: String? = null
    private var currentFolderId: Long? = null
    private var currentFolderPath: String = "根目录"
    private var nextMessageId = 10L
    private val pendingBackendDrafts = mutableMapOf<Long, RagBackendActionDraft>()
    private val clientUploadTracker = AiChatClientUploadTracker()

    init {
        viewModelScope.launch {
            conversationId = conversationStore.conversationIdFlow().first()
        }
    }

    fun updateDraft(value: String) {
        _uiState.update { state -> state.copy(draft = value) }
    }

    fun updateFileContext(folderId: Long?, folderPath: String) {
        currentFolderId = folderId
        currentFolderPath = folderPath.trim().ifBlank { "根目录" }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            conversationId = null
            pendingBackendDrafts.clear()
            clientUploadTracker.clear()
            conversationStore.clearConversation()
            _uiState.value = AiChatUiState(
                messages = initialRagMessages(),
                draft = "",
                online = true,
            )
        }
    }

    fun sendMessage() {
        val message = uiState.value.draft.trim()
        sendConversationMessage(
            requestMessage = message,
            displayText = message,
            clearDraft = true,
        )
    }

    fun selectCandidate(messageId: Long, file: AiChatFileResult) {
        val selection = file.selectionAction ?: return
        val candidateStillAvailable = uiState.value.messages.any { message ->
            message.id == messageId &&
                message.files.any { candidate ->
                    candidate.id == file.id && candidate.selectionAction != null
                }
        }
        if (!candidateStillAvailable) {
            return
        }

        sendConversationMessage(
            requestMessage = selection.requestMessage,
            displayText = selection.displayText,
            clearDraft = true,
            clientEvent = RagAssistantClientEvent(
                type = "SELECT_CANDIDATE",
                candidateId = selection.candidateId,
                candidateIndex = selection.candidateIndex,
                bindingKey = selection.bindingKey,
                planId = selection.planId,
            ),
        )
    }

    private fun sendConversationMessage(
        requestMessage: String,
        displayText: String,
        clearDraft: Boolean,
        clientEvent: RagAssistantClientEvent? = null,
    ) {
        val message = requestMessage.trim()
        val visibleText = displayText.trim().ifBlank { message }
        if (message.isBlank() || uiState.value.sending) {
            return
        }

        val userMessage = AiChatMessage(
            id = allocateMessageId(),
            author = AiChatAuthor.USER,
            text = visibleText,
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                draft = if (clearDraft) "" else state.draft,
                sending = true,
                online = true,
            )
        }

        viewModelScope.launch {
            val assistantMessageId = allocateMessageId()
            var finalResponseHandled = false
            var textDeltaStarted = false
            var streamedAssistantText = ""
            runCatching {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + AiChatMessage(
                            id = assistantMessageId,
                            author = AiChatAuthor.ASSISTANT,
                            text = STREAMING_PLACEHOLDER_TEXT,
                        ),
                    )
                }
                client.planStream(
                    baseUrl = ragBaseUrl,
                    token = authToken,
                    message = message,
                    conversationId = conversationId,
                    clientContext = currentClientContext(),
                    clientEvent = clientEvent,
                ).collect { event ->
                    when (event.type?.trim()?.lowercase()) {
                        "status" -> {
                            if (!textDeltaStarted) {
                                updateAssistantStreamingText(
                                    assistantMessageId,
                                    event.text.orEmpty(),
                                    replace = true,
                                )
                            }
                        }
                        "assistant_text_delta" -> {
                            val delta = event.text.orEmpty()
                            if (delta.isNotBlank()) {
                                streamedAssistantText += delta
                                updateAssistantStreamingText(
                                    assistantMessageId,
                                    delta,
                                    replace = !textDeltaStarted,
                                )
                            }
                            textDeltaStarted = true
                        }
                        "final" -> event.response?.let { response ->
                            finalResponseHandled = true
                            applyAssistantResponse(
                                response = response,
                                assistantMessageId = assistantMessageId,
                                replaceExistingMessage = true,
                                displayTextOverride = streamedAssistantText.takeIf { it.isNotBlank() },
                            )
                        }
                        "error" -> {
                            finalResponseHandled = true
                            updateAssistantStreamingText(
                                assistantMessageId,
                                event.text.orEmpty().ifBlank { "这次请求没有及时完成，请稍后再试。" },
                                replace = true,
                            )
                            _uiState.update { state ->
                                state.copy(sending = false, online = true)
                            }
                        }
                    }
                }
                if (!finalResponseHandled) {
                    throw IllegalStateException("流式响应缺少最终结果。")
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                val streamFailure = error.toRagAssistantFailure()
                if (!streamFailure.retryWithoutStreaming) {
                    showConversationFailure(assistantMessageId, streamFailure)
                    return@onFailure
                }
                runCatching {
                    client.plan(
                        baseUrl = ragBaseUrl,
                        token = authToken,
                        message = message,
                        conversationId = conversationId,
                        clientContext = currentClientContext(),
                        clientEvent = clientEvent,
                    )
                }.onSuccess { response ->
                    applyAssistantResponse(
                        response = response,
                        assistantMessageId = assistantMessageId,
                        replaceExistingMessage = true,
                    )
                }.onFailure { fallbackError ->
                    if (fallbackError is CancellationException) {
                        throw fallbackError
                    }
                    showConversationFailure(assistantMessageId, fallbackError.toRagAssistantFailure())
                }
            }
        }
    }

    private suspend fun applyAssistantResponse(
        response: com.alicia.cloudstorage.phone.data.RagAssistantPlanResponse,
        assistantMessageId: Long,
        replaceExistingMessage: Boolean,
        displayTextOverride: String? = null,
    ) {
        val nextConversationId = response.conversation?.conversationId
            ?.takeIf { it.isNotBlank() }
        if (nextConversationId != null) {
            conversationId = nextConversationId
            conversationStore.saveConversationId(nextConversationId)
        }

        val assistantMessage = response.toAssistantMessage(assistantMessageId).let { message ->
            if (displayTextOverride.isNullOrBlank()) {
                message
            } else {
                message.copy(text = displayTextOverride)
            }
        }
        val clearPendingAttachments = uiState.value.pendingAttachments.isNotEmpty() &&
            !response.shouldRetainComposerAttachments()
        if (assistantMessage.plan?.actionControls != null) {
            response.backendActionDraft
                ?.takeIf { it.isClientExecutableDraft() }
                ?.let { pendingBackendDrafts[assistantMessageId] = it }
        }

        _uiState.update { state ->
            val nextMessages = if (replaceExistingMessage) {
                state.messages.map { message ->
                    if (message.id == assistantMessageId) assistantMessage else message
                }
            } else {
                state.messages + assistantMessage
            }
            state.copy(
                messages = nextMessages,
                sending = false,
                online = true,
                pendingAttachments = if (clearPendingAttachments) emptyList() else state.pendingAttachments,
            )
        }
        if (clearPendingAttachments) {
            _composerAttachmentClearRequests.tryEmit(Unit)
        }
        response.toNavigationTargetOrNull()?.let(_navigationRequests::tryEmit)
    }

    private fun updateAssistantStreamingText(
        assistantMessageId: Long,
        text: String,
        replace: Boolean,
    ) {
        if (text.isBlank()) {
            return
        }
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != assistantMessageId) {
                        message
                    } else {
                        message.copy(text = if (replace) text else message.text + text)
                    }
                },
                online = true,
            )
        }
    }

    private fun showConversationFailure(messageId: Long, failure: RagAssistantFailure) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { existing ->
                    if (existing.id == messageId) existing.copy(text = failure.userMessage) else existing
                },
                sending = false,
                online = !failure.markOffline,
            )
        }
    }

    fun confirmReview(messageId: Long) {
        if (uiState.value.sending) {
            return
        }

        val message = uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val planId = message.plan?.planId
        val executionReference = message.plan?.executionReference
        val draft = pendingBackendDrafts[messageId]
        when (RagExecutionClientPolicy.route(
            executionReference = executionReference,
            backendDraft = draft,
            cloudExecutionEnabled = cloudExecutionEnabled,
            legacyLocalExecutionEnabled = legacyActionExecutionEnabled,
        )) {
            RagExecutionRoute.CLOUD -> confirmCloudExecution(
                messageId = messageId,
                planId = planId,
                actionType = draft?.actionType,
                reference = requireNotNull(executionReference),
                sourceConversationId = conversationId,
            )
            RagExecutionRoute.LEGACY_LOCAL -> executeLegacyDraft(
                messageId = messageId,
                planId = planId,
                draft = requireNotNull(draft),
            )
            RagExecutionRoute.CONTINUE_PLAN -> {
                markReviewHandled(messageId)
                sendConversationMessage(
                    requestMessage = confirmationMessage.ifBlank { DEFAULT_BACKEND_DRAFT_CONFIRMATION_MESSAGE },
                    displayText = "确认计划",
                    clearDraft = true,
                )
            }
            RagExecutionRoute.BLOCKED -> {
                pendingBackendDrafts.remove(messageId)
                markReviewHandled(messageId)
                appendAssistantMessage(
                    if (executionReference != null) {
                        "云端执行通道尚未开启，本次没有提交，也不会回退到本地执行。"
                    } else {
                        "这条计划没有可确认的云端任务，本次没有提交任何文件变更。"
                    },
                )
            }
        }
    }

    private fun executeLegacyDraft(
        messageId: Long,
        planId: String?,
        draft: RagBackendActionDraft,
    ) {
        pendingBackendDrafts.remove(messageId)
        markReviewHandled(messageId)
        val executionMessageId = allocateMessageId()
        showOperationMessage(executionMessageId, draft.actionType)
        viewModelScope.launch {
            runCatching {
                actionExecutor.execute(
                    context = RagActionExecutionContext(
                        baseUrl = apiBaseUrl,
                        token = authToken,
                    ),
                    draft = draft.copy(confirmedByUser = true),
                )
            }.onSuccess { result ->
                result.toFileMutationSignal()?.let { signal ->
                    _fileMutationSignals.tryEmit(signal)
                }
                replaceOperationMessage(
                    messageId = executionMessageId,
                    message = result.toAssistantMessage(executionMessageId),
                )
                syncExecutionOutcome(
                    planId = planId,
                    type = if (result.succeeded) "ACTION_COMPLETED" else "ACTION_FAILED",
                    outcome = result.status.name,
                )
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }

                replaceOperationMessage(
                    messageId = executionMessageId,
                    message = AiChatMessage(
                        id = executionMessageId,
                        author = AiChatAuthor.ASSISTANT,
                        text = error.readableRagMessage(),
                    ),
                )
                syncExecutionOutcome(
                    planId = planId,
                    type = "ACTION_FAILED",
                    outcome = error::class.java.simpleName,
                )
            }
        }
    }

    private fun confirmCloudExecution(
        messageId: Long,
        planId: String?,
        actionType: String?,
        reference: AiChatExecutionReference,
        sourceConversationId: String?,
    ) {
        val executionMessageId = allocateMessageId()
        showOperationMessage(
            messageId = executionMessageId,
            actionType = actionType,
            initialText = "正在提交云端确认...",
        )
        viewModelScope.launch {
            val confirmed = runCatching {
                executionGateway.confirm(
                    baseUrl = ragExecutionBaseUrl,
                    token = authToken,
                    executionId = reference.executionId,
                    expectedVersion = reference.version,
                )
            }.recoverCatching { error ->
                if (error is CancellationException) {
                    throw error
                }
                if (error is ApiException) {
                    throw error
                }
                val recovered = executionGateway.get(
                    baseUrl = ragExecutionBaseUrl,
                    token = authToken,
                    executionId = reference.executionId,
                )
                if (recovered.status.equals("PENDING_CONFIRMATION", ignoreCase = true)) {
                    throw error
                }
                recovered
            }.getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                replaceOperationMessage(
                    executionMessageId,
                    AiChatMessage(
                        id = executionMessageId,
                        author = AiChatAuthor.ASSISTANT,
                        text = error.readableRagMessage(),
                    ),
                )
                return@launch
            }

            pendingBackendDrafts.remove(messageId)
            markReviewHandled(messageId)
            observeCloudExecution(
                executionMessageId = executionMessageId,
                planId = planId,
                sourceConversationId = sourceConversationId,
                expectedExecutionId = reference.executionId,
                initial = confirmed,
            )
        }
    }

    private suspend fun observeCloudExecution(
        executionMessageId: Long,
        planId: String?,
        sourceConversationId: String?,
        expectedExecutionId: String,
        initial: RagExecutionResponse,
    ) {
        var current = initial
        var polls = 0
        var lastStatus: String? = null
        var firstStatusUpdate = true
        while (true) {
            if (current.executionId != expectedExecutionId) {
                val message = AiChatMessage(
                    id = executionMessageId,
                    author = AiChatAuthor.ASSISTANT,
                    text = "任务已提交云端，但返回的任务标识校验失败。为避免关联到错误任务，已停止自动刷新。",
                )
                if (firstStatusUpdate) {
                    replaceOperationMessage(executionMessageId, message)
                } else {
                    updateOperationMessage(executionMessageId, message)
                }
                return
            }
            val status = current.status?.trim()?.uppercase()
            if (firstStatusUpdate || status != lastStatus) {
                val message = current.toAssistantMessage(executionMessageId, apiBaseUrl)
                if (firstStatusUpdate) {
                    replaceOperationMessage(executionMessageId, message)
                    firstStatusUpdate = false
                } else {
                    updateOperationMessage(executionMessageId, message)
                }
                lastStatus = status
            }
            if (current.isTerminal()) {
                current.toFileMutationSignal()?.let(_fileMutationSignals::tryEmit)
                syncExecutionOutcome(
                    planId = planId,
                    type = if (status == "SUCCEEDED") "ACTION_COMPLETED" else "ACTION_FAILED",
                    outcome = status ?: "UNKNOWN",
                    targetConversationId = sourceConversationId,
                )
                return
            }
            if (status == "WAITING_CLIENT_INPUT") {
                val uploadInput = current.waitingUploadInput()
                if (uploadInput == null) {
                    updateOperationMessage(
                        executionMessageId,
                        AiChatMessage(
                            id = executionMessageId,
                            author = AiChatAuthor.ASSISTANT,
                            text = "云端任务请求了客户端输入，但请求结构未通过安全校验，已停止自动处理。",
                        ),
                    )
                    return
                }
                requestCloudUploadInput(
                    executionMessageId = executionMessageId,
                    planId = planId,
                    sourceConversationId = sourceConversationId,
                    uploadInput = uploadInput,
                )
                return
            }
            if (polls++ >= MAX_EXECUTION_STATUS_POLLS) {
                updateOperationMessage(
                    executionMessageId,
                    AiChatMessage(
                        id = executionMessageId,
                        author = AiChatAuthor.ASSISTANT,
                        text = "任务已提交云端并会继续执行，状态更新暂时停止，请稍后重新查看。",
                    ),
                )
                return
            }
            delay(EXECUTION_STATUS_POLL_INTERVAL_MILLIS)
            current = runCatching {
                executionGateway.get(
                    baseUrl = ragExecutionBaseUrl,
                    token = authToken,
                    executionId = expectedExecutionId,
                )
            }.getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                updateOperationMessage(
                    executionMessageId,
                    AiChatMessage(
                        id = executionMessageId,
                        author = AiChatAuthor.ASSISTANT,
                        text = "任务已提交云端并会继续执行，但暂时无法刷新状态：${error.readableRagMessage()}",
                    ),
                )
                return
            }
        }
    }

    fun cancelReview(messageId: Long) {
        if (uiState.value.sending) {
            return
        }
        val message = uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val planId = message.plan?.planId
        val reference = message.plan?.executionReference
        if (reference == null || !cloudExecutionEnabled) {
            pendingBackendDrafts.remove(messageId)
            markReviewHandled(messageId)
            appendAssistantMessage("已取消这次计划，我不会提交任何文件变更。")
            syncExecutionOutcome(planId, "ACTION_CANCELLED", "user_cancelled")
            return
        }

        val sourceConversationId = conversationId
        val operationMessageId = allocateMessageId()
        showOperationMessage(
            messageId = operationMessageId,
            actionType = null,
            initialText = "正在取消云端任务...",
        )
        viewModelScope.launch {
            runCatching {
                executionGateway.cancel(
                    baseUrl = ragExecutionBaseUrl,
                    token = authToken,
                    executionId = reference.executionId,
                )
            }.onSuccess { response ->
                pendingBackendDrafts.remove(messageId)
                markReviewHandled(messageId)
                replaceOperationMessage(
                    operationMessageId,
                    response.toAssistantMessage(operationMessageId, apiBaseUrl),
                )
                syncExecutionOutcome(
                    planId,
                    "ACTION_CANCELLED",
                    response.status ?: "CANCELLED",
                    sourceConversationId,
                )
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                replaceOperationMessage(
                    operationMessageId,
                    AiChatMessage(
                        id = operationMessageId,
                        author = AiChatAuthor.ASSISTANT,
                        text = error.readableRagMessage(),
                    ),
                )
            }
        }
    }

    private fun syncExecutionOutcome(
        planId: String?,
        type: String,
        outcome: String,
        targetConversationId: String? = conversationId,
    ) {
        val activeConversationId = targetConversationId ?: return
        viewModelScope.launch {
            runCatching {
                client.plan(
                    baseUrl = ragBaseUrl,
                    token = authToken,
                    message = "操作结果同步",
                    conversationId = activeConversationId,
                    clientContext = currentClientContext(),
                    clientEvent = RagAssistantClientEvent(
                        type = type,
                        planId = planId,
                        outcome = outcome,
                    ),
                )
            }
            if (conversationId == activeConversationId) {
                conversationId = null
                conversationStore.clearConversation()
            }
        }
    }

    fun prepareComposerAttachment(): Boolean {
        return !uiState.value.sending
    }

    fun completeComposerAttachmentSelection(attachments: List<AiChatPendingAttachment>) {
        if (attachments.isEmpty()) {
            return
        }

        _uiState.update { state ->
            state.copy(pendingAttachments = attachments)
        }
    }

    fun clearComposerAttachments() {
        _uiState.update { state ->
            state.copy(pendingAttachments = emptyList())
        }
    }

    fun runClientUpload(messageId: Long, request: AiChatClientUploadRequest) {
        if (uiState.value.sending) {
            return
        }

        val uploadStillAvailable = uiState.value.messages.any { message ->
            message.id == messageId &&
                message.plan?.clientActionControls?.uploadRequest == request
        }
        if (!uploadStillAvailable) {
            return
        }

        val launch = clientUploadTracker.start(messageId, request)
        if (launch == null) {
            appendAssistantMessage("还有一个文件选择或上传任务正在处理，请完成后再试。")
            return
        }
        val hasPendingAttachments = uiState.value.pendingAttachments.isNotEmpty()
        if (clientUploadRequestChannel.trySend(launch).isFailure) {
            clientUploadTracker.cancel(launch)
            appendAssistantMessage("系统文件选择器暂时没有打开，请稍后再试。")
            return
        }

        if (!hasPendingAttachments) {
            appendAssistantMessage(AiChatExecutionFeedback.uploadSelectionPrompt(request))
        }
    }

    fun completeClientUploadSelection(
        launch: AiChatClientUploadLaunch,
        filesSelected: Boolean,
    ) {
        if (filesSelected) {
            val executionMessageId = allocateMessageId()
            if (!clientUploadTracker.markSelected(launch, executionMessageId)) {
                return
            }
            showOperationMessage(
                messageId = executionMessageId,
                actionType = AiChatExecutionFeedback.uploadActionType(launch.request),
                handledClientActionMessageId = launch.messageId,
                clearPendingAttachments = true,
            )
        } else {
            if (clientUploadTracker.cancel(launch)) {
                val cloudInput = launch.cloudInput
                if (cloudInput == null) {
                    appendAssistantMessage("已取消文件选择，这条上传计划还保留着，需要时可以重新选择文件。")
                } else {
                    appendAssistantMessage("已取消文件选择，正在关闭这次云端上传步骤。")
                    submitCloudClientInput(
                        cloudInput = cloudInput,
                        status = "CANCELLED",
                        nodeIds = emptyList(),
                        errorCode = "upload_cancelled",
                    )
                }
            }
        }
    }

    fun completeClientUploadExecution(
        launch: AiChatClientUploadLaunch,
        result: OperationOutcome,
    ) {
        val executionMessageId = clientUploadTracker.complete(launch) ?: return
        replaceOperationMessage(
            messageId = executionMessageId,
            message = AiChatMessage(
                id = executionMessageId,
                author = AiChatAuthor.ASSISTANT,
                text = result.message,
            ),
        )
        val cloudInput = launch.cloudInput ?: return
        val uploadedNodeIds = result.affectedNodeIds.distinct()
        val succeeded = result.status == OperationOutcomeStatus.SUCCEEDED && uploadedNodeIds.isNotEmpty()
        submitCloudClientInput(
            cloudInput = cloudInput,
            status = if (succeeded) "SUCCEEDED" else "FAILED",
            nodeIds = if (succeeded) uploadedNodeIds else emptyList(),
            errorCode = when {
                succeeded -> null
                result.status == OperationOutcomeStatus.SUCCEEDED -> "client_input_unavailable"
                else -> "upload_failed"
            },
        )
    }

    private fun requestCloudUploadInput(
        executionMessageId: Long,
        planId: String?,
        sourceConversationId: String?,
        uploadInput: RagWaitingUploadInput,
    ) {
        val request = AiChatClientUploadRequest(
            parentId = uploadInput.parentId,
            targetName = null,
        )
        val launch = clientUploadTracker.start(
            messageId = executionMessageId,
            request = request,
            cloudInput = AiChatCloudClientInput(
                executionMessageId = executionMessageId,
                planId = planId,
                sourceConversationId = sourceConversationId,
                executionId = uploadInput.executionId,
                expectedVersion = uploadInput.expectedVersion,
                stepId = uploadInput.stepId,
            ),
        )
        if (launch == null) {
            updateOperationMessage(
                executionMessageId,
                AiChatMessage(
                    id = executionMessageId,
                    author = AiChatAuthor.ASSISTANT,
                    text = "云端任务正在等待上传，但当前还有一个文件选择任务未结束。请先完成当前操作。",
                ),
            )
            return
        }
        if (clientUploadRequestChannel.trySend(launch).isFailure) {
            clientUploadTracker.cancel(launch)
            updateOperationMessage(
                executionMessageId,
                AiChatMessage(
                    id = executionMessageId,
                    author = AiChatAuthor.ASSISTANT,
                    text = "系统文件选择器暂时无法打开，云端任务尚未收到任何文件。",
                ),
            )
            return
        }
        if (uiState.value.pendingAttachments.isEmpty()) {
            appendAssistantMessage(AiChatExecutionFeedback.uploadSelectionPrompt(request))
        }
    }

    private fun submitCloudClientInput(
        cloudInput: AiChatCloudClientInput,
        status: String,
        nodeIds: List<Long>,
        errorCode: String?,
    ) {
        viewModelScope.launch {
            val response = runCatching {
                executionGateway.completeClientInput(
                    baseUrl = ragExecutionBaseUrl,
                    token = authToken,
                    executionId = cloudInput.executionId,
                    expectedVersion = cloudInput.expectedVersion,
                    stepId = cloudInput.stepId,
                    status = status,
                    nodeIds = nodeIds,
                    errorCode = errorCode,
                )
            }.recoverCatching { submissionError ->
                if (submissionError is CancellationException) {
                    throw submissionError
                }
                val observed = executionGateway.get(
                    baseUrl = ragExecutionBaseUrl,
                    token = authToken,
                    executionId = cloudInput.executionId,
                )
                if (observed.status.equals("WAITING_CLIENT_INPUT", ignoreCase = true)) {
                    throw submissionError
                }
                observed
            }.getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                updateOperationMessage(
                    cloudInput.executionMessageId,
                    AiChatMessage(
                        id = cloudInput.executionMessageId,
                        author = AiChatAuthor.ASSISTANT,
                        text = "文件传输已结束，但暂时无法同步云端任务状态：${error.readableRagMessage()}",
                    ),
                )
                return@launch
            }
            observeCloudExecution(
                executionMessageId = cloudInput.executionMessageId,
                planId = cloudInput.planId,
                sourceConversationId = cloudInput.sourceConversationId,
                expectedExecutionId = cloudInput.executionId,
                initial = response,
            )
        }
    }

    private fun markReviewHandled(messageId: Long) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    val plan = message.plan
                    if (message.id == messageId && plan?.actionControls != null) {
                        message.copy(plan = plan.copy(actionControls = null))
                    } else {
                        message
                    }
                },
            )
        }
    }

    private fun appendAssistantMessage(text: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages + AiChatMessage(
                    id = allocateMessageId(),
                    author = AiChatAuthor.ASSISTANT,
                    text = text,
                ),
                sending = false,
                online = true,
            )
        }
    }

    private fun showOperationMessage(
        messageId: Long,
        actionType: String?,
        handledClientActionMessageId: Long? = null,
        clearPendingAttachments: Boolean = false,
        initialText: String = AiChatExecutionFeedback.progressMessage(actionType),
    ) {
        _uiState.update { state ->
            val previousMessages = if (handledClientActionMessageId == null) {
                state.messages
            } else {
                state.messages.map { message ->
                    val plan = message.plan
                    if (message.id == handledClientActionMessageId && plan?.clientActionControls != null) {
                        message.copy(plan = plan.copy(clientActionControls = null))
                    } else {
                        message
                    }
                }
            }
            state.copy(
                messages = previousMessages + AiChatMessage(
                    id = messageId,
                    author = AiChatAuthor.ASSISTANT,
                    text = initialText,
                ),
                pendingAttachments = if (clearPendingAttachments) emptyList() else state.pendingAttachments,
                sending = true,
                online = true,
            )
        }
    }

    private fun replaceOperationMessage(
        messageId: Long,
        message: AiChatMessage,
    ) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { existing ->
                    if (existing.id == messageId) message else existing
                },
                sending = false,
                online = true,
            )
        }
    }

    private fun updateOperationMessage(
        messageId: Long,
        message: AiChatMessage,
    ) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { existing ->
                    if (existing.id == messageId) message else existing
                },
                online = true,
            )
        }
    }

    private fun allocateMessageId(): Long = nextMessageId++

    private fun currentClientContext(): RagAssistantClientContext = RagAssistantClientContext(
        currentFolderId = currentFolderId,
        currentFolderPath = currentFolderPath,
        availableClientInputs = uiState.value.pendingAttachments
            .takeIf { it.isNotEmpty() }
            ?.let { attachments ->
                buildMap {
                    put("files", attachments.size)
                    attachments.count(AiChatPendingAttachment::isFolder)
                        .takeIf { it > 0 }
                        ?.let { put("folders", it) }
                }
            }
            .orEmpty(),
    )

    override fun onCleared() {
        clientUploadRequestChannel.close()
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            context: Context,
            ragBaseUrl: String,
            ragExecutionBaseUrl: String,
            apiBaseUrl: String,
            cloudExecutionEnabled: Boolean,
            legacyActionExecutionEnabled: Boolean,
            confirmationMessage: String = DEFAULT_BACKEND_DRAFT_CONFIRMATION_MESSAGE,
            authToken: String,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RagAssistantViewModel(
                        client = RagAssistantClient(),
                        conversationStore = RagConversationStore(context.applicationContext),
                        actionExecutor = RagActionExecutor(executionEnabled = legacyActionExecutionEnabled),
                        executionGateway = RagExecutionClient(),
                        ragBaseUrl = ragBaseUrl,
                        ragExecutionBaseUrl = ragExecutionBaseUrl,
                        apiBaseUrl = apiBaseUrl,
                        cloudExecutionEnabled = cloudExecutionEnabled,
                        legacyActionExecutionEnabled = legacyActionExecutionEnabled,
                        confirmationMessage = confirmationMessage,
                        authToken = authToken,
                    ) as T
                }
            }

        private const val DEFAULT_BACKEND_DRAFT_CONFIRMATION_MESSAGE = "确认"
        private const val STREAMING_PLACEHOLDER_TEXT = "安安正在思考..."
        private const val EXECUTION_STATUS_POLL_INTERVAL_MILLIS = 1_000L
        private const val MAX_EXECUTION_STATUS_POLLS = 150
    }
}

private fun RagBackendActionDraft.isClientExecutableDraft(): Boolean =
    status.equals("backend_action_ready", ignoreCase = true) &&
        executableByBackend == true &&
        !actionType.isNullOrBlank() &&
        !actionType.equals("none", ignoreCase = true) &&
        requiredClientFields.orEmpty().isEmpty()
