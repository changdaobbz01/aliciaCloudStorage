package com.alicia.cloudstorage.phone.data

import com.google.gson.Gson
import com.google.gson.JsonParser
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class RagAssistantClient(
    private val serviceFactory: RagAssistantServiceFactory = RagAssistantServiceFactory(),
) {
    private val gson = Gson()

    suspend fun plan(
        baseUrl: String,
        token: String?,
        message: String,
        conversationId: String?,
    ): RagAssistantPlanResponse =
        serviceFactory.serviceFor(baseUrl)
            .plan(
                authorization = token
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "Bearer $it" },
                payload = RagAssistantPlanRequest(
                    message = message,
                    conversationId = conversationId?.takeIf { it.isNotBlank() },
                ),
            )
            .requireRagBody(fallback = "安安暂时连接不上，请稍后再试。")

    fun planStream(
        baseUrl: String,
        token: String?,
        message: String,
        conversationId: String?,
    ): Flow<RagAssistantStreamEvent> = flow {
        val serviceBundle = serviceFactory.bundleFor(baseUrl)
        val payload = RagAssistantPlanRequest(
            message = message,
            conversationId = conversationId?.takeIf { it.isNotBlank() },
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
            val reader = body.charStream().buffered()
            try {
                var eventName = ""
                val dataLines = mutableListOf<String>()

                fun flushEvent(): RagAssistantStreamEvent? {
                    val event = parseSseEvent(eventName, dataLines)
                    eventName = ""
                    dataLines.clear()
                    return event
                }

                while (true) {
                    val rawLine = reader.readLine() ?: break
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

private fun <T> Response<T>.requireRagBody(fallback: String): T {
    if (isSuccessful) {
        return body() ?: throw ApiException(fallback, status = code())
    }

    val rawBody = runCatching { errorBody()?.string() }.getOrNull()
    throw ApiException(
        message = rawBody.toReadableRagError(code(), fallback),
        status = code(),
    )
}

private fun String?.toReadableRagError(status: Int, fallback: String): String {
    val body = this?.trim().orEmpty()

    if (body.isNotEmpty() && !body.isHtmlDocument()) {
        runCatching {
            JsonParser.parseString(body)
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.let { jsonObject ->
                    listOf("error", "message", "detail")
                        .firstNotNullOfOrNull { key ->
                            jsonObject.get(key)
                                ?.takeIf { it.isJsonPrimitive }
                                ?.asString
                                ?.takeIf { value -> value.isNotBlank() }
                        }
                }
        }.getOrNull()?.let { return it }

        return body
    }

    return when (status) {
        400 -> "发送给安安的内容不完整，请重新输入。"
        401 -> "登录状态已过期，请重新登录后再问安安。"
        403 -> "当前账号暂时不能使用安安助手。"
        404 -> "没有找到安安助手服务，请检查 RAG 地址配置。"
        429 -> "请求太频繁了，请稍后再问安安。"
        502, 503, 504 -> "安安服务暂时不可用，请稍后再试。"
        in 500..599 -> "安安服务处理失败，请稍后再试。"
        else -> fallback
    }
}

private fun String.isHtmlDocument(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("<!doctype html") ||
        normalized.startsWith("<html") ||
        normalized.contains("<body")
}
