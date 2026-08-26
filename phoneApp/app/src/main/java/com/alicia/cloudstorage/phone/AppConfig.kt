package com.alicia.cloudstorage.phone

const val MAINLAND_BASE_URL = "https://windwindwind-alicia.cn"
const val OFFICIAL_ICP_RECORD = "鄂ICP备2026018755号-2"

private const val LEGACY_HONG_KONG_HTTP_BASE_URL = "http://43.132.237.15"
private const val LEGACY_HONG_KONG_HTTPS_BASE_URL = "https://43.132.237.15"
private const val LEGACY_MAINLAND_HTTP_BASE_URL = "http://windwindwind-alicia.cn"
private const val LEGACY_MAINLAND_HTTP_WWW_BASE_URL = "http://www.windwindwind-alicia.cn"

fun normalizeConfiguredBaseUrl(value: String): String = value.trim().removeSuffix("/")

fun migrateSavedBaseUrl(savedBaseUrl: String?, defaultBaseUrl: String): String {
    val normalizedDefault = normalizeConfiguredBaseUrl(defaultBaseUrl)
    val normalizedSaved = savedBaseUrl?.trim()?.removeSuffix("/")

    return when (normalizedSaved) {
        null,
        "" -> normalizedDefault

        LEGACY_HONG_KONG_HTTP_BASE_URL,
        LEGACY_HONG_KONG_HTTPS_BASE_URL,
        LEGACY_MAINLAND_HTTP_BASE_URL,
        LEGACY_MAINLAND_HTTP_WWW_BASE_URL -> normalizedDefault

        else -> normalizedSaved
    }
}

fun describeAccessEnvironment(baseUrl: String): String =
    when (normalizeConfiguredBaseUrl(baseUrl)) {
        MAINLAND_BASE_URL -> "正式服务"
        else -> normalizeConfiguredBaseUrl(baseUrl)
    }
