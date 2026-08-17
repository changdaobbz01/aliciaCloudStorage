package com.alicia.cloudstorage.phone.ui

import android.net.Uri
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.User
import com.alicia.cloudstorage.phone.data.UserRole
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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

private fun parseApiInstant(value: String): Instant? =
    runCatching {
        OffsetDateTime.parse(value).toInstant()
    }.recoverCatching {
        // Production historically emitted UTC LocalDateTime values without an offset.
        LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
    }.getOrNull()

fun formatDateTime(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (value.isNullOrBlank()) {
        return "暂无"
    }

    return parseApiInstant(value)
        ?.atZone(zoneId)
        ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        ?: value.replace('T', ' ')
}

fun formatMonthDay(
    value: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (value.isNullOrBlank()) {
        return "暂无"
    }

    val date = parseApiInstant(value)
        ?.atZone(zoneId)
        ?.toLocalDate()
        ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

    return date?.format(DateTimeFormatter.ofPattern("MM-dd")) ?: value
}

fun formatRelativeOrDateTime(
    value: String?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (value.isNullOrBlank()) {
        return "暂无"
    }

    val updatedAt = parseApiInstant(value) ?: return formatDateTime(value, zoneId)

    val age = Duration.between(updatedAt, now)
    if (age.isNegative || age >= Duration.ofDays(1)) {
        return formatDateTime(value, zoneId)
    }

    return when {
        age < Duration.ofMinutes(1) -> "刚刚"
        age < Duration.ofHours(1) -> "${age.toMinutes().coerceAtLeast(1)}分钟前"
        else -> "${age.toHours().coerceAtLeast(1)}小时前"
    }
}

fun formatNodeTypeLabel(node: StorageNode): String {
    if (node.type == StorageNodeType.FOLDER) {
        return "文件夹"
    }

    val mimeType = node.mimeType.orEmpty().lowercase()
    val extension = node.extension.orEmpty().lowercase()
    return when {
        mimeType.startsWith("image/") -> "图片"
        mimeType.startsWith("video/") -> "视频"
        mimeType.startsWith("audio/") -> "音频"
        extension == "pdf" || mimeType == "application/pdf" -> "PDF"
        extension in setOf("zip", "rar", "7z", "tar", "gz") -> "压缩包"
        extension in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md") -> "文档"
        extension.isNotBlank() -> extension.uppercase(Locale.US)
        else -> "文件"
    }
}

fun formatHomeRecentMeta(node: StorageNode): String =
    "${formatNodeTypeLabel(node)}   ${formatRelativeOrDateTime(node.updatedAt)}"

fun formatRole(role: UserRole): String =
    when (role) {
        UserRole.ADMIN -> "管理员"
        UserRole.USER -> "普通用户"
    }

fun userAccountLabel(user: User): String =
    user.email?.takeIf { it.isNotBlank() } ?: user.phoneNumber

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
