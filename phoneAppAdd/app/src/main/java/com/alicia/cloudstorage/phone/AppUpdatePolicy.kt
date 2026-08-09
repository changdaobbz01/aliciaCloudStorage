package com.alicia.cloudstorage.phone

import java.net.URI

object AppUpdatePolicy {
    fun resolveDownloadUrl(baseUrl: String, downloadUrl: String): String? {
        if (downloadUrl.isBlank()) return null

        val resolvedUrl = runCatching {
            URI("${baseUrl.trim().removeSuffix("/")}/")
                .resolve(downloadUrl)
                .toString()
        }.getOrElse {
            if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                downloadUrl
            } else {
                "${baseUrl.trim().removeSuffix("/")}/${downloadUrl.trimStart('/')}"
            }
        }

        val resolvedUri = runCatching { URI(resolvedUrl) }.getOrNull() ?: return null
        val scheme = resolvedUri.scheme?.lowercase()
        return resolvedUrl.takeIf {
            scheme in setOf("http", "https") && !resolvedUri.host.isNullOrBlank()
        }
    }
}
