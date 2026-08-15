package com.alicia.cloudstorage.phone.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alicia.cloudstorage.phone.FileDetailArgs
import com.alicia.cloudstorage.phone.data.AliciaRepository
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private const val MAX_DETAIL_TEXT_PREVIEW_BYTES = 2L * 1024 * 1024

private val detailTextExtensions = setOf(
    "txt",
    "md",
    "csv",
    "tsv",
    "log",
    "json",
    "xml",
    "yaml",
    "yml",
)

data class FileDetailUiState(
    val currentNode: StorageNode,
    val galleryNodes: List<StorageNode> = listOf(currentNode),
    val galleryIndex: Int = 0,
    val loading: Boolean = true,
    val kind: PreviewKind? = null,
    val textContent: String = "",
    val previewUrl: String? = null,
    val error: String? = null,
    val activeOperation: FileDetailOperation? = null,
    val downloadPercent: Int? = null,
    val moveFolders: List<StorageNode> = emptyList(),
    val moveFoldersLoading: Boolean = false,
    val message: String? = null,
    val renameDialogOpen: Boolean = false,
    val renameError: String? = null,
    val deleted: Boolean = false,
    val contentChanged: Boolean = false,
)

enum class FileDetailOperation {
    DOWNLOAD,
    RENAME,
    MOVE,
    DELETE,
}

class FileDetailViewModel(
    private val appContext: Context,
    private val args: FileDetailArgs,
    private val repository: AliciaRepository,
) : ViewModel() {
    private val initialGalleryNodes = args.galleryNodes
        .filter { it.type == StorageNodeType.FILE }
        .ifEmpty { listOf(args.node) }
    private val initialGalleryIndex = initialGalleryNodes
        .indexOfFirst { it.id == args.node.id }
        .takeIf { it >= 0 }
        ?: 0
    private val _uiState = MutableStateFlow(
        FileDetailUiState(
            currentNode = initialGalleryNodes[initialGalleryIndex],
            galleryNodes = initialGalleryNodes,
            galleryIndex = initialGalleryIndex,
        ),
    )
    val uiState = _uiState.asStateFlow()
    private var previewGeneration = 0L

    init {
        loadPreview()
    }

    fun retry() {
        loadPreview()
    }

    fun openGalleryIndex(index: Int) {
        val state = _uiState.value
        if (
            state.activeOperation != null ||
            state.renameDialogOpen ||
            index !in state.galleryNodes.indices ||
            index == state.galleryIndex
        ) {
            return
        }

        val nextNode = state.galleryNodes[index]
        _uiState.update {
            it.copy(
                currentNode = nextNode,
                galleryIndex = index,
                loading = true,
                kind = null,
                textContent = "",
                previewUrl = null,
                error = null,
                downloadPercent = null,
                moveFolders = emptyList(),
                moveFoldersLoading = false,
                message = null,
                renameDialogOpen = false,
                renameError = null,
                deleted = false,
            )
        }
        loadPreview()
    }

    fun beginRename() {
        val state = _uiState.value
        if (state.activeOperation != null || state.deleted) return
        _uiState.update { it.copy(renameDialogOpen = true, renameError = null) }
    }

    fun dismissRename() {
        if (_uiState.value.activeOperation == FileDetailOperation.RENAME) return
        _uiState.update { it.copy(renameDialogOpen = false, renameError = null) }
    }

    fun rename(rawName: String) {
        val state = _uiState.value
        if (state.activeOperation != null || state.deleted) return
        val validation = validateNodeName(rawName, state.currentNode.name)
        if (!validation.isValid) {
            _uiState.update { it.copy(renameError = validation.errorMessage) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeOperation = FileDetailOperation.RENAME,
                    renameError = null,
                    message = null,
                )
            }
            try {
                val renamedNode = repository.renameNode(
                    baseUrl = args.baseUrl,
                    token = args.authToken,
                    nodeId = state.currentNode.id,
                    name = validation.normalizedName,
                )
                _uiState.update {
                    it.copy(
                        currentNode = renamedNode,
                        galleryNodes = it.galleryNodes.map { galleryNode ->
                            if (galleryNode.id == renamedNode.id) renamedNode else galleryNode
                        },
                        activeOperation = null,
                        renameDialogOpen = false,
                        renameError = null,
                        contentChanged = true,
                        message = "已重命名为：${renamedNode.name}",
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        activeOperation = null,
                        renameError = error.message?.takeIf(String::isNotBlank) ?: "重命名失败，请稍后重试。",
                    )
                }
            }
        }
    }

    fun downloadToUri(destinationUri: Uri) {
        val node = _uiState.value.currentNode
        if (_uiState.value.activeOperation != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeOperation = FileDetailOperation.DOWNLOAD,
                    downloadPercent = 0,
                    message = null,
                )
            }
            runCatching {
                repository.saveDownloadedFileToUriViaSignedUrl(
                    context = appContext,
                    baseUrl = args.baseUrl,
                    token = args.authToken,
                    fileId = node.id,
                    destinationUri = destinationUri,
                    onProgress = { progress ->
                        val percent = progress.totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { total ->
                                ((progress.transferredBytes.toDouble() / total.toDouble()) * 100)
                                    .toInt()
                                    .coerceIn(0, 100)
                            }
                        _uiState.update { state -> state.copy(downloadPercent = percent) }
                    },
                )
            }.onSuccess { fileName ->
                _uiState.update {
                    it.copy(
                        activeOperation = null,
                        downloadPercent = null,
                        message = "已下载：$fileName",
                    )
                }
            }.onFailure { error ->
                finishOperationWithError(error)
            }
        }
    }

    fun loadMoveFolders() {
        if (_uiState.value.moveFoldersLoading || _uiState.value.moveFolders.isNotEmpty()) return
        val node = _uiState.value.currentNode
        viewModelScope.launch {
            _uiState.update { it.copy(moveFoldersLoading = true, message = null) }
            runCatching {
                repository.fetchFolders(args.baseUrl, args.authToken)
                    .filterNot { it.id == node.id }
                    .sortedBy { it.name.lowercase() }
            }.onSuccess { folders ->
                _uiState.update { it.copy(moveFoldersLoading = false, moveFolders = folders) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        moveFoldersLoading = false,
                        message = error.message?.takeIf(String::isNotBlank) ?: "加载文件夹失败。",
                    )
                }
            }
        }
    }

    fun moveTo(parentId: Long?) {
        val node = _uiState.value.currentNode
        if (_uiState.value.activeOperation != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(activeOperation = FileDetailOperation.MOVE, message = null) }
            runCatching {
                repository.moveNodes(
                    baseUrl = args.baseUrl,
                    token = args.authToken,
                    nodeIds = listOf(node.id),
                    parentId = parentId,
                )
            }.onSuccess { movedNodes ->
                val movedNode = movedNodes.firstOrNull()
                _uiState.update {
                    it.copy(
                        currentNode = movedNode ?: it.currentNode,
                        galleryNodes = if (movedNode == null) {
                            it.galleryNodes
                        } else {
                            it.galleryNodes.map { galleryNode ->
                                if (galleryNode.id == movedNode.id) movedNode else galleryNode
                            }
                        },
                        activeOperation = null,
                        contentChanged = true,
                        message = "移动成功。",
                    )
                }
            }.onFailure { error ->
                finishOperationWithError(error)
            }
        }
    }

    fun deleteToTrash() {
        val node = _uiState.value.currentNode
        if (_uiState.value.activeOperation != null || _uiState.value.deleted) return
        viewModelScope.launch {
            _uiState.update { it.copy(activeOperation = FileDetailOperation.DELETE, message = null) }
            runCatching {
                repository.moveNodeToTrash(
                    baseUrl = args.baseUrl,
                    token = args.authToken,
                    nodeId = node.id,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        activeOperation = null,
                        deleted = true,
                        contentChanged = true,
                        message = "已移入回收站。",
                    )
                }
            }.onFailure { error ->
                finishOperationWithError(error)
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun loadPreview() {
        val node = _uiState.value.currentNode
        val generation = ++previewGeneration
        val previewKind = resolveDetailPreviewKind(node)

        if (previewKind == null) {
            _uiState.update {
                if (it.currentNode.id == node.id) {
                    it.copy(
                        loading = false,
                        kind = null,
                        textContent = "",
                        previewUrl = null,
                        error = null,
                    )
                } else {
                    it
                }
            }
            return
        }
        if (previewKind == PreviewKind.TEXT && node.size > MAX_DETAIL_TEXT_PREVIEW_BYTES) {
            _uiState.update {
                if (it.currentNode.id == node.id) {
                    it.copy(
                        loading = false,
                        kind = previewKind,
                        textContent = "",
                        previewUrl = null,
                        error = "文本文件超过 2 MB，请下载后查看完整内容。",
                    )
                } else {
                    it
                }
            }
            return
        }

        if (previewKind == PreviewKind.PDF) {
            _uiState.update {
                if (it.currentNode.id == node.id) {
                    it.copy(
                        loading = false,
                        kind = previewKind,
                        textContent = "",
                        previewUrl = null,
                        error = null,
                    )
                } else {
                    it
                }
            }
            return
        }

        _uiState.update {
            it.copy(
                loading = true,
                kind = previewKind,
                textContent = "",
                previewUrl = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                when (previewKind) {
                    PreviewKind.TEXT -> {
                        val file = repository.downloadFileViaSignedUrl(
                            baseUrl = args.baseUrl,
                            token = args.authToken,
                            fileId = node.id,
                        )
                        decodeDetailText(file.bytes, file.contentType) to null
                    }

                    PreviewKind.IMAGE,
                    PreviewKind.VIDEO,
                    PreviewKind.AUDIO,
                    -> {
                        val access = repository.fetchInlineFileAccessUrl(
                            baseUrl = args.baseUrl,
                            token = args.authToken,
                            fileId = node.id,
                        )
                        "" to access.url
                    }

                    PreviewKind.PDF -> "" to null
                }
            }.onSuccess { (textContent, previewUrl) ->
                _uiState.update {
                    if (generation == previewGeneration && it.currentNode.id == node.id) {
                        it.copy(
                            loading = false,
                            kind = previewKind,
                            textContent = textContent,
                            previewUrl = previewUrl,
                            error = null,
                        )
                    } else {
                        it
                    }
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    if (generation == previewGeneration && state.currentNode.id == node.id) {
                        state.copy(
                            loading = false,
                            error = error.message?.takeIf(String::isNotBlank) ?: "文件预览加载失败。",
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun finishOperationWithError(error: Throwable) {
        _uiState.update {
            it.copy(
                activeOperation = null,
                downloadPercent = null,
                message = error.message?.takeIf(String::isNotBlank) ?: "操作失败，请稍后重试。",
            )
        }
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            args: FileDetailArgs,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FileDetailViewModel(
                    appContext = appContext.applicationContext,
                    args = args,
                    repository = AliciaRepository(),
                ) as T
        }
    }
}

private fun resolveDetailPreviewKind(node: StorageNode): PreviewKind? {
    val mimeType = node.mimeType?.lowercase().orEmpty()
    val extension = node.extension?.lowercase().orEmpty()
    return when {
        mimeType.startsWith("image/") -> PreviewKind.IMAGE
        mimeType == "application/pdf" || extension == "pdf" -> PreviewKind.PDF
        mimeType.startsWith("video/") -> PreviewKind.VIDEO
        mimeType.startsWith("audio/") -> PreviewKind.AUDIO
        mimeType.startsWith("text/") || extension in detailTextExtensions -> PreviewKind.TEXT
        else -> null
    }
}

private fun decodeDetailText(bytes: ByteArray, contentType: String?): String {
    val charsetNames = buildList {
        detectDetailBomCharset(bytes)?.let(::add)
        contentType
            ?.split(';')
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.takeIf(String::isNotBlank)
            ?.let(::add)
        add(StandardCharsets.UTF_8.name())
        add("GB18030")
        add(StandardCharsets.UTF_16LE.name())
        add(StandardCharsets.UTF_16BE.name())
    }.distinct()

    charsetNames.forEach { charsetName ->
        val charset = runCatching { Charset.forName(charsetName) }.getOrNull() ?: return@forEach
        val decoded = runCatching {
            charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull() ?: return@forEach
        return decoded.removePrefix("\uFEFF")
    }
    return bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
}

private fun detectDetailBomCharset(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> StandardCharsets.UTF_8.name()
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> StandardCharsets.UTF_16LE.name()
    bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> StandardCharsets.UTF_16BE.name()
    else -> null
}
