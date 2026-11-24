package com.alicia.cloudstorage.phone.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.UUID

internal class RagAssistantClient(
    private val serviceFactory: RagAssistantServiceFactory = RagAssistantServiceFactory(),
) {
    private val gson = Gson()

    suspend fun plan(
        baseUrl: String,
        token: String?,
        message: String,
        conversationId: String?,
        clientContext: RagAssistantClientContext?,
        clientEvent: RagAssistantClientEvent? = null,
    ): RagAssistantPlanResponse {
        val response = serviceFactory.serviceFor(baseUrl)
            .plan(
                authorization = token
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Bearer $it" },
                payload = RagAssistantPlanRequest(
                    message = message,
                    conversationId = conversationId?.takeIf { it.isNotBlank() },
                    clientContext = clientContext,
                    clientEvent = clientEvent,
                ),
            )
        return response.requireRagBody(fallback = "安安暂时连接不上，请稍后再试。")
            .copy(feedback = parseFeedbackHeaders(response.headers()["X-Recognition-Trace-Id"], response.headers()["X-Recognition-Feedback"]))
    }

    suspend fun reportFeedback(baseUrl: String, token: String, ref: RagFeedbackReference, category: RagFeedbackCategory) {
        val response = serviceFactory.serviceFor(baseUrl)
            .feedback("Bearer $token", RagCategoryFeedbackRequest(ref.traceId, category.name))
            .requireRagBody("反馈暂时无法提交。")
        if (!response.accepted) throw ApiException("反馈未被接受。", status = 502)
    }

    suspend fun previewCandidate(baseUrl: String, token: String, ref: RagFeedbackReference, expression: String): String =
        serviceFactory.serviceFor(baseUrl)
            .previewCandidate("Bearer $token", RagCandidatePreviewRequest(ref.traceId, expression))
            .requireRagBody("无法生成脱敏预览。").expression?.takeIf { it.isNotBlank() }
            ?: throw ApiException("脱敏预览内容为空。", status = 502)

    suspend fun submitCandidate(baseUrl: String, token: String, ref: RagFeedbackReference, expression: String): String =
        serviceFactory.serviceFor(baseUrl)
            .submitCandidate("Bearer $token", RagCandidateSubmission(ref.traceId, expression, consent = true))
            .requireRagBody("样本暂时无法提交。").candidateId?.takeIf { it.isNotBlank() }
            ?: throw ApiException("样本回执缺失。", status = 502)

    fun planStream(
        baseUrl: String,
        token: String?,
        message: String,
        conversationId: String?,
        clientContext: RagAssistantClientContext?,
        clientEvent: RagAssistantClientEvent? = null,
    ): Flow<RagAssistantStreamEvent> = flow {
        val serviceBundle = serviceFactory.bundleFor(baseUrl)
        val payload = RagAssistantPlanRequest(
            message = message,
            conversationId = conversationId?.takeIf { it.isNotBlank() },
            clientContext = clientContext,
            clientEvent = clientEvent,
        )
        val request = Request.Builder()
            .url("${ensureTrailingSlash(baseUrl)}api/assistant/plan/stream")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json; charset=utf-8")
            .apply {
                token
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header("Authorization", "Bearer $it") }
            }
            .post(gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        serviceBundle.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val rawBody = runCatching { response.body?.string() }.getOrNull()
                throw ApiException(
                    message = rawBody.toReadableRagError(
                        status = response.code,
                        fallback = "安安暂时连接不上，请稍后再试。",
                    ),
                    status = response.code,
                )
            }

            val body = response.body ?: throw ApiException("安安没有返回内容。", status = response.code)
            val feedback = parseFeedbackHeaders(
                response.header("X-Recognition-Trace-Id"), response.header("X-Recognition-Feedback"),
            )
            val reader = body.charStream().buffered()
            try {
                var eventName = ""
                val dataLines = mutableListOf<String>()
                var finalResponseReceived = false

                fun flushEvent(): RagAssistantStreamEvent? {
                    val event = parseSseEvent(eventName, dataLines)
                    eventName = ""
                    dataLines.clear()
                    if (event?.type.equals("final", ignoreCase = true) && event?.response != null) {
                        finalResponseReceived = true
                        return event.copy(response = event.response.copy(feedback = feedback))
                    }
                    return event
                }

                while (true) {
                    val rawLine = try {
                        reader.readLine()
                    } catch (failure: IOException) {
                        if (finalResponseReceived) return@flow
                        throw failure
                    } ?: break
                    val line = rawLine.trimEnd()
                    when {
                        line.isBlank() -> flushEvent()?.let { emit(it) }
                        line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                    }
                }
                flushEvent()?.let { emit(it) }
            } finally {
                reader.close()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseSseEvent(
        eventName: String,
        dataLines: List<String>,
    ): RagAssistantStreamEvent? {
        val data = dataLines.joinToString("\n").trim()
        if (data.isBlank()) {
            return null
        }

        val parsed = gson.fromJson(data, RagAssistantStreamEvent::class.java)
        return parsed.copy(type = parsed.type ?: eventName.takeIf { it.isNotBlank() })
    }
}

internal class RagAssistantServiceFactory {
    private val serviceCache = ConcurrentHashMap<String, RagAssistantServiceBundle>()

    fun serviceFor(baseUrl: String): RagAssistantService =
        bundleFor(baseUrl).service

    fun bundleFor(baseUrl: String): RagAssistantServiceBundle =
        serviceCache.getOrPut(normalizedBaseUrl(baseUrl)) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val service = Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(baseUrl))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RagAssistantService::class.java)
            RagAssistantServiceBundle(service, okHttpClient)
        }

    private fun normalizedBaseUrl(baseUrl: String) = baseUrl.trim().removeSuffix("/")

    private fun ensureTrailingSlash(baseUrl: String) = "${normalizedBaseUrl(baseUrl)}/"
}

internal data class RagAssistantServiceBundle(
    val service: RagAssistantService,
    val okHttpClient: OkHttpClient,
)

private fun ensureTrailingSlash(baseUrl: String) = "${baseUrl.trim().removeSuffix("/")}/"

internal fun parseFeedbackHeaders(traceId: String?, mode: String?): RagFeedbackReference? {
    val candidateEnabled = when (mode) {
        "category" -> false
        "category,candidate" -> true
        else -> return null
    }
    if (traceId?.length != 36) return null
    val id = runCatching { UUID.fromString(traceId).toString() }.getOrNull() ?: return null
    return RagFeedbackReference(id, candidateEnabled).takeIf { id.equals(traceId, ignoreCase = true) }
}

private fun <T> Response<T>.requireRagBody(fallback: String): T =
    requireBodyWithRagError(fallback)
