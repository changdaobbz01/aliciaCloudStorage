package com.alicia.cloudstorage.phone.data

import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RagAssistantClientStreamTest {
    @Test
    fun retainsTheFinalResponseWhenTheConnectionBreaksBeforeDone() {
        MockWebServer().use { server ->
            server.enqueue(
                streamResponse(finalEvent + "\n".repeat(2048))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )

            val events = readStream(server)

            assertEquals(listOf("final"), events.map { it.type })
            assertEquals("源 文件.pdf", events.single().response?.assistantText)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun stillReportsATransportFailureBeforeTheFinalResponse() {
        MockWebServer().use { server ->
            server.enqueue(
                streamResponse(statusEvent + "\n".repeat(2048) + finalEvent)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )

            assertThrows(IOException::class.java) { readStream(server) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun keepsTheDoneEventForACompleteStream() {
        MockWebServer().use { server ->
            server.enqueue(streamResponse(statusEvent + finalEvent + doneEvent))

            assertEquals(listOf("status", "final", "done"), readStream(server).map { it.type })
        }
    }

    private fun streamResponse(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    private fun readStream(server: MockWebServer): List<RagAssistantStreamEvent> = runBlocking {
        RagAssistantClient().planStream(
            baseUrl = server.url("/rag/").toString(),
            token = null,
            message = "synthetic stream check",
            conversationId = null,
            clientContext = null,
        ).toList()
    }

    private val statusEvent = "event:status\ndata:{\"type\":\"status\",\"text\":\"working\"}\n\n"
    private val finalEvent =
        "event:final\ndata:{\"type\":\"final\",\"response\":{\"assistantText\":\"源 文件.pdf\"}}\n\n"
    private val doneEvent = "event:done\ndata:{\"type\":\"done\"}\n\n"
}
