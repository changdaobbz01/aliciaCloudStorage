package com.alicia.cloudstorage.phone.ui

import android.net.Uri
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.User
import com.alicia.cloudstorage.phone.data.UserRole
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val BYTES_PER_GIB = 1024L * 1024 * 1024

fun formatBytes(value: Long): String {
    if (value <= 0L) {
        return "0 B"
    }

    if (value < 1024L) {
        return "$value B"
    }

    val units = listOf("KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var unitIndex = -1

    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex += 1
    }

    val decimals = if (size >= 100) 0 else 1
    return String.format(Locale.US, "%.${decimals}f %s", size, units[unitIndex])
}

fun formatOptionalBytes(value: Long?): String =
    value?.let(::formatBytes) ?: "无限制"

fun formatPercent(used: Long, total: Long?): Int {
    if (total == null || total <= 0L) {
        return 0
    }

    return ((used.toDouble() / total.toDouble()) * 100)
        .toInt()
        .coerceIn(0, 100)
}

fun formatDateLabel(value: String): String {
    return try {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("M/d"))
    } catch (_: DateTimeParseException) {
        value
    }
}

fun formatDateTime(value: String?): String {
    if (value.isNullOrBlank()) {
        return "暂无"
    }

    return runCatching {
        OffsetDateTime.parse(value)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.recoverCatching {
        LocalDateTime.parse(value)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrElse { value.replace('T', ' ') }
}

fun formatRole(role: UserRole): String =
    when (role) {
        UserRole.ADMIN -> "管理员"
        UserRole.USER -> "普通用户"
    }

fun formatNodeMeta(node: StorageNode): String =
    when (node.type) {
        StorageNodeType.FOLDER -> "文件夹 · ${formatDateTime(node.updatedAt)}"
        StorageNodeType.FILE -> "${formatBytes(node.size)} · ${formatDateTime(node.updatedAt)}"
    }

fun userUsageLabel(user: User): String =
    "${formatBytes(user.usedBytes)} / ${formatOptionalBytes(user.storageQuotaBytes)}"

fun resolveUserAvatarUrl(baseUrl: String, user: User): String? {
    val avatarUrl = user.avatarUrl?.trim().orEmpty()
    if (avatarUrl.isBlank()) {
        return null
    }

    return when {
        avatarUrl.startsWith("cos:") ->
            "${baseUrl.removeSuffix("/")}/api/auth/avatar/${user.id}?v=${Uri.encode(avatarUrl)}"

        avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") -> avatarUrl
        avatarUrl.startsWith("/") -> "${baseUrl.removeSuffix("/")}$avatarUrl"
        else -> "${baseUrl.removeSuffix("/")}/$avatarUrl"
    }
}

fun formatGigabytesInput(value: Long): String =
    String.format(Locale.US, "%.2f", value.toDouble() / BYTES_PER_GIB.toDouble())
        .trimEnd('0')
        .trimEnd('.')
