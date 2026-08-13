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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
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
    private val ragBaseUrl: String,
    private val apiBaseUrl: String,
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

        val planId = uiState.value.messages.firstOrNull { it.id == messageId }?.plan?.planId
        val draft = pendingBackendDrafts.remove(messageId)
        markReviewHandled(messageId)
        if (draft == null) {
            sendConversationMessage(
                requestMessage = confirmationMessage.ifBlank { DEFAULT_BACKEND_DRAFT_CONFIRMATION_MESSAGE },
                displayText = "确认计划",
                clearDraft = true,
            )
            return
        }

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

    fun cancelReview(messageId: Long) {
        val planId = uiState.value.messages.firstOrNull { it.id == messageId }?.plan?.planId
        pendingBackendDrafts.remove(messageId)
        markReviewHandled(messageId)
        appendAssistantMessage("已取消这次计划，我不会提交任何文件变更。")
        syncExecutionOutcome(planId, "ACTION_CANCELLED", "user_cancelled")
    }

    private fun syncExecutionOutcome(planId: String?, type: String, outcome: String) {
        val activeConversationId = conversationId ?: return
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
                appendAssistantMessage("已取消文件选择，这条上传计划还保留着，需要时可以重新选择文件。")
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
                    text = AiChatExecutionFeedback.progressMessage(actionType),
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
            apiBaseUrl: String,
            actionExecutionEnabled: Boolean,
            confirmationMessage: String = DEFAULT_BACKEND_DRAFT_CONFIRMATION_MESSAGE,
            authToken: String,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RagAssistantViewModel(
                        client = RagAssistantClient(),
                        conversationStore = RagConversationStore(context.applicationContext),
                        actionExecutor = RagActionExecutor(executionEnabled = actionExecutionEnabled),
                        ragBaseUrl = ragBaseUrl,
                        apiBaseUrl = apiBaseUrl,
                        confirmationMessage = confirmationMessage,
                        authToken = authToken,
                    ) as T
                }
            }

        private const val DEFAULT_BACKEND_DRAFT_CONFIRMATION_MESSAGE = "确认"
        private const val STREAMING_PLACEHOLDER_TEXT = "安安正在思考..."
    }
}

private fun RagBackendActionDraft.isClientExecutableDraft(): Boolean =
    status.equals("backend_action_ready", ignoreCase = true) &&
        executableByBackend == true &&
        !actionType.isNullOrBlank() &&
        !actionType.equals("none", ignoreCase = true) &&
        requiredClientFields.orEmpty().isEmpty()
