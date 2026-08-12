package com.alicia.cloudstorage.phone.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alicia.cloudstorage.phone.data.RagActionExecutionContext
import com.alicia.cloudstorage.phone.data.RagActionExecutor
import com.alicia.cloudstorage.phone.data.RagAssistantClient
import com.alicia.cloudstorage.phone.data.RagBackendActionDraft
import com.alicia.cloudstorage.phone.data.RagConversationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
    private val _clientUploadRequests = MutableSharedFlow<AiChatClientUploadLaunch>(extraBufferCapacity = 1)
    val clientUploadRequests = _clientUploadRequests.asSharedFlow()
    private val _composerAttachmentClearRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val composerAttachmentClearRequests = _composerAttachmentClearRequests.asSharedFlow()

    private var conversationId: String? = null
    private var nextMessageId = 10L
    private val pendingBackendDrafts = mutableMapOf<Long, RagBackendActionDraft>()

    init {
        viewModelScope.launch {
            conversationId = conversationStore.conversationIdFlow().first()
        }
    }

    fun updateDraft(value: String) {
        _uiState.update { state -> state.copy(draft = value) }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            conversationId = null
            pendingBackendDrafts.clear()
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
        )
    }

    private fun sendConversationMessage(
        requestMessage: String,
        displayText: String,
        clearDraft: Boolean,
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
                    }
                }
                if (!finalResponseHandled) {
                    throw IllegalStateException("流式响应缺少最终结果。")
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                runCatching {
                    client.plan(
                        baseUrl = ragBaseUrl,
                        token = authToken,
                        message = message,
                        conversationId = conversationId,
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
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { existing ->
                                if (existing.id == assistantMessageId) {
                                    existing.copy(text = fallbackError.readableRagMessage())
                                } else {
                                    existing
                                }
                            },
                            sending = false,
                            online = false,
                        )
                    }
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

    fun confirmReview(messageId: Long) {
        if (uiState.value.sending) {
            return
        }

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

        _uiState.update { state -> state.copy(sending = true, online = true) }
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
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + result.toAssistantMessage(allocateMessageId()),
                        sending = false,
                        online = true,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + AiChatMessage(
                            id = allocateMessageId(),
                            author = AiChatAuthor.ASSISTANT,
                            text = error.readableRagMessage(),
                        ),
                        sending = false,
                        online = false,
                    )
                }
            }
        }
    }

    fun cancelReview(messageId: Long) {
        pendingBackendDrafts.remove(messageId)
        markReviewHandled(messageId)
        conversationId = null
        viewModelScope.launch {
            conversationStore.clearConversation()
        }
        appendAssistantMessage("已取消这次计划，我不会提交任何文件变更。")
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

        if (!_clientUploadRequests.tryEmit(AiChatClientUploadLaunch(messageId, request))) {
            appendAssistantMessage("系统文件选择器暂时没有打开，请稍后再试。")
            return
        }

        val targetText = request.targetName?.takeIf { it.isNotBlank() }
        val createFolderName = request.createFolderName?.takeIf { it.isNotBlank() }
        val message = if (createFolderName != null) {
            "请在系统文件选择器中选择要上传到「${targetText ?: createFolderName}」的文件，选择后我会先创建文件夹再上传。"
        } else {
            "请在系统文件选择器中选择要上传${targetText?.let { "到「$it」" }.orEmpty()}的文件。"
        }
        appendAssistantMessage(message)
    }

    fun completeClientUploadSelection(
        launch: AiChatClientUploadLaunch,
        filesSelected: Boolean,
    ) {
        val uploadStillAvailable = uiState.value.messages.any { message ->
            message.id == launch.messageId &&
                message.plan?.clientActionControls?.uploadRequest == launch.request
        }
        if (!uploadStillAvailable) {
            return
        }

        if (filesSelected) {
            markClientActionHandled(launch.messageId)
            val message = if (launch.request.createFolderName.isNullOrBlank()) {
                "已收到文件选择，开始上传。"
            } else {
                "已收到文件选择，开始创建文件夹并上传。"
            }
            _uiState.update { state ->
                state.copy(pendingAttachments = emptyList())
            }
            appendAssistantMessage(message)
        } else {
            appendAssistantMessage("已取消文件选择，这条上传计划还保留着，需要时可以重新选择文件。")
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

    private fun markClientActionHandled(messageId: Long) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    val plan = message.plan
                    if (message.id == messageId && plan?.clientActionControls != null) {
                        message.copy(plan = plan.copy(clientActionControls = null))
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

    private fun allocateMessageId(): Long = nextMessageId++

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
