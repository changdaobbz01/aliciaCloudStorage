package com.alicia.cloudstorage.phone.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.alicia.cloudstorage.phone.data.ApiException

internal class LocalFolderUploadPlanner(
    private val maxFiles: Int,
    private val maxDirectories: Int,
) {
    fun build(context: Context, treeUri: Uri): LocalFolderUploadPlan {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?.takeIf { document -> document.exists() && document.isDirectory && document.canRead() }
            ?: throw ApiException("无法读取你选择的文件夹。", 400)
        val rootName = root.name
            ?.trim()
            ?.takeIf { name -> name.isNotBlank() }
            ?: "上传文件夹"
        val directories = mutableListOf<List<String>>()
        val files = mutableListOf<LocalFolderUploadFile>()

        fun visit(directory: DocumentFile, relativeDirectory: List<String>) {
            val children = runCatching { directory.listFiles().toList() }
                .getOrElse { error -> throw ApiException(error.message ?: "读取文件夹内容失败。", 400) }
                .sortedWith(
                    compareByDescending<DocumentFile> { child -> child.isDirectory }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { child -> child.name.orEmpty() },
                )

            children.forEach { child ->
                val childName = child.name?.trim().orEmpty()
                if (childName.isBlank()) {
                    return@forEach
                }
                when {
                    child.isDirectory -> {
                        if (directories.size >= maxDirectories) {
                            throw ApiException("单次最多上传 $maxDirectories 个子文件夹。", 400)
                        }
                        val childPath = relativeDirectory + childName
                        directories += childPath
                        visit(child, childPath)
                    }

                    child.isFile -> {
                        if (files.size >= maxFiles) {
                            throw ApiException("单次最多上传 $maxFiles 个文件。", 400)
                        }
                        files += LocalFolderUploadFile(
                            uri = child.uri,
                            name = childName,
                            sizeBytes = child.length().takeIf { size -> size >= 0L },
                            relativeDirectory = relativeDirectory,
                        )
                    }
                }
            }
        }

        visit(root, emptyList())
        return LocalFolderUploadPlan(
            rootName = rootName,
            directories = directories.toList(),
            files = files.toList(),
        )
    }
}
