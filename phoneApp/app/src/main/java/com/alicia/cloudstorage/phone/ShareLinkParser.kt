package com.alicia.cloudstorage.phone

import android.net.Uri

object ShareLinkParser {
    private const val OFFICIAL_WEB_HOST = "windwindwind-alicia.cn"
    private val shareCodePattern = Regex("^[A-Za-z0-9_-]{4,40}$")
    private val urlPattern = Regex("""(?i)\b(?:https?://|aliciacloud://)[^\s<>"']+""")
    private val trailingPunctuation = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '"', '\'',
        '。', '，', '；', '：', '！', '？', '）', '】', '》',
    )

    fun shareCodeFromUri(uri: Uri?, baseUrl: String): String? {
        if (uri == null) {
            return null
        }

        return when (uri.scheme?.lowercase()) {
            "https", "http" -> shareCodeFromWebUri(uri, baseUrl)
            "aliciacloud" -> shareCodeFromAppUri(uri)
            else -> null
        }
    }

    fun findShareCodeInText(text: String?, baseUrl: String): String? {
        val source = text?.take(4096).orEmpty()
        if (source.isBlank()) {
            return null
        }

        return urlPattern
            .findAll(source)
            .mapNotNull { match ->
                val candidate = match.value.trimEnd(*trailingPunctuation)
                shareCodeFromUri(Uri.parse(candidate), baseUrl)
            }
            .firstOrNull()
    }

    private fun shareCodeFromWebUri(uri: Uri, baseUrl: String): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host !in allowedWebHosts(baseUrl)) {
            return null
        }

        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != "share") {
            return null
        }

        return segments[1].takeIf(::isValidShareCode)
    }

    private fun shareCodeFromAppUri(uri: Uri): String? {
        val host = uri.host?.lowercase()
        val segments = uri.pathSegments
        val shareCode = when {
            host == "share" -> segments.firstOrNull()
            segments.size >= 2 && segments[0] == "share" -> segments[1]
            else -> null
        }

        return shareCode?.takeIf(::isValidShareCode)
    }

    private fun allowedWebHosts(baseUrl: String): Set<String> {
        val configuredHost = runCatching {
            Uri.parse(baseUrl).host?.lowercase()
        }.getOrNull()

        return buildSet {
            add(OFFICIAL_WEB_HOST)
            configuredHost?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun isValidShareCode(value: String): Boolean =
        shareCodePattern.matches(value)
}
