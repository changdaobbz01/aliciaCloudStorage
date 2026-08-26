package com.alicia.cloudstorage.phone.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alicia.cloudstorage.phone.ShareCreateArgs
import com.alicia.cloudstorage.phone.data.AliciaRepository
import com.alicia.cloudstorage.phone.data.StorageNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareCreatedState(
    val title: String,
    val shareUrl: String,
    val password: String?,
    val expiresAt: String?,
    val allowDownload: Boolean,
    val allowSave: Boolean,
    val itemCount: Long,
) {
    fun toShareText(): String = buildString {
        appendLine("我通过 Alicia 云盘分享了：$title")
        appendLine(shareUrl)
        password?.takeIf(String::isNotBlank)?.let { appendLine("提取码：$it") }
        expiresAt?.takeIf(String::isNotBlank)?.let { appendLine("有效期至：$it") }
    }
}

data class ShareCreateUiState(
    val creating: Boolean = false,
    val createdShare: ShareCreatedState? = null,
    val error: String? = null,
    val message: String? = null,
)

class ShareCreateViewModel(
    private val args: ShareCreateArgs,
    private val repository: AliciaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShareCreateUiState())
    val uiState = _uiState.asStateFlow()
    private var creationInFlight = false

    fun createShare(
        title: String,
        password: String?,
        expiresInDays: Int?,
        allowDownload: Boolean,
        allowSave: Boolean,
    ) {
        if (creationInFlight) return
        val selection = validateShareNodes(args.selection.nodes)
        if (!selection.isValid) {
            _uiState.update {
                it.copy(error = selection.errorMessage ?: "请先选择要分享的文件或文件夹。")
            }
            return
        }

        val normalizedTitle = title.trim().ifBlank { args.selection.defaultTitle }
        val normalizedPassword = password?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedPassword != null && normalizedPassword.length !in 4..32) {
            _uiState.update { it.copy(error = "提取码需为 4 到 32 个字符。") }
            return
        }

        creationInFlight = true
        _uiState.update { it.copy(creating = true, createdShare = null, error = null, message = null) }
        viewModelScope.launch {
            runCatching {
                repository.createShareLink(
                    baseUrl = args.baseUrl,
                    token = args.authToken,
                    nodeIds = selection.nodes.map(StorageNode::id),
                    title = normalizedTitle,
                    password = normalizedPassword,
                    expiresInDays = expiresInDays,
                    allowDownload = allowDownload,
                    allowSave = allowSave,
                )
            }.onSuccess { share ->
                _uiState.update {
                    it.copy(
                        creating = false,
                        createdShare = ShareCreatedState(
                            title = share.title.ifBlank { normalizedTitle },
                            shareUrl = "${args.baseUrl.trimEnd('/')}/share/${share.shareCode}",
                            password = normalizedPassword,
                            expiresAt = share.expiresAt,
                            allowDownload = share.allowDownload,
                            allowSave = share.allowSave,
                            itemCount = share.itemCount,
                        ),
                        error = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        creating = false,
                        error = error.message?.takeIf(String::isNotBlank) ?: "创建分享失败，请稍后重试。",
                    )
                }
            }
            creationInFlight = false
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun provideFactory(
            args: ShareCreateArgs,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShareCreateViewModel(
                    args = args,
                    repository = AliciaRepository(),
                ) as T
        }
    }
}
