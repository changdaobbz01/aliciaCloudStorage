package com.alicia.cloudstorage.phone.data

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.ConcurrentHashMap

internal interface RagExecutionGateway {
    suspend fun confirm(
        baseUrl: String,
        token: String,
        executionId: String,
        expectedVersion: Long,
    ): RagExecutionResponse

    suspend fun get(
        baseUrl: String,
        token: String,
        executionId: String,
    ): RagExecutionResponse

    suspend fun cancel(
        baseUrl: String,
        token: String,
        executionId: String,
    ): RagExecutionResponse

    suspend fun completeClientInput(
        baseUrl: String,
        token: String,
        executionId: String,
        expectedVersion: Long,
        stepId: String,
        status: String,
        nodeIds: List<Long> = emptyList(),
        errorCode: String? = null,
    ): RagExecutionResponse
}

internal class RagExecutionClient(
    private val serviceFactory: RagExecutionServiceFactory = RagExecutionServiceFactory(),
) : RagExecutionGateway {
    override suspend fun confirm(
        baseUrl: String,
        token: String,
        executionId: String,
        expectedVersion: Long,
    ): RagExecutionResponse =
        serviceFactory.serviceFor(baseUrl)
            .confirm(
                executionId = executionId,
                authorization = token.asBearerToken(),
                payload = RagExecutionConfirmRequest(expectedVersion),
            )
            .requireBodyWithExecutionError("暂时无法确认云端任务，请稍后重试。")

    override suspend fun get(
        baseUrl: String,
        token: String,
        executionId: String,
    ): RagExecutionResponse =
        serviceFactory.serviceFor(baseUrl)
            .get(executionId, token.asBearerToken())
            .requireBodyWithExecutionError("暂时无法获取云端任务状态。")

    override suspend fun cancel(
        baseUrl: String,
        token: String,
        executionId: String,
    ): RagExecutionResponse =
        serviceFactory.serviceFor(baseUrl)
            .cancel(executionId, token.asBearerToken())
            .requireBodyWithExecutionError("暂时无法取消云端任务，请稍后重试。")

    override suspend fun completeClientInput(
        baseUrl: String,
        token: String,
        executionId: String,
        expectedVersion: Long,
        stepId: String,
        status: String,
        nodeIds: List<Long>,
        errorCode: String?,
    ): RagExecutionResponse =
        serviceFactory.serviceFor(baseUrl)
            .completeClientInput(
                executionId = executionId,
                authorization = token.asBearerToken(),
                payload = RagExecutionClientInputRequest(
                    expectedVersion = expectedVersion,
                    stepId = stepId,
                    status = status,
                    nodeIds = nodeIds,
                    errorCode = errorCode,
                ),
            )
            .requireBodyWithExecutionError("暂时无法提交文件上传结果，请稍后重试。")
}

internal data class RagExecutionConfirmRequest(
    val expectedVersion: Long,
)

internal data class RagExecutionClientInputRequest(
    val expectedVersion: Long,
    val stepId: String,
    val status: String,
    val nodeIds: List<Long>,
    val errorCode: String? = null,
)

internal data class RagExecutionResponse(
    val executionId: String?,
    val status: String?,
    val version: Long?,
    val summary: String?,
    val risk: String?,
    val expiresAt: String?,
    val confirmedAt: String?,
    val queuedAt: String?,
    val startedAt: String?,
    val finishedAt: String?,
    val resultCode: String?,
    val result: Map<String, Any>?,
    val errorCode: String?,
    val steps: List<RagExecutionStepResponse>?,
)

internal data class RagExecutionStepResponse(
    val stepId: String?,
    val index: Int?,
    val actionType: String?,
    val status: String?,
    val attempts: Int?,
    val startedAt: String?,
    val finishedAt: String?,
    val errorCode: String?,
    val result: Map<String, Any>?,
)

internal interface RagExecutionService {
    @POST("api/executions/{executionId}/confirm")
    suspend fun confirm(
        @Path("executionId") executionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: RagExecutionConfirmRequest,
    ): Response<RagExecutionResponse>

    @GET("api/executions/{executionId}")
    suspend fun get(
        @Path("executionId") executionId: String,
        @Header("Authorization") authorization: String,
    ): Response<RagExecutionResponse>

    @POST("api/executions/{executionId}/cancel")
    suspend fun cancel(
        @Path("executionId") executionId: String,
        @Header("Authorization") authorization: String,
    ): Response<RagExecutionResponse>

    @POST("api/executions/{executionId}/client-input")
    suspend fun completeClientInput(
        @Path("executionId") executionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: RagExecutionClientInputRequest,
    ): Response<RagExecutionResponse>
}

internal class RagExecutionServiceFactory {
    private val services = ConcurrentHashMap<String, RagExecutionService>()

    fun serviceFor(baseUrl: String): RagExecutionService =
        services.getOrPut(normalizedBaseUrl(baseUrl)) {
            Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(baseUrl))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RagExecutionService::class.java)
        }

    private fun normalizedBaseUrl(baseUrl: String): String = baseUrl.trim().removeSuffix("/")

    private fun ensureTrailingSlash(baseUrl: String): String = "${normalizedBaseUrl(baseUrl)}/"
}

private fun String.asBearerToken(): String {
    val normalized = trim()
    require(normalized.isNotEmpty()) { "RAG execution requires a user access token." }
    return "Bearer $normalized"
}
