package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.ApiException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagAssistantFailurePolicyTest {

    @Test
    fun `connection refusal hides endpoint details and skips duplicate request`() {
        val failure = ConnectException("Failed to connect to /127.0.0.1:8081")
            .toRagAssistantFailure()

        assertEquals("安安的服务暂时没有连接上，请稍后再试。", failure.userMessage)
        assertFalse(failure.userMessage.contains("127.0.0.1"))
        assertFalse(failure.retryWithoutStreaming)
        assertTrue(failure.markOffline)
    }

    @Test
    fun `wrapped network errors are classified by their cause`() {
        val failure = IllegalStateException(
            "request failed",
            ConnectException("Connection refused"),
        ).toRagAssistantFailure()

        assertEquals("安安的服务暂时没有连接上，请稍后再试。", failure.userMessage)
        assertFalse(failure.retryWithoutStreaming)
        assertTrue(failure.markOffline)
    }

    @Test
    fun `common network failures have stable user messages`() {
        assertFalse(SocketTimeoutException("timeout").toRagAssistantFailure().markOffline)
        assertTrue(UnknownHostException("rag.internal").toRagAssistantFailure().markOffline)
        assertFalse(IOException("socket closed").toRagAssistantFailure().markOffline)
        assertEquals(
            "这次理解花得有点久，安安仍然在线，请再试一次。",
            SocketTimeoutException("timeout").readableRagMessage(),
        )
        assertEquals(
            "当前网络暂时找不到安安的服务，请检查网络后再试。",
            UnknownHostException("rag.internal").readableRagMessage(),
        )
        assertEquals(
            "安安暂时无法建立安全连接，请稍后再试。",
            SSLException("handshake failed").readableRagMessage(),
        )
        assertEquals(
            "这次连接意外中断了，请再试一次。",
            IOException("socket closed").readableRagMessage(),
        )
    }

    @Test
    fun `server errors preserve sanitized API message without retry`() {
        val failure = ApiException("安安服务暂时不可用，请稍后再试。", 503)
            .toRagAssistantFailure()

        assertEquals("安安服务暂时不可用，请稍后再试。", failure.userMessage)
        assertFalse(failure.retryWithoutStreaming)
        assertFalse(failure.markOffline)
    }

    @Test
    fun `unsupported stream endpoint falls back to regular request`() {
        val failure = ApiException("没有找到流式接口。", 404)
            .toRagAssistantFailure()

        assertTrue(failure.retryWithoutStreaming)
    }

    @Test
    fun `unexpected stream protocol failure may use regular request`() {
        val failure = IllegalStateException("流式响应缺少最终结果。")
            .toRagAssistantFailure()

        assertEquals("安安暂时没有回应，请稍后再试。", failure.userMessage)
        assertTrue(failure.retryWithoutStreaming)
    }
}
