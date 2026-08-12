package com.alicia.cloudstorage.phone.ui

import android.net.Uri

internal enum class LocalUploadSelectionKind {
    FILE,
    FOLDER,
}

internal data class LocalUploadSelection(
    val uri: Uri,
    val kind: LocalUploadSelectionKind,
    val displayName: String? = null,
)

internal data class LocalFolderUploadFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val relativeDirectory: List<String>,
)

internal data class LocalFolderUploadPlan(
    val rootName: String,
    val directories: List<List<String>>,
    val files: List<LocalFolderUploadFile>,
)

internal data class UploadBatchSummary(
    var totalFiles: Int = 0,
    var successCount: Int = 0,
    var createdFolderCount: Int = 0,
    var firstError: Throwable? = null,
) {
    val changedStorage: Boolean
        get() = successCount > 0 || createdFolderCount > 0

    fun toOutcome(errorMessage: String? = null): OperationOutcome =
        when {
            firstError == null && totalFiles == 0 && createdFolderCount > 0 -> {
                OperationOutcome.succeeded("文件夹结构已创建完成。")
            }

            firstError == null && totalFiles > 0 && successCount == totalFiles -> {
                OperationOutcome.succeeded(
                    if (successCount == 1) "上传完成。" else "已逐项上传 $successCount 个文件。",
                )
            }

            successCount > 0 -> {
                OperationOutcome.partiallySucceeded(
                    buildString {
                        append("已上传 $successCount 个文件，${totalFiles - successCount} 个未完成。")
                        errorMessage?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
                    },
                )
            }

            else -> OperationOutcome.failed(
                errorMessage?.takeIf { it.isNotBlank() }?.let { "上传没有完成：$it" } ?: "上传失败。",
            )
        }
}

internal data class ResolvedLocalUploadFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val parentId: Long?,
    val locationLabel: String,
)

internal data class QueuedLocalUpload(
    val file: ResolvedLocalUploadFile,
    val taskId: Long,
)
