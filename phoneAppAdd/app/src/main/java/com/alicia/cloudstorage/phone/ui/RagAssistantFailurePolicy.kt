package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.ApiException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal data class RagAssistantFailure(
    val userMessage: String,
    val retryWithoutStreaming: Boolean,
    val markOffline: Boolean,
)

internal fun Throwable.toRagAssistantFailure(): RagAssistantFailure {
    val causes = causeChain()

    causes.filterIsInstance<ConnectException>().firstOrNull()?.let {
        return RagAssistantFailure(
            userMessage = "安安的服务暂时没有连接上，请稍后再试。",
            retryWithoutStreaming = false,
            markOffline = true,
        )
    }
    causes.filterIsInstance<SocketTimeoutException>().firstOrNull()?.let {
        return RagAssistantFailure(
            userMessage = "这次理解花得有点久，安安仍然在线，请再试一次。",
            retryWithoutStreaming = false,
            markOffline = false,
        )
    }
    causes.filterIsInstance<UnknownHostException>().firstOrNull()?.let {
        return RagAssistantFailure(
            userMessage = "当前网络暂时找不到安安的服务，请检查网络后再试。",
            retryWithoutStreaming = false,
            markOffline = true,
        )
    }
    causes.filterIsInstance<SSLException>().firstOrNull()?.let {
        return RagAssistantFailure(
            userMessage = "安安暂时无法建立安全连接，请稍后再试。",
            retryWithoutStreaming = false,
            markOffline = true,
        )
    }
    causes.filterIsInstance<ApiException>().firstOrNull()?.let { error ->
        return RagAssistantFailure(
            userMessage = error.message.takeIf { it.isNotBlank() } ?: DEFAULT_RAG_FAILURE_MESSAGE,
            retryWithoutStreaming = error.status in STREAM_FALLBACK_HTTP_STATUSES,
            markOffline = false,
        )
    }
    causes.filterIsInstance<IOException>().firstOrNull()?.let {
        return RagAssistantFailure(
            userMessage = "这次连接意外中断了，请再试一次。",
            retryWithoutStreaming = false,
            markOffline = false,
        )
    }

    return RagAssistantFailure(
        userMessage = DEFAULT_RAG_FAILURE_MESSAGE,
        retryWithoutStreaming = true,
        markOffline = false,
    )
}

internal fun Throwable.readableRagMessage(): String = toRagAssistantFailure().userMessage

private fun Throwable.causeChain(): List<Throwable> {
    val causes = mutableListOf<Throwable>()
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && current !in visited) {
        causes += current
        visited += current
        current = current.cause
    }
    return causes
}

private const val DEFAULT_RAG_FAILURE_MESSAGE = "安安暂时没有回应，请稍后再试。"
private val STREAM_FALLBACK_HTTP_STATUSES = setOf(404, 405, 406, 415, 501)
