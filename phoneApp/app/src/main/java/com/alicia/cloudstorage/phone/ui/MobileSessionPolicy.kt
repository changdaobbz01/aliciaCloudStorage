package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.ApiException

internal const val MOBILE_SESSION_EXPIRED_MESSAGE = "登录状态已过期，请重新登录。"
internal const val MOBILE_SESSION_INCOMPLETE_MESSAGE = "登录信息不完整，请重新登录。"
private const val MOBILE_REQUEST_FAILED_MESSAGE = "请求失败，请稍后再试。"

internal fun Throwable.isMobileAuthExpired(): Boolean =
    this is ApiException && status == 401

internal fun Throwable.mobileReadableMessage(): String =
    when {
        isMobileAuthExpired() -> MOBILE_SESSION_EXPIRED_MESSAGE
        !message.isNullOrBlank() -> message!!
        else -> MOBILE_REQUEST_FAILED_MESSAGE
    }
