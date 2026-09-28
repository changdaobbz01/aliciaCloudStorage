package com.alicia.cloudstorage.phone.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RagExecutionClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RagExecutionClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RagExecutionClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `confirm sends only execution id version and bearer token`() = runBlocking {
        server.enqueue(jsonResponse(status = "QUEUED", version = 1))

        val response = client.confirm(
            baseUrl = server.url("/").toString(),
            token = "access-token",
            executionId = "execution-1",
            expectedVersion = 0,
        )

        assertEquals("QUEUED", response.status)
        val request = server.takeRequest()
        assertEquals("/api/executions/execution-1/confirm", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertEquals("{\"expectedVersion\":0}", request.body.readUtf8())
        assertTrue(request.bodySize < 64)
    }

    @Test
    fun `query and cancel use execution service routes`() = runBlocking {
        server.enqueue(jsonResponse(status = "RUNNING", version = 2))
        server.enqueue(jsonResponse(status = "CANCELLED", version = 3))

        assertEquals(
            "RUNNING",
            client.get(server.url("/").toString(), "token", "execution-2").status,
        )
        assertEquals(
            "CANCELLED",
            client.cancel(server.url("/").toString(), "token", "execution-2").status,
        )

        assertEquals("GET /api/executions/execution-2 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("POST /api/executions/execution-2/cancel HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun `client input reports only typed upload outcome`() = runBlocking {
        server.enqueue(jsonResponse(status = "SUCCEEDED", version = 4))

        val response = client.completeClientInput(
            baseUrl = server.url("/").toString(),
            token = "access-token",
            executionId = "execution-4",
            expectedVersion = 3,
            stepId = "step-4",
            status = "SUCCEEDED",
            nodeIds = listOf(41L, 42L),
        )

        assertEquals("SUCCEEDED", response.status)
        val request = server.takeRequest()
        assertEquals("/api/executions/execution-4/client-input", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertEquals(
            "{\"expectedVersion\":3,\"stepId\":\"step-4\",\"status\":\"SUCCEEDED\",\"nodeIds\":[41,42]}",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `machine error is converted to stable user message`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"rag_admin_confirmation_required\"}"),
        )

        try {
            client.confirm(server.url("/").toString(), "token", "execution-3", 0)
            throw AssertionError("Expected ApiException")
        } catch (error: ApiException) {
            assertEquals(403, error.status)
            assertEquals("当前账号不在云端执行灰度范围内。", error.message)
        }
    }

    private fun jsonResponse(status: String, version: Long): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """{"executionId":"execution-1","status":"$status","version":$version,"steps":[]}""",
            )
}
