package com.alicia.cloudstorage.phone

import java.net.URI

object AliciaShareLinks {
    private val shareCodePattern = Regex("^[A-Za-z0-9_-]{4,40}$")

    fun build(baseUrl: String, shareCode: String): String? {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedShareCode = shareCode.trim()
        if (!shareCodePattern.matches(normalizedShareCode)) {
            return null
        }

        val baseUri = runCatching { URI(normalizedBaseUrl) }.getOrNull() ?: return null
        if (
            baseUri.scheme?.lowercase() !in setOf("http", "https") ||
            baseUri.host.isNullOrBlank() ||
            baseUri.rawUserInfo != null ||
            baseUri.rawQuery != null ||
            baseUri.rawFragment != null
        ) {
            return null
        }

        return "$normalizedBaseUrl/share/$normalizedShareCode"
    }
}
