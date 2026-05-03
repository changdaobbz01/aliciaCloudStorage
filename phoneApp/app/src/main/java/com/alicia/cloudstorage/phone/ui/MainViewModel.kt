package com.alicia.cloudstorage.phone.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alicia.cloudstorage.phone.BuildConfig
import com.alicia.cloudstorage.phone.data.AliciaRepository
import com.alicia.cloudstorage.phone.data.ApiException
import com.alicia.cloudstorage.phone.data.AppTab
import com.alicia.cloudstorage.phone.data.DriveOverview
import com.alicia.cloudstorage.phone.data.FolderCrumb
import com.alicia.cloudstorage.phone.data.SessionStore
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeFilter
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.UsageHistoryPoint
import com.alicia.cloudstorage.phone.data.User
import com.alicia.cloudstorage.phone.data.UserRole
import com.alicia.cloudstorage.phone.data.isAdmin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private const val MAX_TEXT_PREVIEW_BYTES = 2L * 1024 * 1024
private const val MAX_IMAGE_PREVIEW_BYTES = 10L * 1024 * 1024
private const val BYTES_PER_GIB = 1024L * 1024 * 1024

private val PREVIEWABLE_TEXT_EXTENSIONS = setOf(
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

private val defaultBreadCrumbs = listOf(FolderCrumb(id = null, label = "根目录"))

private data class AuthSession(
    val token: String,
    val baseUrl: String,
)

enum class PreviewKind {
    IMAGE,
    TEXT,
}

data class FilePreviewState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val fileName: String = "",
    val kind: PreviewKind? = null,
    val textContent: String = "",
    val imageBytes: ByteArray? = null,
    val error: String? = null,
)

data class HomeUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val overview: DriveOverview? = null,
    val usageHistory: List<UsageHistoryPoint> = emptyList(),
)

data class ExplorerUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<StorageNode> = emptyList(),
    val hasLoadedFolder: Boolean = false,
    val keyword: String = "",
    val filter: StorageNodeFilter = StorageNodeFilter.ALL,
    val currentFolderId: Long? = null,
    val breadcrumbs: List<FolderCrumb> = defaultBreadCrumbs,
    val isUploading: Boolean = false,
    val isCreatingFolder: Boolean = false,
    val actionNodeId: Long? = null,
)

data class TeamUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val users: List<User> = emptyList(),
    val isCreatingUser: Boolean = false,
    val quotaUserId: Long? = null,
    val passwordUserId: Long? = null,
)

data class AppUiState(
    val isBooting: Boolean = true,
    val isSubmittingLogin: Boolean = false,
    val isUpdatingAvatar: Boolean = false,
    val isChangingPassword: Boolean = false,
    val baseUrl: String = BuildConfig.DEFAULT_API_BASE_URL,
    val authToken: String? = null,
    val currentUser: User? = null,
    val selectedTab: AppTab = AppTab.HOME,
    val home: HomeUiState = HomeUiState(),
    val files: ExplorerUiState = ExplorerUiState(),
    val trash: ExplorerUiState = ExplorerUiState(breadcrumbs = emptyList()),
    val team: TeamUiState = TeamUiState(),
    val preview: FilePreviewState = FilePreviewState(),
)

class MainViewModel(
    private val repository: AliciaRepository,
    private val sessionStore: SessionStore,
    private val defaultBaseUrl: String,
    private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState(baseUrl = defaultBaseUrl))
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val fileDirectoryCache = mutableMapOf<Long?, List<StorageNode>>()

    init {
        restoreSession()
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { state -> state.copy(baseUrl = value) }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { state -> state.copy(selectedTab = tab) }

        when (tab) {
            AppTab.HOME -> refreshHomeIfNeeded()
            AppTab.FILES -> refreshFilesIfNeeded()
            AppTab.TRASH -> refreshTrashIfNeeded()
            AppTab.TEAM -> refreshTeamIfNeeded()
            AppTab.ME -> Unit
        }
    }

    fun login(phoneNumber: String, password: String) {
        val normalizedBaseUrl = runCatching { normalizeBaseUrl(uiState.value.baseUrl) }
            .getOrElse { error ->
                emitMessage(error.message ?: "请输入正确的后端地址。")
                return
            }

        if (!phoneNumber.matches(Regex("^1\\d{10}$"))) {
            emitMessage("请输入 11 位手机号。")
            return
        }

        if (password.isBlank()) {
            emitMessage("请输入登录密码。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isSubmittingLogin = true,
                    baseUrl = normalizedBaseUrl,
                )
            }

            runCatching {
                sessionStore.saveBaseUrl(normalizedBaseUrl)
                repository.login(
                    baseUrl = normalizedBaseUrl,
                    phoneNumber = phoneNumber,
                    password = password,
                )
            }.onSuccess { response ->
                sessionStore.saveSession(response.token, normalizedBaseUrl)
                fileDirectoryCache.clear()
                _uiState.update { state ->
                    state.copy(
                        isBooting = false,
                        isSubmittingLogin = false,
                        authToken = response.token,
                        currentUser = response.user,
                        selectedTab = AppTab.HOME,
                        home = HomeUiState(),
                        files = ExplorerUiState(),
                        trash = ExplorerUiState(breadcrumbs = emptyList()),
                        team = TeamUiState(),
                        preview = FilePreviewState(),
                    )
                }

                emitMessage("欢迎回来，${response.user.nickname}")
                refreshAll()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(isBooting = false, isSubmittingLogin = false)
                }
                handleError(error)
            }
        }
    }

    fun refreshCurrentTab() {
        when (uiState.value.selectedTab) {
            AppTab.HOME -> refreshHome(forceLoading = true)
            AppTab.FILES -> refreshFiles(forceLoading = true)
            AppTab.TRASH -> refreshTrash(forceLoading = true)
            AppTab.TEAM -> refreshTeam(forceLoading = true)
            AppTab.ME -> emitMessage("当前账号信息已经是最新展示。")
        }
    }

    fun updateFileKeyword(value: String) {
        _uiState.update { state -> state.copy(files = state.files.copy(keyword = value)) }
    }

    fun updateTrashKeyword(value: String) {
        _uiState.update { state -> state.copy(trash = state.trash.copy(keyword = value)) }
    }

    fun applyFileFilter(filter: StorageNodeFilter) {
        _uiState.update { state -> state.copy(files = state.files.copy(filter = filter)) }
        fileDirectoryCache.clear()
        refreshFiles(forceLoading = true)
    }

    fun applyTrashFilter(filter: StorageNodeFilter) {
        _uiState.update { state -> state.copy(trash = state.trash.copy(filter = filter)) }
        refreshTrash(forceLoading = true)
    }

    fun submitFileSearch() {
        fileDirectoryCache.clear()
        refreshFiles(forceLoading = true)
    }

    fun submitTrashSearch() {
        refreshTrash(forceLoading = true)
    }

    fun createUser(
        phoneNumber: String,
        nickname: String,
        password: String,
        role: UserRole,
        quotaGb: String,
        onSuccess: () -> Unit = {},
    ) {
        val session = authenticatedSession() ?: return
        val trimmedPhoneNumber = phoneNumber.trim()
        val trimmedNickname = nickname.trim()
        val trimmedPassword = password.trim()

        if (!trimmedPhoneNumber.matches(Regex("^1\\d{10}$"))) {
            emitMessage("请输入 11 位手机号。")
            return
        }

        if (trimmedNickname.isBlank()) {
            emitMessage("请输入用户昵称。")
            return
        }

        if (trimmedPassword.isBlank()) {
            emitMessage("请输入初始密码。")
            return
        }

        val storageQuotaBytes = if (role == UserRole.ADMIN) {
            null
        } else {
            parseQuotaGbToBytes(quotaGb) ?: return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(team = state.team.copy(isCreatingUser = true))
            }

            runCatching {
                repository.createUser(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    phoneNumber = trimmedPhoneNumber,
                    nickname = trimmedNickname,
                    password = trimmedPassword,
                    role = role,
                    storageQuotaBytes = storageQuotaBytes,
                )
            }.onSuccess { createdUser ->
                _uiState.update { state ->
                    state.copy(
                        team = state.team.copy(
                            isCreatingUser = false,
                            users = state.team.users + createdUser,
                        ),
                    )
                }
                emitMessage("账号创建成功。")
                onSuccess()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(team = state.team.copy(isCreatingUser = false))
                }
                handleError(error)
            }
        }
    }

    fun updateUserQuota(
        user: User,
        quotaGb: String,
        onSuccess: () -> Unit = {},
    ) {
        val session = authenticatedSession() ?: return
        if (user.isAdmin) {
            emitMessage("管理员账号不限制存储额度。")
            return
        }

        val storageQuotaBytes = parseQuotaGbToBytes(quotaGb) ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(team = state.team.copy(quotaUserId = user.id))
            }

            runCatching {
                repository.updateUserQuota(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    userId = user.id,
                    storageQuotaBytes = storageQuotaBytes,
                )
            }.onSuccess { updatedUser ->
                _uiState.update { state ->
                    state.copy(
                        currentUser = if (state.currentUser?.id == updatedUser.id) {
                            updatedUser
                        } else {
                            state.currentUser
                        },
                        team = state.team.copy(
                            quotaUserId = null,
                            users = state.team.users.map { existing ->
                                if (existing.id == updatedUser.id) updatedUser else existing
                            },
                        ),
                    )
                }
                emitMessage("用户额度已更新。")
                onSuccess()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(team = state.team.copy(quotaUserId = null))
                }
                handleError(error)
            }
        }
    }

    fun resetUserPassword(
        user: User,
        newPassword: String,
        onSuccess: () -> Unit = {},
    ) {
        val session = authenticatedSession() ?: return
        val trimmedNewPassword = newPassword.trim()

        if (trimmedNewPassword.isBlank()) {
            emitMessage("请输入新密码。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(team = state.team.copy(passwordUserId = user.id))
            }

            runCatching {
                repository.resetUserPassword(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    userId = user.id,
                    newPassword = trimmedNewPassword,
                )
            }.onSuccess { response ->
                _uiState.update { state ->
                    state.copy(team = state.team.copy(passwordUserId = null))
                }
                emitMessage(response.message.ifBlank { "用户密码已重置。" })
                onSuccess()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(team = state.team.copy(passwordUserId = null))
                }
                handleError(error)
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isUpdatingAvatar = true) }

            runCatching {
                repository.uploadCurrentUserAvatar(
                    context = appContext,
                    baseUrl = session.baseUrl,
                    token = session.token,
                    uri = uri,
                )
            }.onSuccess { updatedUser ->
                _uiState.update { state ->
                    state.copy(
                        isUpdatingAvatar = false,
                        currentUser = updatedUser,
                    )
                }
                emitMessage("头像已更新。")
            }.onFailure { error ->
                _uiState.update { state -> state.copy(isUpdatingAvatar = false) }
                handleError(error)
            }
        }
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        onSuccess: () -> Unit = {},
    ) {
        val session = authenticatedSession() ?: return
        val trimmedOldPassword = oldPassword.trim()
        val trimmedNewPassword = newPassword.trim()

        if (trimmedOldPassword.isBlank()) {
            emitMessage("请输入当前密码。")
            return
        }

        if (trimmedNewPassword.isBlank()) {
            emitMessage("请输入新密码。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isChangingPassword = true) }

            runCatching {
                repository.changePassword(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    oldPassword = trimmedOldPassword,
                    newPassword = trimmedNewPassword,
                )
            }.onSuccess { response ->
                _uiState.update { state -> state.copy(isChangingPassword = false) }
                emitMessage(response.message.ifBlank { "密码修改成功。" })
                onSuccess()
            }.onFailure { error ->
                _uiState.update { state -> state.copy(isChangingPassword = false) }
                handleError(error)
            }
        }
    }

    fun openNode(node: StorageNode) {
        if (node.type != StorageNodeType.FOLDER) {
            previewFile(node)
            return
        }

        rememberCurrentDirectorySnapshot()
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    currentFolderId = node.id,
                    breadcrumbs = state.files.breadcrumbs + FolderCrumb(id = node.id, label = node.name),
                    items = emptyList(),
                    hasLoadedFolder = false,
                    loading = true,
                    error = null,
                ),
            )
        }
        refreshFiles(forceLoading = false)
    }

    fun jumpToCrumb(index: Int) {
        val current = uiState.value.files.breadcrumbs
        if (index !in current.indices) {
            return
        }

        if (index == current.lastIndex) {
            return
        }

        val target = current[index]
        val cachedItems = fileDirectoryCache[target.id]
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    currentFolderId = target.id,
                    breadcrumbs = current.take(index + 1),
                    items = cachedItems ?: emptyList(),
                    hasLoadedFolder = cachedItems != null,
                    loading = cachedItems == null,
                    error = null,
                ),
            )
        }

        if (cachedItems == null) {
            refreshFiles(forceLoading = false)
        }
    }

    fun createFolder(folderName: String) {
        val session = authenticatedSession() ?: return
        val trimmedName = folderName.trim()

        if (trimmedName.isBlank()) {
            emitMessage("请输入文件夹名称。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(files = state.files.copy(isCreatingFolder = true))
            }

            runCatching {
                repository.createFolder(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    parentId = uiState.value.files.currentFolderId,
                    folderName = trimmedName,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(files = state.files.copy(isCreatingFolder = false))
                }
                emitMessage("已创建文件夹：$trimmedName")
                refreshAfterMutation(refreshFiles = true, refreshTrash = false)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(isCreatingFolder = false))
                }
                handleError(error)
            }
        }
    }

    fun uploadDocument(uri: Uri) {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(files = state.files.copy(isUploading = true))
            }

            runCatching {
                repository.uploadFile(
                    context = appContext,
                    baseUrl = session.baseUrl,
                    token = session.token,
                    parentId = uiState.value.files.currentFolderId,
                    uri = uri,
                )
            }.onSuccess { node ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(isUploading = false))
                }
                emitMessage("上传完成：${node.name}")
                refreshAfterMutation(refreshFiles = true, refreshTrash = false)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(isUploading = false))
                }
                handleError(error)
            }
        }
    }

    fun moveNodeToTrash(node: StorageNode) {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(files = state.files.copy(actionNodeId = node.id))
            }

            runCatching {
                repository.moveNodeToTrash(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeId = node.id,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(files = state.files.copy(actionNodeId = null))
                }
                emitMessage("已移入回收站：${node.name}")
                refreshAfterMutation(refreshFiles = true, refreshTrash = true)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(actionNodeId = null))
                }
                handleError(error)
            }
        }
    }

    fun restoreNode(node: StorageNode) {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(trash = state.trash.copy(actionNodeId = node.id))
            }

            runCatching {
                repository.restoreNode(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeId = node.id,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(actionNodeId = null))
                }
                emitMessage("已恢复：${node.name}")
                refreshAfterMutation(refreshFiles = true, refreshTrash = true)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(actionNodeId = null))
                }
                handleError(error)
            }
        }
    }

    fun permanentlyDeleteNode(node: StorageNode) {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(trash = state.trash.copy(actionNodeId = node.id))
            }

            runCatching {
                repository.permanentlyDeleteNode(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeId = node.id,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(actionNodeId = null))
                }
                emitMessage("已彻底删除：${node.name}")
                refreshAfterMutation(refreshFiles = false, refreshTrash = true)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(actionNodeId = null))
                }
                handleError(error)
            }
        }
    }

    fun previewFile(node: StorageNode) {
        val session = authenticatedSession() ?: return
        val previewKind = resolvePreviewKind(node)

        if (previewKind == null) {
            emitMessage("当前文件类型暂不支持内置预览，请先下载。")
            return
        }

        if (previewKind == PreviewKind.TEXT && node.size > MAX_TEXT_PREVIEW_BYTES) {
            emitMessage("文本文件超过 2 MB，先下载查看会更稳。")
            return
        }

        if (previewKind == PreviewKind.IMAGE && node.size > MAX_IMAGE_PREVIEW_BYTES) {
            emitMessage("图片超过 10 MB，先下载查看会更稳。")
            return
        }

        _uiState.update { state ->
            state.copy(
                preview = FilePreviewState(
                    visible = true,
                    loading = true,
                    fileName = node.name,
                    kind = previewKind,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                repository.downloadFile(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    fileId = node.id,
                )
            }.onSuccess { file ->
                _uiState.update { state ->
                    state.copy(
                        preview = when (previewKind) {
                            PreviewKind.TEXT -> FilePreviewState(
                                visible = true,
                                loading = false,
                                fileName = file.fileName,
                                kind = previewKind,
                                textContent = file.bytes.toString(Charsets.UTF_8),
                            )

                            PreviewKind.IMAGE -> FilePreviewState(
                                visible = true,
                                loading = false,
                                fileName = file.fileName,
                                kind = previewKind,
                                imageBytes = file.bytes,
                            )
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        preview = FilePreviewState(
                            visible = true,
                            loading = false,
                            fileName = node.name,
                            kind = previewKind,
                            error = error.readableMessage(),
                        ),
                    )
                }
                handleError(error, emitUserMessage = false)
            }
        }
    }

    fun closePreview() {
        _uiState.update { state -> state.copy(preview = FilePreviewState()) }
    }

    fun downloadFileToUri(node: StorageNode, destinationUri: Uri) {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(files = state.files.copy(actionNodeId = node.id))
            }

            runCatching {
                repository.saveDownloadedFileToUri(
                    context = appContext,
                    baseUrl = session.baseUrl,
                    token = session.token,
                    fileId = node.id,
                    destinationUri = destinationUri,
                )
            }.onSuccess { fileName ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(actionNodeId = null))
                }
                emitMessage("已保存：$fileName")
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(actionNodeId = null))
                }
                handleError(error)
            }
        }
    }

    fun logout() {
        val baseUrl = uiState.value.baseUrl
        viewModelScope.launch {
            sessionStore.clearToken(baseUrl)
            fileDirectoryCache.clear()
            _uiState.value = AppUiState(
                isBooting = false,
                baseUrl = baseUrl,
            )
            emitMessage("你已退出登录。")
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val session = sessionStore.sessionFlow(defaultBaseUrl).first()
            _uiState.update { state -> state.copy(baseUrl = session.baseUrl) }

            if (session.token.isNullOrBlank()) {
                _uiState.update { state -> state.copy(isBooting = false) }
                return@launch
            }

            runCatching {
                val currentUser = repository.fetchCurrentUser(session.baseUrl, session.token)
                session to currentUser
            }.onSuccess { (savedSession, currentUser) ->
                fileDirectoryCache.clear()
                _uiState.update { state ->
                    state.copy(
                        isBooting = false,
                        authToken = savedSession.token,
                        currentUser = currentUser,
                        baseUrl = savedSession.baseUrl,
                    )
                }
                refreshAll()
            }.onFailure { error ->
                sessionStore.clearToken(session.baseUrl)
                _uiState.update { state ->
                    state.copy(isBooting = false, authToken = null, currentUser = null)
                }
                handleError(error)
            }
        }
    }

    private fun refreshAll() {
        syncCurrentUser()
        refreshHome(forceLoading = false)
        refreshFiles(forceLoading = false)
        refreshTrash(forceLoading = false)
        refreshTeam(forceLoading = false)
    }

    private fun refreshAfterMutation(refreshFiles: Boolean, refreshTrash: Boolean) {
        fileDirectoryCache.clear()
        syncCurrentUser()
        refreshHome(forceLoading = false)
        if (refreshFiles) {
            refreshFiles(forceLoading = true)
        }
        if (refreshTrash) {
            refreshTrash(forceLoading = true)
        }
        refreshTeam(forceLoading = false)
    }

    private fun refreshHomeIfNeeded() {
        if (uiState.value.home.overview == null && !uiState.value.home.loading) {
            refreshHome(forceLoading = true)
        }
    }

    private fun refreshFilesIfNeeded() {
        if (!uiState.value.files.hasLoadedFolder && !uiState.value.files.loading) {
            refreshFiles(forceLoading = true)
        }
    }

    private fun refreshTrashIfNeeded() {
        if (!uiState.value.trash.hasLoadedFolder && !uiState.value.trash.loading) {
            refreshTrash(forceLoading = true)
        }
    }

    private fun refreshTeamIfNeeded() {
        val currentUser = uiState.value.currentUser ?: return
        if (!currentUser.isAdmin || uiState.value.team.loading) {
            return
        }

        if (uiState.value.team.users.isEmpty()) {
            refreshTeam(forceLoading = true)
        }
    }

    private fun syncCurrentUser() {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            runCatching {
                repository.fetchCurrentUser(session.baseUrl, session.token)
            }.onSuccess { currentUser ->
                _uiState.update { state -> state.copy(currentUser = currentUser) }
            }.onFailure { error ->
                handleError(error, emitUserMessage = false)
            }
        }
    }

    private fun refreshHome(forceLoading: Boolean) {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { state ->
                    state.copy(home = state.home.copy(loading = true, error = null))
                }
            }

            runCatching {
                val overview = repository.fetchDriveOverview(session.baseUrl, session.token)
                val history = repository.fetchUsageHistory(session.baseUrl, session.token)
                overview to history
            }.onSuccess { (overview, history) ->
                _uiState.update { state ->
                    state.copy(
                        home = state.home.copy(
                            loading = false,
                            error = null,
                            overview = overview,
                            usageHistory = history,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(home = state.home.copy(loading = false, error = error.readableMessage()))
                }
                handleError(error, emitUserMessage = false)
            }
        }
    }

    private fun refreshFiles(forceLoading: Boolean) {
        val session = authenticatedSession() ?: return
        val files = uiState.value.files

        viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { state ->
                    state.copy(files = state.files.copy(loading = true, error = null))
                }
            }

            runCatching {
                repository.fetchStorageNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    parentId = files.currentFolderId,
                    keyword = files.keyword,
                    filter = files.filter,
                )
            }.onSuccess { page ->
                fileDirectoryCache[files.currentFolderId] = page.items
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            loading = false,
                            error = null,
                            hasLoadedFolder = true,
                            items = page.items,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(loading = false, error = error.readableMessage()))
                }
                handleError(error, emitUserMessage = false)
            }
        }
    }

    private fun refreshTrash(forceLoading: Boolean) {
        val session = authenticatedSession() ?: return
        val trash = uiState.value.trash

        viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(loading = true, error = null))
                }
            }

            runCatching {
                repository.fetchTrashNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    keyword = trash.keyword,
                    filter = trash.filter,
                )
            }.onSuccess { page ->
                _uiState.update { state ->
                    state.copy(
                        trash = state.trash.copy(
                            loading = false,
                            error = null,
                            hasLoadedFolder = true,
                            items = page.items,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(loading = false, error = error.readableMessage()))
                }
                handleError(error, emitUserMessage = false)
            }
        }
    }

    private fun refreshTeam(forceLoading: Boolean) {
        val session = authenticatedSession() ?: return
        val currentUser = uiState.value.currentUser ?: return
        if (!currentUser.isAdmin) {
            return
        }

        viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { state ->
                    state.copy(team = state.team.copy(loading = true, error = null))
                }
            }

            runCatching {
                repository.fetchUsers(session.baseUrl, session.token)
            }.onSuccess { users ->
                _uiState.update { state ->
                    state.copy(team = state.team.copy(loading = false, error = null, users = users))
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(team = state.team.copy(loading = false, error = error.readableMessage()))
                }
                handleError(error, emitUserMessage = false)
            }
        }
    }

    private fun resolvePreviewKind(node: StorageNode): PreviewKind? {
        val mimeType = node.mimeType?.lowercase().orEmpty()
        val extension = node.extension?.lowercase().orEmpty()

        return when {
            mimeType.startsWith("image/") -> PreviewKind.IMAGE
            mimeType.startsWith("text/") -> PreviewKind.TEXT
            extension in PREVIEWABLE_TEXT_EXTENSIONS -> PreviewKind.TEXT
            else -> null
        }
    }

    private fun authenticatedSession(): AuthSession? {
        val state = uiState.value
        val token = state.authToken ?: return null
        return AuthSession(token = token, baseUrl = state.baseUrl)
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "请输入后端地址。" }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        return withScheme.removeSuffix("/")
    }

    private fun rememberCurrentDirectorySnapshot() {
        val files = uiState.value.files
        fileDirectoryCache[files.currentFolderId] = files.items
    }

    private fun parseQuotaGbToBytes(value: String): Long? {
        val quotaGb = value.trim().toDoubleOrNull()
        if (quotaGb == null || !quotaGb.isFinite() || quotaGb <= 0) {
            emitMessage("请输入大于 0 的 GB 额度，例如 0.5。")
            return null
        }

        return (quotaGb * BYTES_PER_GIB.toDouble()).roundToLong()
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    private fun handleError(error: Throwable, emitUserMessage: Boolean = true) {
        val message = error.readableMessage()

        if (error is ApiException && error.status == 401) {
            logout()
            if (emitUserMessage) {
                emitMessage("登录状态已过期，请重新登录。")
            }
            return
        }

        if (emitUserMessage) {
            emitMessage(message)
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        repository = AliciaRepository(),
                        sessionStore = SessionStore(context.applicationContext),
                        defaultBaseUrl = BuildConfig.DEFAULT_API_BASE_URL,
                        appContext = context.applicationContext,
                    ) as T
                }
            }
    }
}

private fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后再试。"
