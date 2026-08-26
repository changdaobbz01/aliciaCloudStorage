package com.alicia.cloudstorage.phone.data

import com.google.gson.JsonParser
import okhttp3.Response as OkHttpResponse
import retrofit2.Response as RetrofitResponse

private val CLOUD_ERROR_FIELDS = listOf("error", "message")
private val RAG_ERROR_FIELDS = listOf("error", "message", "detail")

internal fun <T> RetrofitResponse<T>.requireBodyWithCloudError(fallback: String): T {
    if (isSuccessful) {
        return body() ?: throw ApiException(fallback, status = code())
    }

    throw ApiException(
        message = runCatching { errorBody()?.string() }
            .getOrNull()
            .toReadableCloudError(code(), fallback),
        status = code(),
    )
}

internal fun <T> RetrofitResponse<T>.requireBodyWithRagError(fallback: String): T {
    if (isSuccessful) {
        return body() ?: throw ApiException(fallback, status = code())
    }

    throw ApiException(
        message = runCatching { errorBody()?.string() }
            .getOrNull()
            .toReadableRagError(code(), fallback),
        status = code(),
    )
}

internal fun OkHttpResponse.requireSuccessfulSignedDownload(fallback: String) {
    if (isSuccessful) {
        return
    }

    throw ApiException(
        message = runCatching { body?.string() }
            .getOrNull()
            .toReadableCloudError(code, fallback),
        status = code,
    )
}

internal fun String?.toReadableCloudError(status: Int, fallback: String): String =
    toReadableApiError(
        status = status,
        fallback = fallback,
        fields = CLOUD_ERROR_FIELDS,
        statusMessage = ::cloudStatusToReadableError,
    )

internal fun String?.toReadableRagError(status: Int, fallback: String): String =
    toReadableApiError(
        status = status,
        fallback = fallback,
        fields = RAG_ERROR_FIELDS,
        statusMessage = { ragStatusToReadableError(status, fallback) },
    )

private fun String?.toReadableApiError(
    status: Int,
    fallback: String,
    fields: List<String>,
    statusMessage: (Int) -> String?,
): String {
    val readableStatusError = statusMessage(status)
    val body = this?.trim().orEmpty()

    if (body.isNotEmpty() && !body.isMachineErrorDocument()) {
        parseApiErrorMessage(body, fields)?.let { return it }
        return body
    }

    return readableStatusError ?: fallback
}

private fun parseApiErrorMessage(body: String, fields: List<String>): String? =
    runCatching {
        JsonParser.parseString(body)
            .takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.let { jsonObject ->
                fields.firstNotNullOfOrNull { key ->
                    jsonObject.get(key)
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.takeIf { value -> value.isNotBlank() }
                }
            }
    }.getOrNull()

private fun String.isMachineErrorDocument(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("<!doctype html") ||
        normalized.startsWith("<?xml") ||
        normalized.startsWith("<html") ||
        normalized.startsWith("<error>") ||
        normalized.contains("<body")
}

private fun cloudStatusToReadableError(status: Int): String? =
    when (status) {
        400 -> "请求内容不正确，请检查填写的信息。"
        401 -> "登录状态已过期，请重新登录。"
        403 -> "当前账号没有权限执行这个操作。"
        404 -> "请求的资源不存在。"
        413 -> "文件太大，当前最多支持上传 1GB 的文件。请换一个更小的文件后重试。"
        415 -> "当前文件类型不受支持。"
        429 -> "请求过于频繁，请稍后再试。"
        502, 503, 504 -> "服务暂时不可用，请稍后再试。"
        in 500..599 -> "服务器处理失败，请稍后再试。"
        else -> null
    }

private fun ragStatusToReadableError(status: Int, fallback: String): String =
    when (status) {
        400 -> "发送给安安的内容不完整，请重新输入。"
        401 -> "登录状态已过期，请重新登录后再问安安。"
        403 -> "当前账号暂时不能使用安安助手。"
        404 -> "没有找到安安助手服务，请检查 RAG 地址配置。"
        429 -> "请求太频繁了，请稍后再问安安。"
        502, 503, 504 -> "这次处理没有及时完成，请再试一次，安安仍然在线。"
        in 500..599 -> "安安服务处理失败，请稍后再试。"
        else -> fallback
    }
