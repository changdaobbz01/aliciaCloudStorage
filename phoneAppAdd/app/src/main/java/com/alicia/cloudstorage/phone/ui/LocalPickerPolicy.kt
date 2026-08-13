package com.alicia.cloudstorage.phone.ui

internal enum class LocalPickerMode {
    FOLDERS,
    FILES_AND_FOLDERS,
}

internal enum class LocalMediaPreviewKind {
    IMAGE,
    VIDEO,
}

internal fun LocalPickerMode.showsFiles(): Boolean = this == LocalPickerMode.FILES_AND_FOLDERS

internal fun LocalPickerMode.allowsSelection(kind: LocalUploadSelectionKind): Boolean =
    when (this) {
        LocalPickerMode.FOLDERS -> kind == LocalUploadSelectionKind.FOLDER
        LocalPickerMode.FILES_AND_FOLDERS -> true
    }

internal fun localMediaPreviewKind(name: String, mimeType: String?): LocalMediaPreviewKind? {
    val normalizedMimeType = mimeType.normalizedMimeType()
    val extension = name.normalizedExtension()

    return when {
        normalizedMimeType.startsWith("image/") -> LocalMediaPreviewKind.IMAGE
        normalizedMimeType.startsWith("video/") -> LocalMediaPreviewKind.VIDEO
        extension in IMAGE_EXTENSIONS -> LocalMediaPreviewKind.IMAGE
        extension in VIDEO_EXTENSIONS -> LocalMediaPreviewKind.VIDEO
        else -> null
    }
}

private fun String?.normalizedMimeType(): String =
    orEmpty().substringBefore(';').trim().lowercase()

private fun String.normalizedExtension(): String =
    substringAfterLast('.', missingDelimiterValue = "").trim().lowercase()

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "avi", "webm", "m4v", "3gp")
