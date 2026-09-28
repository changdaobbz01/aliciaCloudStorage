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

internal fun <T> RetrofitResponse<T>.requireBodyWithExecutionError(fallback: String): T {
    if (isSuccessful) {
        return body() ?: throw ApiException(fallback, status = code())
    }

    throw ApiException(
        message = runCatching { errorBody()?.string() }
            .getOrNull()
            .toReadableExecutionError(code(), fallback),
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

internal fun String?.toReadableExecutionError(status: Int, fallback: String): String {
    val statusMessage = executionStatusToReadableError(status, fallback)
    val body = this?.trim().orEmpty()
    if (body.isEmpty() || body.isMachineErrorDocument()) {
        return statusMessage
    }

    val responseMessage = parseApiErrorMessage(body, RAG_ERROR_FIELDS) ?: return statusMessage
    return executionCodeToReadableError(responseMessage) ?: responseMessage
        .takeUnless(String::looksLikeMachineCode)
        ?: statusMessage
}

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

private fun executionStatusToReadableError(status: Int, fallback: String): String =
    when (status) {
        400 -> "任务确认信息不完整，请重新生成计划。"
        401 -> "登录状态已过期；已确认的云端任务仍会继续执行。"
        403 -> "当前账号不在云端执行灰度范围内。"
        404 -> "云端执行功能尚未开放，或任务已经不可用。"
        409 -> "任务状态已经变化，请重新获取后再操作。"
        422 -> "该计划不符合当前云端执行范围。"
        429 -> "云端任务请求过于频繁，请稍后重试。"
        502, 503, 504 -> "云端执行服务暂时不可用，请稍后重试。"
        in 500..599 -> "云端执行服务处理失败，请稍后重试。"
        else -> fallback
    }

private fun executionCodeToReadableError(code: String): String? =
    when (code.trim().lowercase()) {
        "rag_admin_confirmation_required" -> "当前账号不在云端执行灰度范围内。"
        "execution_version_conflict" -> "任务状态已经变化，请刷新后重试。"
        "execution_not_confirmable" -> "这项任务当前不能再次确认。"
        "execution_confirmation_expired" -> "这项任务已过确认期限，请重新生成计划。"
        "execution_not_cancellable" -> "这项任务当前不能取消。"
        "execution_not_found" -> "没有找到这项云端任务。"
        "execution_api_disabled", "execution_confirmation_disabled" -> "云端执行功能尚未开放。"
        "action_not_enabled" -> "该操作尚未加入云端执行灰度范围。"
        "phase4_single_step_required", "phase4_single_object_required" ->
            "该计划超出当前单步骤、单对象执行范围。"
        "identity_unavailable" -> "身份服务暂时不可用，请稍后重试。"
        else -> null
    }

private fun String.looksLikeMachineCode(): Boolean = matches(Regex("[a-z0-9]+(?:_[a-z0-9]+)+"))
