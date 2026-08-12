package com.alicia.cloudstorage.phone.ui

internal data class AiChatClientUploadRequest(
    val parentId: Long?,
    val targetName: String?,
    val createFolderName: String? = null,
)

internal data class AiChatClientUploadLaunch(
    val operationId: Long,
    val messageId: Long,
    val request: AiChatClientUploadRequest,
)

internal data class AiChatClientUploadCallbacks(
    val onSelectionResolved: (Boolean) -> Unit,
    val onExecutionCompleted: (OperationOutcome) -> Unit,
)

internal class AiChatClientUploadTracker {
    private var nextOperationId = 1L
    private val pending = mutableMapOf<Long, PendingUpload>()

    fun start(messageId: Long, request: AiChatClientUploadRequest): AiChatClientUploadLaunch? {
        if (pending.isNotEmpty()) {
            return null
        }
        val launch = AiChatClientUploadLaunch(
            operationId = nextOperationId++,
            messageId = messageId,
            request = request,
        )
        pending[launch.operationId] = PendingUpload(launch)
        return launch
    }

    fun markSelected(launch: AiChatClientUploadLaunch, executionMessageId: Long): Boolean {
        val current = pending[launch.operationId]?.takeIf { it.launch == launch } ?: return false
        if (current.executionMessageId != null) {
            return false
        }
        pending[launch.operationId] = current.copy(executionMessageId = executionMessageId)
        return true
    }

    fun cancel(launch: AiChatClientUploadLaunch): Boolean {
        val current = pending[launch.operationId]?.takeIf { it.launch == launch } ?: return false
        pending.remove(current.launch.operationId)
        return true
    }

    fun complete(launch: AiChatClientUploadLaunch): Long? {
        val current = pending[launch.operationId]?.takeIf { it.launch == launch } ?: return null
        val executionMessageId = current.executionMessageId ?: return null
        pending.remove(launch.operationId)
        return executionMessageId
    }

    fun clear() {
        pending.clear()
    }

    private data class PendingUpload(
        val launch: AiChatClientUploadLaunch,
        val executionMessageId: Long? = null,
    )
}
