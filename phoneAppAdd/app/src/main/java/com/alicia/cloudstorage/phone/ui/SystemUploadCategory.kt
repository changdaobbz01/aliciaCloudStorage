package com.alicia.cloudstorage.phone.ui

internal enum class SystemUploadCategory(
    val mimeTypes: List<String>,
) {
    MEDIA(
        listOf(
            "image/*",
            "video/*",
        ),
    ),
    DOCUMENTS(
        listOf(
            "application/pdf",
            "text/*",
            "application/rtf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        ),
    ),
    ARCHIVES(
        listOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/gzip",
            "application/x-gzip",
            "application/x-bzip2",
            "application/x-xz",
        ),
    ),
    AUDIO(listOf("audio/*")),
    OTHER(listOf("*/*")),
}
