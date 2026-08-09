package com.alicia.cloudstorage.phone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alicia.cloudstorage.phone.BuildConfig
import com.alicia.cloudstorage.phone.ShareLinkParser
import com.alicia.cloudstorage.phone.describeAccessEnvironment
import com.alicia.cloudstorage.phone.normalizeConfiguredBaseUrl
import com.alicia.cloudstorage.phone.data.AliciaRepository
import com.alicia.cloudstorage.phone.data.ApiException
import com.alicia.cloudstorage.phone.data.AppPackageVersionInfo
import com.alicia.cloudstorage.phone.data.AppTab
import com.alicia.cloudstorage.phone.data.DriveOverview
import com.alicia.cloudstorage.phone.data.FolderCrumb
import com.alicia.cloudstorage.phone.data.SessionStore
import com.alicia.cloudstorage.phone.data.ShareLinkDetailResponse
import com.alicia.cloudstorage.phone.data.ShareLinkStatusResponse
import com.alicia.cloudstorage.phone.data.StorageFileCategory
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeFilter
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.TransferProgress
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.net.URI
import kotlin.math.roundToLong

private const val MAX_TEXT_PREVIEW_BYTES = 2L * 1024 * 1024
private const val BYTES_PER_GIB = 1024L * 1024 * 1024
private const val MAX_TRANSFER_HISTORY = 50

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
    PDF,
    VIDEO,
    AUDIO,
}

enum class FileSearchScope {
    CURRENT_FOLDER,
    GLOBAL,
}

enum class TransferPanelTab {
    DOWNLOADS,
    UPLOADS,
}

enum class TransferKind {
    DOWNLOAD,
    UPLOAD,
}

enum class TransferItemKind {
    FILE,
    ARCHIVE,
}

enum class TransferStatus {
    QUEUED,
    PREPARING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
}

data class TransferTask(
    val id: Long,
    val kind: TransferKind,
    val itemKind: TransferItemKind,
    val title: String,
    val status: TransferStatus,
    val sourceNodeIds: List<Long> = emptyList(),
    val sourceUri: Uri? = null,
    val destinationUri: Uri? = null,
    val transferredBytes: Long = 0L,
    val totalBytes: Long? = null,
    val progressPercent: Int? = null,
    val locationLabel: String? = null,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class FilePreviewState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val fileName: String = "",
    val kind: PreviewKind? = null,
    val textContent: String = "",
    val previewUrl: String? = null,
    val localFilePath: String? = null,
    val error: String? = null,
)

data class HomeUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val overview: DriveOverview? = null,
    val usageHistory: List<UsageHistoryPoint> = emptyList(),
    val recentNodes: List<StorageNode> = emptyList(),
)

data class ExplorerUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<StorageNode> = emptyList(),
    val hasLoadedFolder: Boolean = false,
    val keyword: String = "",
    val searchScope: FileSearchScope = FileSearchScope.CURRENT_FOLDER,
    val filter: StorageNodeFilter = StorageNodeFilter.ALL,
    val category: StorageFileCategory? = null,
    val currentFolderId: Long? = null,
    val breadcrumbs: List<FolderCrumb> = defaultBreadCrumbs,
    val isUploading: Boolean = false,
    val isCreatingFolder: Boolean = false,
    val actionNodeId: Long? = null,
    val selectedNodeIds: Set<Long> = emptySet(),
    val highlightedNodeId: Long? = null,
    val isBatchActing: Boolean = false,
    val moveTargetFolders: List<StorageNode> = emptyList(),
    val moveTargetLoading: Boolean = false,
)

data class TeamUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val users: List<User> = emptyList(),
    val isCreatingUser: Boolean = false,
    val quotaUserId: Long? = null,
    val passwordUserId: Long? = null,
)

data class AppUpdateState(
    val currentVersionName: String,
    val latestVersionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

enum class IncomingShareSource {
    CLIPBOARD,
    DEEP_LINK,
}

data class IncomingSharePromptState(
    val shareCode: String,
    val clipboardFingerprint: String? = null,
)

data class IncomingShareUiState(
    val prompt: IncomingSharePromptState? = null,
    val activeShareCode: String? = null,
    val clipboardFingerprint: String? = null,
    val source: IncomingShareSource? = null,
    val statusLoading: Boolean = false,
    val detailLoading: Boolean = false,
    val passwordChecking: Boolean = false,
    val saving: Boolean = false,
    val expandedFolderIds: Set<Long> = emptySet(),
    val selectedNodeIds: Set<Long> = emptySet(),
    val saveTargetPickerOpen: Boolean = false,
    val saveTargetFolders: List<StorageNode> = emptyList(),
    val saveTargetLoading: Boolean = false,
    val saveTargetParentId: Long? = null,
    val loginPromptDismissed: Boolean = false,
    val status: ShareLinkStatusResponse? = null,
    val detail: ShareLinkDetailResponse? = null,
    val shareAccessToken: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
)

data class AppUiState(
    val isBooting: Boolean = true,
    val isSubmittingLogin: Boolean = false,
    val isUpdatingProfile: Boolean = false,
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
    val appUpdate: AppUpdateState? = null,
    val incomingShare: IncomingShareUiState = IncomingShareUiState(),
    val transfers: List<TransferTask> = emptyList(),
    val transferPanelOpen: Boolean = false,
    val transferPanelTab: TransferPanelTab = TransferPanelTab.DOWNLOADS,
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
    private var dismissedUpdateVersionName: String? = null
    private var currentPreviewCacheFile: File? = null
    private var lastDismissedClipboardShareCode: String? = null
    private var lastDismissedClipboardFingerprint: String? = null
    private var lastDismissedClipboardAtMillis: Long = 0L
    private var nextTransferId = 1L
    private val transferJobs = mutableMapOf<Long, Job>()

    init {
        restoreSession()
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { state -> state.copy(baseUrl = value) }
    }

    fun switchBaseUrl(targetBaseUrl: String) {
        val normalizedBaseUrl = runCatching { normalizeBaseUrl(targetBaseUrl) }
            .getOrElse { error ->
                emitMessage(error.message ?: "请输入正确的后端地址。")
                return
            }

        if (normalizeConfiguredBaseUrl(uiState.value.baseUrl) == normalizedBaseUrl) {
            emitMessage("当前已接入${describeAccessEnvironment(normalizedBaseUrl)}。")
            return
        }

        viewModelScope.launch {
            transferJobs.values.forEach { job -> job.cancel() }
            transferJobs.clear()
            sessionStore.clearToken(normalizedBaseUrl)
            fileDirectoryCache.clear()
            clearPreviewArtifacts()
            dismissedUpdateVersionName = null
            _uiState.value = AppUiState(
                isBooting = false,
                baseUrl = normalizedBaseUrl,
            )
            checkForAppUpdate(normalizedBaseUrl)
            emitMessage("已切换到${describeAccessEnvironment(normalizedBaseUrl)}，请重新登录。")
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { state -> state.copy(selectedTab = tab) }

        when (tab) {
            AppTab.HOME -> refreshHomeIfNeeded()
            AppTab.FILES -> refreshFilesIfNeeded()
            AppTab.TRASH -> refreshTrashIfNeeded()
            AppTab.TRANSFERS -> Unit
            AppTab.TEAM -> refreshTeamIfNeeded()
            AppTab.ME -> {
                syncCurrentUser()
                refreshTeamIfNeeded()
            }
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
                clearPreviewArtifacts()
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
                        transfers = emptyList(),
                        transferPanelOpen = false,
                        transferPanelTab = TransferPanelTab.DOWNLOADS,
                    )
                }

                emitMessage("欢迎回来，${response.user.nickname}")
                refreshAll()
                checkForAppUpdate(normalizedBaseUrl)
                refreshIncomingShareDetailIfReady()
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
            AppTab.TRANSFERS -> Unit
            AppTab.TEAM -> refreshTeam(forceLoading = true)
            AppTab.ME -> {
                syncCurrentUser()
                val currentUser = uiState.value.currentUser
                if (currentUser?.isAdmin == true) {
                    refreshTeam(forceLoading = true)
                } else {
                    emitMessage("当前账号信息已经是最新展示。")
                }
            }
        }
    }

    fun refreshAfterFileDetailMutation() {
        refreshAfterMutation(refreshFiles = true, refreshTrash = true)
    }

    fun openTransferPanel(tab: TransferPanelTab = TransferPanelTab.DOWNLOADS) {
        _uiState.update { state ->
            state.copy(
                selectedTab = AppTab.TRANSFERS,
                transferPanelOpen = false,
                transferPanelTab = tab,
            )
        }
    }

    fun closeTransferPanel() {
        _uiState.update { state -> state.copy(transferPanelOpen = false) }
    }

    fun selectTransferPanelTab(tab: TransferPanelTab) {
        _uiState.update { state -> state.copy(transferPanelTab = tab) }
    }

    fun clearFinishedTransfers() {
        _uiState.update { state ->
            state.copy(
                transfers = state.transfers.filter { task ->
                    task.status == TransferStatus.QUEUED ||
                        task.status == TransferStatus.PREPARING ||
                        task.status == TransferStatus.RUNNING ||
                        (task.kind == TransferKind.DOWNLOAD && task.status == TransferStatus.FAILED)
                },
            )
        }
    }

    fun cancelTransfer(taskId: Long) {
        transferJobs[taskId]?.cancel()
        updateTransfer(taskId) { task ->
            task.copy(
                status = TransferStatus.CANCELED,
                errorMessage = null,
            )
        }
    }

    fun updateFileKeyword(value: String) {
        _uiState.update { state ->
            state.copy(files = state.files.copy(keyword = value, highlightedNodeId = null))
        }
    }

    fun updateTrashKeyword(value: String) {
        _uiState.update { state -> state.copy(trash = state.trash.copy(keyword = value)) }
    }

    fun applyFileFilter(filter: StorageNodeFilter) {
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    filter = filter,
                    category = null,
                    searchScope = FileSearchScope.CURRENT_FOLDER,
                    highlightedNodeId = null,
                ),
            )
        }
        fileDirectoryCache.clear()
        refreshFiles(forceLoading = true)
    }

    fun applyFileCategory(category: StorageFileCategory?) {
        _uiState.update { state ->
            state.copy(
                selectedTab = AppTab.FILES,
                files = state.files.copy(
                    category = category,
                    filter = if (category == null) StorageNodeFilter.ALL else StorageNodeFilter.FILE,
                    currentFolderId = null,
                    breadcrumbs = defaultBreadCrumbs,
                    searchScope = if (category == null) FileSearchScope.CURRENT_FOLDER else FileSearchScope.GLOBAL,
                    selectedNodeIds = emptySet(),
                    highlightedNodeId = null,
                ),
            )
        }
        fileDirectoryCache.clear()
        refreshFiles(forceLoading = true)
    }

    fun applyTrashFilter(filter: StorageNodeFilter) {
        _uiState.update { state -> state.copy(trash = state.trash.copy(filter = filter)) }
        refreshTrash(forceLoading = true)
    }

    fun submitFileSearch() {
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    highlightedNodeId = null,
                    searchScope = if (state.files.category == null) {
                        FileSearchScope.CURRENT_FOLDER
                    } else {
                        FileSearchScope.GLOBAL
                    },
                ),
            )
        }
        fileDirectoryCache.clear()
        refreshFiles(forceLoading = true)
    }

    fun submitHomeFileSearch() {
        val rootItems = fileDirectoryCache[null]
        val normalizedKeyword = uiState.value.files.keyword.trim()
        val isGlobalSearch = normalizedKeyword.isNotEmpty()
        _uiState.update { state ->
            state.copy(
                selectedTab = AppTab.FILES,
                files = state.files.copy(
                    currentFolderId = null,
                    breadcrumbs = defaultBreadCrumbs,
                    items = if (isGlobalSearch) emptyList() else rootItems ?: emptyList(),
                    hasLoadedFolder = !isGlobalSearch && rootItems != null,
                    loading = isGlobalSearch || rootItems == null,
                    error = null,
                    selectedNodeIds = emptySet(),
                    highlightedNodeId = null,
                    category = null,
                    searchScope = if (isGlobalSearch) {
                        FileSearchScope.GLOBAL
                    } else {
                        FileSearchScope.CURRENT_FOLDER
                    },
                ),
            )
        }
        refreshFiles(forceLoading = false)
    }

    fun submitTrashSearch() {
        refreshTrash(forceLoading = true)
    }

    fun toggleNodeSelection(isTrashMode: Boolean, nodeId: Long) {
        updateExplorerState(isTrashMode) { explorer ->
            val nextSelection = explorer.selectedNodeIds.toMutableSet().apply {
                if (!add(nodeId)) {
                    remove(nodeId)
                }
            }
            explorer.copy(
                selectedNodeIds = nextSelection,
                highlightedNodeId = if (isTrashMode) explorer.highlightedNodeId else null,
            )
        }
    }

    fun clearNodeSelection(isTrashMode: Boolean) {
        updateExplorerState(isTrashMode) { explorer ->
            explorer.copy(
                selectedNodeIds = emptySet(),
                highlightedNodeId = if (isTrashMode) explorer.highlightedNodeId else null,
            )
        }
    }

    fun selectAllVisibleNodes(isTrashMode: Boolean) {
        updateExplorerState(isTrashMode) { explorer ->
            explorer.copy(
                selectedNodeIds = explorer.items.mapTo(linkedSetOf()) { it.id },
                highlightedNodeId = if (isTrashMode) explorer.highlightedNodeId else null,
            )
        }
    }

    fun revealNodeInFiles(node: StorageNode) {
        val currentFiles = uiState.value.files
        val targetFolderId = node.parentId
        val sameFolder = currentFiles.currentFolderId == targetFolderId
        if (!sameFolder) {
            rememberCurrentDirectorySnapshot()
        }

        val cachedItems = if (sameFolder) currentFiles.items else fileDirectoryCache[targetFolderId]
        _uiState.update { state ->
            val nextBreadcrumbs = resolveRevealBreadcrumbs(
                current = state.files.breadcrumbs,
                targetFolderId = targetFolderId,
            )
            state.copy(
                selectedTab = AppTab.FILES,
                files = state.files.copy(
                    currentFolderId = targetFolderId,
                    breadcrumbs = nextBreadcrumbs,
                    items = cachedItems ?: emptyList(),
                    hasLoadedFolder = cachedItems != null,
                    loading = cachedItems == null,
                    error = null,
                    selectedNodeIds = emptySet(),
                    highlightedNodeId = node.id,
                    category = null,
                    searchScope = FileSearchScope.CURRENT_FOLDER,
                ),
            )
        }

        if (!sameFolder) {
            refreshFiles(forceLoading = false)
        }
    }

    fun loadMoveTargets() {
        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    files = state.files.copy(
                        moveTargetLoading = true,
                    ),
                )
            }

            runCatching {
                repository.fetchFolders(session.baseUrl, session.token)
                    .sortedBy { it.name.lowercase() }
            }.onSuccess { folders ->
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            moveTargetLoading = false,
                            moveTargetFolders = folders,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(moveTargetLoading = false))
                }
                handleError(error)
            }
        }
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

    fun updateNickname(
        nickname: String,
        onSuccess: () -> Unit = {},
    ) {
        val session = authenticatedSession() ?: return
        val currentUser = uiState.value.currentUser ?: return
        val trimmedNickname = nickname.trim()

        if (trimmedNickname.isBlank()) {
            emitMessage("请输入昵称。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isUpdatingProfile = true) }

            runCatching {
                repository.updateProfile(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    phoneNumber = currentUser.phoneNumber,
                    nickname = trimmedNickname,
                    avatarUrl = currentUser.avatarUrl,
                )
            }.onSuccess { updatedUser ->
                _uiState.update { state ->
                    state.copy(
                        isUpdatingProfile = false,
                        currentUser = updatedUser,
                        team = state.team.copy(
                            users = state.team.users.map { user ->
                                if (user.id == updatedUser.id) updatedUser else user
                            },
                        ),
                    )
                }
                emitMessage("昵称已更新。")
                onSuccess()
            }.onFailure { error ->
                _uiState.update { state -> state.copy(isUpdatingProfile = false) }
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
                    selectedNodeIds = emptySet(),
                    highlightedNodeId = null,
                    category = null,
                    searchScope = FileSearchScope.CURRENT_FOLDER,
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
                    selectedNodeIds = emptySet(),
                    highlightedNodeId = null,
                    category = null,
                    searchScope = FileSearchScope.CURRENT_FOLDER,
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
        uploadDocuments(listOf(uri))
    }

    fun uploadDocuments(uris: List<Uri>) {
        val session = authenticatedSession() ?: return
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    files = state.files.copy(isUploading = true),
                    transferPanelOpen = false,
                    transferPanelTab = TransferPanelTab.UPLOADS,
                )
            }

            val parentId = uiState.value.files.currentFolderId
            var successCount = 0
            var firstError: Throwable? = null

            uniqueUris.forEach { uri ->
                val descriptor = runCatching {
                    repository.describeUploadAsset(appContext, uri)
                }.getOrNull()
                val taskId = appendTransfer(
                    TransferTask(
                        id = allocateTransferId(),
                        kind = TransferKind.UPLOAD,
                        itemKind = TransferItemKind.FILE,
                        title = descriptor?.fileName ?: uri.lastPathSegment ?: "upload.bin",
                        status = TransferStatus.PREPARING,
                        sourceUri = uri,
                        totalBytes = descriptor?.sizeBytes,
                        locationLabel = resolveUploadLocationLabel(parentId),
                    ),
                )

                val taskJob = launch {
                    runCatching {
                        repository.uploadFile(
                            context = appContext,
                            baseUrl = session.baseUrl,
                            token = session.token,
                            parentId = parentId,
                            uri = uri,
                            onProgress = { progress ->
                                updateTransferProgress(taskId, progress)
                            },
                        )
                    }.onSuccess {
                        successCount += 1
                        updateTransfer(taskId) { task ->
                            task.copy(
                                status = TransferStatus.COMPLETED,
                                transferredBytes = task.totalBytes ?: task.transferredBytes,
                                progressPercent = 100,
                                errorMessage = null,
                            )
                        }
                    }.onFailure { error ->
                        if (error is CancellationException) {
                            updateTransfer(taskId) { task ->
                                task.copy(status = TransferStatus.CANCELED)
                            }
                        } else {
                            if (firstError == null) {
                                firstError = error
                            }
                            updateTransfer(taskId) { task ->
                                task.copy(
                                    status = TransferStatus.FAILED,
                                    errorMessage = error.readableMessage(),
                                )
                            }
                        }
                    }.also {
                        transferJobs.remove(taskId)
                    }
                }

                transferJobs[taskId] = taskJob
                taskJob.invokeOnCompletion { transferJobs.remove(taskId) }
                taskJob.join()
            }

            _uiState.update { state ->
                state.copy(files = state.files.copy(isUploading = false))
            }

            when {
                successCount == uniqueUris.size -> {
                    emitMessage(if (successCount == 1) "上传完成。" else "已上传 $successCount 个文件。")
                    refreshAfterMutation(refreshFiles = true, refreshTrash = false)
                }

                successCount > 0 -> {
                    emitMessage("已上传 $successCount 个文件，${uniqueUris.size - successCount} 个失败。")
                    firstError?.let { handleError(it) }
                    refreshAfterMutation(refreshFiles = true, refreshTrash = false)
                }

                else -> {
                    handleError(firstError ?: ApiException("上传失败。", 400))
                }
            }
        }
    }

    fun moveSelectedNodes(parentId: Long?, onSuccess: () -> Unit = {}) {
        val session = authenticatedSession() ?: return
        val selectedIds = uiState.value.files.selectedNodeIds.toList()

        if (selectedIds.isEmpty()) {
            emitMessage("请先选择要移动的文件。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(files = state.files.copy(isBatchActing = true))
            }

            runCatching {
                repository.moveNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeIds = selectedIds,
                    parentId = parentId,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            isBatchActing = false,
                            selectedNodeIds = emptySet(),
                        ),
                    )
                }
                emitMessage(if (selectedIds.size == 1) "移动成功。" else "已移动 ${selectedIds.size} 项。")
                onSuccess()
                refreshAfterMutation(refreshFiles = true, refreshTrash = false)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(isBatchActing = false))
                }
                handleError(error)
            }
        }
    }

    fun moveSelectedNodesToTrash() {
        val session = authenticatedSession() ?: return
        val selectedIds = uiState.value.files.selectedNodeIds.toList()

        if (selectedIds.isEmpty()) {
            emitMessage("请先选择要删除的文件。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(files = state.files.copy(isBatchActing = true))
            }

            runCatching {
                repository.moveNodesToTrash(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeIds = selectedIds,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            isBatchActing = false,
                            selectedNodeIds = emptySet(),
                        ),
                    )
                }
                emitMessage(if (selectedIds.size == 1) "已移入回收站。" else "已移入回收站 ${selectedIds.size} 项。")
                refreshAfterMutation(refreshFiles = true, refreshTrash = true)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(isBatchActing = false))
                }
                handleError(error)
            }
        }
    }

    fun restoreSelectedNodes() {
        val session = authenticatedSession() ?: return
        val selectedIds = uiState.value.trash.selectedNodeIds.toList()

        if (selectedIds.isEmpty()) {
            emitMessage("请先选择要恢复的文件。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(trash = state.trash.copy(isBatchActing = true))
            }

            runCatching {
                repository.restoreNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeIds = selectedIds,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        trash = state.trash.copy(
                            isBatchActing = false,
                            selectedNodeIds = emptySet(),
                        ),
                    )
                }
                emitMessage(if (selectedIds.size == 1) "恢复成功。" else "已恢复 ${selectedIds.size} 项。")
                refreshAfterMutation(refreshFiles = true, refreshTrash = true)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(isBatchActing = false))
                }
                handleError(error)
            }
        }
    }

    fun permanentlyDeleteSelectedNodes() {
        val session = authenticatedSession() ?: return
        val selectedIds = uiState.value.trash.selectedNodeIds.toList()

        if (selectedIds.isEmpty()) {
            emitMessage("请先选择要彻底删除的文件。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(trash = state.trash.copy(isBatchActing = true))
            }

            runCatching {
                repository.permanentlyDeleteNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeIds = selectedIds,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        trash = state.trash.copy(
                            isBatchActing = false,
                            selectedNodeIds = emptySet(),
                        ),
                    )
                }
                emitMessage(if (selectedIds.size == 1) "已彻底删除。" else "已彻底删除 ${selectedIds.size} 项。")
                refreshAfterMutation(refreshFiles = false, refreshTrash = true)
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(isBatchActing = false))
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

    fun handleIncomingShareUri(uri: Uri?) {
        val shareCode = ShareLinkParser.shareCodeFromUri(uri, uiState.value.baseUrl) ?: return
        openIncomingShare(shareCode, IncomingShareSource.DEEP_LINK)
    }

    fun checkClipboardForShareLink() {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
        if (clip.itemCount <= 0) {
            return
        }

        val clipboardTexts = (0 until clip.itemCount)
            .asSequence()
            .flatMap { index ->
                runCatching {
                    clipboardItemTexts(clip.getItemAt(index)).asSequence()
                }.getOrElse { emptySequence() }
            }
            .distinct()
            .toList()

        val shareCode = clipboardTexts
            .asSequence()
            .mapNotNull { text -> ShareLinkParser.findShareCodeInText(text, uiState.value.baseUrl) }
            .firstOrNull()
            ?: return
        val clipboardFingerprint = clipboardFingerprint(clip, clipboardTexts)
        val incomingShare = uiState.value.incomingShare
        val dismissedRecently = lastDismissedClipboardShareCode == shareCode &&
            lastDismissedClipboardFingerprint == clipboardFingerprint &&
            System.currentTimeMillis() - lastDismissedClipboardAtMillis < 30_000L

        if (
            incomingShare.activeShareCode == shareCode ||
            incomingShare.prompt?.shareCode == shareCode ||
            dismissedRecently
        ) {
            return
        }

        _uiState.update { state ->
            state.copy(
                incomingShare = state.incomingShare.copy(
                    prompt = IncomingSharePromptState(
                        shareCode = shareCode,
                        clipboardFingerprint = clipboardFingerprint,
                    ),
                ),
            )
        }
    }

    private fun clipboardFingerprint(clip: ClipData, texts: List<String>): String =
        listOf(
            clip.description.timestamp.toString(),
            clip.description.label?.toString().orEmpty(),
            texts.joinToString(separator = "\u001F").take(4096),
        ).joinToString(separator = "\u001E")

    private fun clipboardItemTexts(item: ClipData.Item): List<String> =
        buildList {
            item.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            item.htmlText?.takeIf { it.isNotBlank() }?.let(::add)
            item.uri?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            item.intent?.dataString?.takeIf { it.isNotBlank() }?.let(::add)
            item.coerceToText(appContext)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()

    fun dismissIncomingSharePrompt() {
        val prompt = uiState.value.incomingShare.prompt
        val shareCode = prompt?.shareCode
        if (!shareCode.isNullOrBlank()) {
            lastDismissedClipboardShareCode = shareCode
            lastDismissedClipboardFingerprint = prompt.clipboardFingerprint
            lastDismissedClipboardAtMillis = System.currentTimeMillis()
        }

        _uiState.update { state ->
            state.copy(incomingShare = state.incomingShare.copy(prompt = null))
        }
    }

    fun confirmIncomingSharePrompt() {
        val prompt = uiState.value.incomingShare.prompt ?: return
        _uiState.update { state ->
            state.copy(incomingShare = state.incomingShare.copy(prompt = null))
        }
        openIncomingShare(
            shareCode = prompt.shareCode,
            source = IncomingShareSource.CLIPBOARD,
            clipboardFingerprint = prompt.clipboardFingerprint,
        )
    }

    fun closeIncomingShare() {
        val incomingShare = uiState.value.incomingShare
        if (incomingShare.source == IncomingShareSource.CLIPBOARD && !incomingShare.activeShareCode.isNullOrBlank()) {
            lastDismissedClipboardShareCode = incomingShare.activeShareCode
            lastDismissedClipboardFingerprint = incomingShare.clipboardFingerprint
            lastDismissedClipboardAtMillis = System.currentTimeMillis()
        }

        _uiState.update { state ->
            state.copy(incomingShare = IncomingShareUiState())
        }
    }

    fun toggleIncomingShareFolder(folderId: Long) {
        _uiState.update { state ->
            val expandedFolderIds = state.incomingShare.expandedFolderIds
            state.copy(
                incomingShare = state.incomingShare.copy(
                    expandedFolderIds = if (folderId in expandedFolderIds) {
                        expandedFolderIds - folderId
                    } else {
                        expandedFolderIds + folderId
                    },
                ),
            )
        }
    }

    fun toggleIncomingShareNodeSelection(nodeId: Long) {
        _uiState.update { state ->
            val incomingShare = state.incomingShare
            val detail = incomingShare.detail ?: return@update state
            state.copy(
                incomingShare = incomingShare.copy(
                    selectedNodeIds = ShareTreeSelection.toggle(
                        items = detail.items,
                        selectedNodeIds = incomingShare.selectedNodeIds,
                        nodeId = nodeId,
                    ),
                ),
            )
        }
    }

    fun openIncomingShareSaveTargetPicker() {
        val incomingShare = uiState.value.incomingShare
        val detail = incomingShare.detail ?: return
        if (!detail.allowSave) {
            emitMessage("分享者未开放保存权限。")
            return
        }

        if (incomingShare.selectedNodeIds.isEmpty()) {
            emitMessage("请先选择要保存的分享内容。")
            return
        }

        val session = authenticatedSession()
        if (session == null) {
            emitMessage("请先登录后再保存分享。")
            return
        }

        _uiState.update { state ->
            state.copy(
                incomingShare = state.incomingShare.copy(
                    saveTargetPickerOpen = true,
                    saveTargetParentId = null,
                    saveTargetLoading = true,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                repository.fetchFolders(session.baseUrl, session.token)
            }.onSuccess { folders ->
                _uiState.update { state ->
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            saveTargetFolders = folders,
                            saveTargetLoading = false,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            saveTargetLoading = false,
                        ),
                    )
                }
                handleError(error)
            }
        }
    }

    fun closeIncomingShareSaveTargetPicker() {
        _uiState.update { state ->
            state.copy(
                incomingShare = state.incomingShare.copy(
                    saveTargetPickerOpen = false,
                    saveTargetLoading = false,
                ),
            )
        }
    }

    fun selectIncomingShareSaveTarget(parentId: Long?) {
        _uiState.update { state ->
            state.copy(
                incomingShare = state.incomingShare.copy(
                    saveTargetParentId = parentId,
                ),
            )
        }
    }

    fun dismissIncomingShareLoginNotice() {
        _uiState.update { state ->
            state.copy(
                incomingShare = state.incomingShare.copy(
                    loginPromptDismissed = true,
                ),
            )
        }
    }

    fun verifyIncomingSharePassword(password: String) {
        val shareCode = uiState.value.incomingShare.activeShareCode ?: return
        val trimmedPassword = password.trim()
        if (trimmedPassword.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    incomingShare = state.incomingShare.copy(
                        passwordError = "请输入提取码。",
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    incomingShare = state.incomingShare.copy(
                        passwordChecking = true,
                        passwordError = null,
                        error = null,
                    ),
                )
            }

            runCatching {
                repository.verifySharePassword(
                    baseUrl = uiState.value.baseUrl,
                    shareCode = shareCode,
                    password = trimmedPassword,
                )
            }.onSuccess { response ->
                val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
                if (accessToken == null) {
                    _uiState.update { state ->
                        state.copy(
                            incomingShare = state.incomingShare.copy(
                                passwordChecking = false,
                                passwordError = "提取码校验未返回访问凭证，请重试。",
                            ),
                        )
                    }
                    return@onSuccess
                }

                _uiState.update { state ->
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            passwordChecking = false,
                            shareAccessToken = accessToken,
                            passwordError = null,
                        ),
                    )
                }
                emitMessage("提取码校验通过。")
                refreshIncomingShareDetailIfReady()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            passwordChecking = false,
                            passwordError = error.readableMessage(),
                        ),
                    )
                }
            }
        }
    }

    fun saveIncomingShareToDrive() {
        val incomingShare = uiState.value.incomingShare
        val detail = incomingShare.detail ?: return
        val targetParentId = incomingShare.saveTargetParentId
        val targetFolders = incomingShare.saveTargetFolders
        val selectedNodeIds = ShareTreeSelection.minimalSelectedRootIds(
            items = detail.items,
            selectedNodeIds = incomingShare.selectedNodeIds,
        )
        if (selectedNodeIds.isEmpty()) {
            emitMessage("请先选择要保存的分享内容。")
            return
        }
        if (!detail.allowSave) {
            emitMessage("分享者未开放保存权限。")
            return
        }

        val session = authenticatedSession()
        if (session == null) {
            emitMessage("请先登录后再保存分享。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(incomingShare = state.incomingShare.copy(saving = true, error = null))
            }

            runCatching {
                repository.saveShareToDrive(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    shareCode = detail.shareCode,
                    shareAccessToken = incomingShare.shareAccessToken,
                    parentId = targetParentId,
                    selectedNodeIds = selectedNodeIds,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        selectedTab = AppTab.FILES,
                        files = state.files.copy(
                            currentFolderId = targetParentId,
                            breadcrumbs = resolveFolderBreadcrumbs(targetFolders, targetParentId),
                            keyword = "",
                            searchScope = FileSearchScope.CURRENT_FOLDER,
                            filter = StorageNodeFilter.ALL,
                            items = emptyList(),
                            hasLoadedFolder = false,
                            selectedNodeIds = emptySet(),
                            highlightedNodeId = null,
                        ),
                        incomingShare = IncomingShareUiState(),
                    )
                }
                emitMessage(if (targetParentId == null) "已保存到你的网盘根目录。" else "已保存到选定文件夹。")
                refreshAll()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(incomingShare = state.incomingShare.copy(saving = false))
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

        clearPreviewArtifacts()
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
                when (previewKind) {
                    PreviewKind.TEXT -> {
                        val file = repository.downloadFileViaSignedUrl(
                            baseUrl = session.baseUrl,
                            token = session.token,
                            fileId = node.id,
                        )
                        FilePreviewState(
                            visible = true,
                            loading = false,
                            fileName = file.fileName,
                            kind = previewKind,
                            textContent = decodePreviewText(file.bytes, file.contentType),
                        )
                    }

                    PreviewKind.PDF -> {
                        val previewFile = repository.cachePreviewFileViaSignedUrl(
                            context = appContext,
                            baseUrl = session.baseUrl,
                            token = session.token,
                            fileId = node.id,
                        )
                        currentPreviewCacheFile = File(previewFile.localPath)
                        FilePreviewState(
                            visible = true,
                            loading = false,
                            fileName = previewFile.fileName,
                            kind = previewKind,
                            localFilePath = previewFile.localPath,
                        )
                    }

                    PreviewKind.IMAGE,
                    PreviewKind.VIDEO,
                    PreviewKind.AUDIO,
                    -> {
                        val access = repository.fetchInlineFileAccessUrl(
                            baseUrl = session.baseUrl,
                            token = session.token,
                            fileId = node.id,
                        )
                        FilePreviewState(
                            visible = true,
                            loading = false,
                            fileName = access.fileName ?: node.name,
                            kind = previewKind,
                            previewUrl = access.url,
                        )
                    }
                }
            }.onSuccess { previewState ->
                _uiState.update { state ->
                    state.copy(preview = previewState)
                }
            }.onFailure { error ->
                clearPreviewArtifacts()
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
        clearPreviewArtifacts()
        _uiState.update { state -> state.copy(preview = FilePreviewState()) }
    }

    fun dismissAppUpdate() {
        dismissedUpdateVersionName = uiState.value.appUpdate?.latestVersionName
        _uiState.update { state -> state.copy(appUpdate = null) }
    }

    fun openAppUpdateDownload() {
        val appUpdate = uiState.value.appUpdate ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appUpdate.downloadUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            appContext.startActivity(intent)
        }.onSuccess {
            dismissAppUpdate()
        }.onFailure {
            emitMessage("无法打开更新链接，请稍后再试。")
        }
    }

    fun downloadFileToUri(node: StorageNode, destinationUri: Uri) {
        val session = authenticatedSession() ?: return
        val taskId = appendTransfer(
            TransferTask(
                id = allocateTransferId(),
                kind = TransferKind.DOWNLOAD,
                itemKind = TransferItemKind.FILE,
                title = node.name,
                status = TransferStatus.PREPARING,
                sourceNodeIds = listOf(node.id),
                destinationUri = destinationUri,
                totalBytes = node.size.takeIf { it > 0L },
                locationLabel = resolveDestinationLabel(destinationUri, node.name),
            ),
        )

        _uiState.update { state ->
            state.copy(
                transferPanelOpen = false,
                transferPanelTab = TransferPanelTab.DOWNLOADS,
                files = state.files.copy(actionNodeId = node.id),
            )
        }

        val taskJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(files = state.files.copy(actionNodeId = node.id))
            }

            runCatching {
                repository.saveDownloadedFileToUriViaSignedUrl(
                    context = appContext,
                    baseUrl = session.baseUrl,
                    token = session.token,
                    fileId = node.id,
                    destinationUri = destinationUri,
                    onProgress = { progress ->
                        updateTransferProgress(taskId, progress)
                    },
                )
            }.onSuccess { fileName ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(actionNodeId = null))
                }
                updateTransfer(taskId) { task ->
                    task.copy(
                        status = TransferStatus.COMPLETED,
                        title = fileName,
                        transferredBytes = task.totalBytes ?: task.transferredBytes,
                        progressPercent = 100,
                        errorMessage = null,
                    )
                }
                emitMessage("已保存：$fileName")
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(files = state.files.copy(actionNodeId = null))
                }
                if (error is CancellationException) {
                    updateTransfer(taskId) { task ->
                        task.copy(status = TransferStatus.CANCELED)
                    }
                } else {
                    updateTransfer(taskId) { task ->
                        task.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = error.readableMessage(),
                        )
                    }
                    handleError(error)
                }
            }
        }
        transferJobs[taskId] = taskJob
        taskJob.invokeOnCompletion { transferJobs.remove(taskId) }
    }

    fun downloadArchiveToUri(nodeIds: List<Long>, destinationUri: Uri) {
        val session = authenticatedSession() ?: return
        val uniqueNodeIds = nodeIds.distinct()

        if (uniqueNodeIds.isEmpty()) {
            emitMessage("请先选择要下载的项目。")
            return
        }

        val taskTitle = resolveArchiveTransferTitle(uniqueNodeIds)
        val taskId = appendTransfer(
            TransferTask(
                id = allocateTransferId(),
                kind = TransferKind.DOWNLOAD,
                itemKind = TransferItemKind.ARCHIVE,
                title = taskTitle,
                status = TransferStatus.PREPARING,
                sourceNodeIds = uniqueNodeIds,
                destinationUri = destinationUri,
                locationLabel = resolveDestinationLabel(destinationUri, taskTitle),
            ),
        )

        _uiState.update { state ->
            state.copy(
                transferPanelOpen = false,
                transferPanelTab = TransferPanelTab.DOWNLOADS,
                files = state.files.copy(
                    isBatchActing = true,
                    actionNodeId = uniqueNodeIds.singleOrNull(),
                ),
            )
        }

        val taskJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    files = state.files.copy(
                        isBatchActing = true,
                        actionNodeId = uniqueNodeIds.singleOrNull(),
                    ),
                )
            }

            runCatching {
                repository.saveArchiveToUri(
                    context = appContext,
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeIds = uniqueNodeIds,
                    destinationUri = destinationUri,
                    onProgress = { progress ->
                        updateTransferProgress(taskId, progress)
                    },
                )
            }.onSuccess { fileName ->
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            isBatchActing = false,
                            actionNodeId = null,
                            selectedNodeIds = emptySet(),
                        ),
                    )
                }
                updateTransfer(taskId) { task ->
                    task.copy(
                        status = TransferStatus.COMPLETED,
                        title = fileName,
                        progressPercent = 100,
                        errorMessage = null,
                    )
                }
                emitMessage("已保存：$fileName")
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            isBatchActing = false,
                            actionNodeId = null,
                        ),
                    )
                }
                if (error is CancellationException) {
                    updateTransfer(taskId) { task ->
                        task.copy(status = TransferStatus.CANCELED)
                    }
                } else {
                    updateTransfer(taskId) { task ->
                        task.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = error.readableMessage(),
                        )
                    }
                    handleError(error)
                }
            }
        }
        transferJobs[taskId] = taskJob
        taskJob.invokeOnCompletion { transferJobs.remove(taskId) }
    }

    fun retryDownloadTransfer(taskId: Long) {
        val task = uiState.value.transfers.firstOrNull { it.id == taskId } ?: return

        if (task.kind != TransferKind.DOWNLOAD || task.status != TransferStatus.FAILED) {
            return
        }

        if (transferJobs[taskId]?.isActive == true) {
            emitMessage("该任务正在传输中。")
            return
        }

        val destinationUri = task.destinationUri
        val sourceNodeIds = task.sourceNodeIds
        if (destinationUri == null || sourceNodeIds.isEmpty()) {
            emitMessage("无法重新下载，请从文件列表重新选择保存位置。")
            return
        }

        if (task.itemKind == TransferItemKind.FILE && sourceNodeIds.size != 1) {
            emitMessage("无法重新下载，请从文件列表重新选择文件。")
            return
        }

        val session = authenticatedSession()
        if (session == null) {
            updateTransfer(taskId) { current ->
                current.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = "登录状态不可用，请重新登录后再试。",
                )
            }
            emitMessage("登录状态不可用，请重新登录后再试。")
            return
        }

        updateTransfer(taskId) { current ->
            current.copy(
                status = TransferStatus.PREPARING,
                transferredBytes = 0L,
                progressPercent = null,
                errorMessage = null,
            )
        }

        _uiState.update { state ->
            state.copy(
                transferPanelOpen = false,
                transferPanelTab = TransferPanelTab.DOWNLOADS,
                files = state.files.copy(
                    isBatchActing = task.itemKind == TransferItemKind.ARCHIVE,
                    actionNodeId = sourceNodeIds.singleOrNull(),
                ),
            )
        }

        val taskJob = viewModelScope.launch {
            runCatching {
                if (task.itemKind == TransferItemKind.FILE) {
                    repository.saveDownloadedFileToUriViaSignedUrl(
                        context = appContext,
                        baseUrl = session.baseUrl,
                        token = session.token,
                        fileId = sourceNodeIds.first(),
                        destinationUri = destinationUri,
                        onProgress = { progress ->
                            updateTransferProgress(taskId, progress)
                        },
                    )
                } else {
                    repository.saveArchiveToUri(
                        context = appContext,
                        baseUrl = session.baseUrl,
                        token = session.token,
                        nodeIds = sourceNodeIds,
                        destinationUri = destinationUri,
                        onProgress = { progress ->
                            updateTransferProgress(taskId, progress)
                        },
                    )
                }
            }.onSuccess { fileName ->
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            isBatchActing = false,
                            actionNodeId = null,
                        ),
                    )
                }
                updateTransfer(taskId) { current ->
                    current.copy(
                        status = TransferStatus.COMPLETED,
                        title = fileName,
                        transferredBytes = current.totalBytes ?: current.transferredBytes,
                        progressPercent = 100,
                        errorMessage = null,
                    )
                }
                emitMessage("已保存：$fileName")
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            isBatchActing = false,
                            actionNodeId = null,
                        ),
                    )
                }
                if (error is CancellationException) {
                    updateTransfer(taskId) { current ->
                        current.copy(status = TransferStatus.CANCELED)
                    }
                } else {
                    updateTransfer(taskId) { current ->
                        current.copy(
                            status = TransferStatus.FAILED,
                            errorMessage = error.readableMessage(),
                        )
                    }
                    handleError(error)
                }
            }
        }
        transferJobs[taskId] = taskJob
        taskJob.invokeOnCompletion { transferJobs.remove(taskId) }
    }

    fun logout() {
        val baseUrl = uiState.value.baseUrl
        viewModelScope.launch {
            transferJobs.values.forEach { job -> job.cancel() }
            transferJobs.clear()
            sessionStore.clearToken(baseUrl)
            fileDirectoryCache.clear()
            clearPreviewArtifacts()
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
                checkForAppUpdate(session.baseUrl)
                return@launch
            }

            runCatching {
                val currentUser = repository.fetchCurrentUser(session.baseUrl, session.token)
                session to currentUser
            }.onSuccess { (savedSession, currentUser) ->
                fileDirectoryCache.clear()
                clearPreviewArtifacts()
                _uiState.update { state ->
                    state.copy(
                        isBooting = false,
                        authToken = savedSession.token,
                        currentUser = currentUser,
                        baseUrl = savedSession.baseUrl,
                    )
                }
                refreshAll()
                checkForAppUpdate(savedSession.baseUrl)
                refreshIncomingShareDetailIfReady()
            }.onFailure { error ->
                sessionStore.clearToken(session.baseUrl)
                clearPreviewArtifacts()
                _uiState.update { state ->
                    state.copy(isBooting = false, authToken = null, currentUser = null)
                }
                handleError(error)
                checkForAppUpdate(session.baseUrl)
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
                val recentNodes = repository.fetchStorageNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    parentId = null,
                    keyword = "",
                    filter = StorageNodeFilter.FILE,
                    size = 4,
                    sortBy = "createdAt",
                    sortDirection = "desc",
                    recursive = true,
                ).items
                Triple(overview, history, recentNodes)
            }.onSuccess { (overview, history, recentNodes) ->
                _uiState.update { state ->
                    state.copy(
                        home = state.home.copy(
                            loading = false,
                            error = null,
                            overview = overview,
                            usageHistory = history,
                            recentNodes = recentNodes,
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
                    parentId = if (files.category != null || files.searchScope == FileSearchScope.GLOBAL) {
                        null
                    } else {
                        files.currentFolderId
                    },
                    keyword = files.keyword,
                    filter = if (files.category == null) files.filter else StorageNodeFilter.FILE,
                    recursive = files.category != null || (files.searchScope == FileSearchScope.GLOBAL && files.keyword.isNotBlank()),
                    category = files.category,
                )
            }.onSuccess { page ->
                val visibleIds = page.items.mapTo(hashSetOf()) { it.id }
                if (files.category == null && files.searchScope != FileSearchScope.GLOBAL) {
                    fileDirectoryCache[files.currentFolderId] = page.items
                }
                _uiState.update { state ->
                    state.copy(
                        files = state.files.copy(
                            loading = false,
                            error = null,
                            hasLoadedFolder = true,
                            items = page.items,
                            selectedNodeIds = state.files.selectedNodeIds.filterTo(linkedSetOf()) { it in visibleIds },
                            highlightedNodeId = state.files.highlightedNodeId?.takeIf { it in visibleIds },
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
                val visibleIds = page.items.mapTo(hashSetOf()) { it.id }
                _uiState.update { state ->
                    state.copy(
                        trash = state.trash.copy(
                            loading = false,
                            error = null,
                            hasLoadedFolder = true,
                            items = page.items,
                            selectedNodeIds = state.trash.selectedNodeIds.filterTo(linkedSetOf()) { it in visibleIds },
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

    private fun updateExplorerState(
        isTrashMode: Boolean,
        transform: (ExplorerUiState) -> ExplorerUiState,
    ) {
        _uiState.update { state ->
            if (isTrashMode) {
                state.copy(trash = transform(state.trash))
            } else {
                state.copy(files = transform(state.files))
            }
        }
    }

    private fun resolvePreviewKind(node: StorageNode): PreviewKind? {
        val mimeType = node.mimeType?.lowercase().orEmpty()
        val extension = node.extension?.lowercase().orEmpty()

        return when {
            mimeType.startsWith("image/") -> PreviewKind.IMAGE
            mimeType == "application/pdf" || extension == "pdf" -> PreviewKind.PDF
            mimeType.startsWith("video/") -> PreviewKind.VIDEO
            mimeType.startsWith("audio/") -> PreviewKind.AUDIO
            mimeType.startsWith("text/") -> PreviewKind.TEXT
            extension in PREVIEWABLE_TEXT_EXTENSIONS -> PreviewKind.TEXT
            else -> null
        }
    }

    private fun clearPreviewArtifacts() {
        currentPreviewCacheFile?.takeIf(File::exists)?.delete()
        currentPreviewCacheFile = null
    }

    private fun decodePreviewText(bytes: ByteArray, contentType: String?): String {
        val charsetNames = buildList {
            detectBomCharset(bytes)?.let(::add)
            extractCharsetName(contentType)?.let(::add)
            add(StandardCharsets.UTF_8.name())
            add("GB18030")
            add(StandardCharsets.UTF_16LE.name())
            add(StandardCharsets.UTF_16BE.name())
        }.distinct()

        for (charsetName in charsetNames) {
            val charset = runCatching { Charset.forName(charsetName) }.getOrNull() ?: continue
            val decoded = runCatching { decodeStrict(bytes, charset) }.getOrNull() ?: continue
            return decoded.removePrefix("\uFEFF")
        }

        return bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun extractCharsetName(contentType: String?): String? =
        contentType
            ?.split(';')
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.takeIf(String::isNotBlank)

    private fun detectBomCharset(bytes: ByteArray): String? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> StandardCharsets.UTF_8.name()
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> StandardCharsets.UTF_16LE.name()
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> StandardCharsets.UTF_16BE.name()
        else -> null
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String =
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun openIncomingShare(
        shareCode: String,
        source: IncomingShareSource,
        clipboardFingerprint: String? = null,
    ) {
        val baseUrl = uiState.value.baseUrl

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    incomingShare = IncomingShareUiState(
                        activeShareCode = shareCode,
                        clipboardFingerprint = clipboardFingerprint,
                        source = source,
                        statusLoading = true,
                    ),
                )
            }

            runCatching {
                repository.fetchPublicShareStatus(
                    baseUrl = baseUrl,
                    shareCode = shareCode,
                )
            }.onSuccess { status ->
                _uiState.update { state ->
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            statusLoading = false,
                            status = status,
                            error = if (status.available) null else shareUnavailableMessage(status),
                        ),
                    )
                }
                refreshIncomingShareDetailIfReady()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            statusLoading = false,
                            error = error.readableMessage(),
                        ),
                    )
                }
            }
        }
    }

    private fun refreshIncomingShareDetailIfReady() {
        val incomingShare = uiState.value.incomingShare
        val shareCode = incomingShare.activeShareCode ?: return
        val status = incomingShare.status ?: return

        if (!status.available || incomingShare.detailLoading) {
            return
        }

        if (status.requiresPassword && incomingShare.shareAccessToken.isNullOrBlank()) {
            return
        }

        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    incomingShare = state.incomingShare.copy(
                        detailLoading = true,
                        error = null,
                    ),
                )
            }

            runCatching {
                repository.fetchShareDetail(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    shareCode = shareCode,
                    shareAccessToken = incomingShare.shareAccessToken,
                )
            }.onSuccess { detail ->
                _uiState.update { state ->
                    if (state.incomingShare.activeShareCode != shareCode) {
                        state
                    } else {
                        val defaultSelectedNodeIds = if (detail.allowSave) {
                            ShareTreeSelection.allNodeIds(detail.items)
                        } else {
                            emptySet()
                        }
                        state.copy(
                            incomingShare = state.incomingShare.copy(
                                detailLoading = false,
                                detail = detail,
                                selectedNodeIds = defaultSelectedNodeIds,
                                error = null,
                            ),
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    if (state.incomingShare.activeShareCode != shareCode) {
                        state
                    } else {
                        val invalidPasswordAccess = status.requiresPassword &&
                            error is ApiException &&
                            error.status == 400

                        state.copy(
                            incomingShare = state.incomingShare.copy(
                                detailLoading = false,
                                shareAccessToken = if (invalidPasswordAccess) null else state.incomingShare.shareAccessToken,
                                passwordError = if (invalidPasswordAccess) "提取码凭证已失效，请重新输入。" else state.incomingShare.passwordError,
                                error = if (invalidPasswordAccess) null else error.readableMessage(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun shareUnavailableMessage(status: ShareLinkStatusResponse): String =
        when (status.reason) {
            "EXPIRED" -> "分享链接已过期。"
            "REVOKED" -> "分享链接已被取消。"
            else -> "分享链接暂不可用。"
        }

    private fun allocateTransferId(): Long = nextTransferId++

    private fun appendTransfer(task: TransferTask): Long {
        _uiState.update { state ->
            state.copy(
                transfers = (listOf(task) + state.transfers).take(MAX_TRANSFER_HISTORY),
            )
        }
        return task.id
    }

    private fun updateTransfer(
        taskId: Long,
        transform: (TransferTask) -> TransferTask,
    ) {
        _uiState.update { state ->
            state.copy(
                transfers = state.transfers.map { task ->
                    if (task.id == taskId) transform(task) else task
                },
            )
        }
    }

    private fun updateTransferProgress(taskId: Long, progress: TransferProgress) {
        if (transferJobs[taskId]?.isCancelled == true) {
            throw CancellationException()
        }

        updateTransfer(taskId) { task ->
            if (
                task.status == TransferStatus.COMPLETED ||
                task.status == TransferStatus.FAILED ||
                task.status == TransferStatus.CANCELED
            ) {
                return@updateTransfer task
            }

            val totalBytes = progress.totalBytes ?: task.totalBytes
            val progressPercent = totalBytes
                ?.takeIf { it > 0L }
                ?.let { total ->
                    ((progress.transferredBytes.toDouble() / total.toDouble()) * 100)
                        .roundToLong()
                        .toInt()
                        .coerceIn(0, 99)
                }

            task.copy(
                status = TransferStatus.RUNNING,
                transferredBytes = progress.transferredBytes,
                totalBytes = totalBytes,
                progressPercent = progressPercent,
                errorMessage = null,
            )
        }
    }

    private fun resolveDestinationLabel(uri: Uri, fallbackName: String): String {
        val displayName = runCatching {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()

        return displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: fallbackName
    }

    private fun resolveUploadLocationLabel(parentId: Long?): String {
        if (parentId == null) {
            return "根目录"
        }

        return uiState.value.files.breadcrumbs
            .lastOrNull { it.id == parentId }
            ?.label
            ?: "当前目录"
    }

    private fun resolveArchiveTransferTitle(nodeIds: List<Long>): String {
        if (nodeIds.size != 1) {
            return "选中 ${nodeIds.size} 项.zip"
        }

        val node = uiState.value.files.items.firstOrNull { it.id == nodeIds.first() }
        val name = node?.name?.trim().orEmpty().ifBlank { "AliciaCloud" }
        val baseName = name.removeSuffix(".zip").ifBlank { "AliciaCloud" }
        return "$baseName.zip"
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
        if (files.searchScope == FileSearchScope.GLOBAL) {
            return
        }
        fileDirectoryCache[files.currentFolderId] = files.items
    }

    private fun resolveRevealBreadcrumbs(
        current: List<FolderCrumb>,
        targetFolderId: Long?,
    ): List<FolderCrumb> {
        if (targetFolderId == null) {
            return defaultBreadCrumbs
        }

        val currentIndex = current.indexOfFirst { it.id == targetFolderId }
        if (currentIndex >= 0) {
            return current.take(currentIndex + 1)
        }

        val folderLabel = current.firstOrNull { it.id == targetFolderId }?.label
            ?: fileDirectoryCache.values
                .asSequence()
                .flatten()
                .firstOrNull { it.id == targetFolderId && it.type == StorageNodeType.FOLDER }
                ?.name
            ?: "目标目录"

        return defaultBreadCrumbs + FolderCrumb(id = targetFolderId, label = folderLabel)
    }

    private fun resolveFolderBreadcrumbs(
        folders: List<StorageNode>,
        targetFolderId: Long?,
    ): List<FolderCrumb> {
        if (targetFolderId == null) {
            return defaultBreadCrumbs
        }

        val folderById = folders.associateBy { it.id }
        val path = mutableListOf<StorageNode>()
        val visitedIds = mutableSetOf<Long>()
        var cursor = folderById[targetFolderId]

        while (cursor != null && visitedIds.add(cursor.id)) {
            path += cursor
            cursor = cursor.parentId?.let(folderById::get)
        }

        if (path.isEmpty()) {
            return defaultBreadCrumbs + FolderCrumb(id = targetFolderId, label = "目标目录")
        }

        return defaultBreadCrumbs + path.asReversed().map { folder ->
            FolderCrumb(id = folder.id, label = folder.name)
        }
    }

    private fun parseQuotaGbToBytes(value: String): Long? {
        val quotaGb = value.trim().toDoubleOrNull()
        if (quotaGb == null || !quotaGb.isFinite() || quotaGb <= 0) {
            emitMessage("请输入大于 0 的 GB 额度，例如 50。")
            return null
        }

        return (quotaGb * BYTES_PER_GIB.toDouble()).roundToLong()
    }

    private fun checkForAppUpdate(baseUrl: String) {
        if (!BuildConfig.APP_UPDATE_ENABLED) {
            _uiState.update { state -> state.copy(appUpdate = null) }
            return
        }

        viewModelScope.launch {
            runCatching {
                repository.fetchLatestAppVersion(baseUrl)
            }.onSuccess { versionInfo ->
                val latestVersionName = versionInfo.versionName?.trim().orEmpty()

                if (!versionInfo.available || latestVersionName.isBlank()) {
                    _uiState.update { state -> state.copy(appUpdate = null) }
                    return@onSuccess
                }

                if (dismissedUpdateVersionName == latestVersionName) {
                    _uiState.update { state -> state.copy(appUpdate = null) }
                    return@onSuccess
                }

                val currentVersionName = resolveInstalledVersionName()
                if (compareVersionNames(currentVersionName, latestVersionName) >= 0) {
                    _uiState.update { state -> state.copy(appUpdate = null) }
                    return@onSuccess
                }

                _uiState.update { state ->
                    state.copy(
                        appUpdate = AppUpdateState(
                            currentVersionName = currentVersionName,
                            latestVersionName = latestVersionName,
                            releaseNotes = versionInfo.releaseNotes?.trim().orEmpty(),
                            downloadUrl = resolveAppUpdateDownloadUrl(baseUrl, versionInfo),
                        ),
                    )
                }
            }
        }
    }

    private fun resolveInstalledVersionName(): String =
        BuildConfig.VERSION_NAME
            .trim()
            .ifBlank { "0.0.0" }

    private fun resolveAppUpdateDownloadUrl(baseUrl: String, versionInfo: AppPackageVersionInfo): String =
        runCatching {
            URI("${baseUrl.trim().removeSuffix("/")}/")
                .resolve(versionInfo.downloadUrl)
                .toString()
        }.getOrElse {
            if (versionInfo.downloadUrl.startsWith("http://") || versionInfo.downloadUrl.startsWith("https://")) {
                versionInfo.downloadUrl
            } else {
                "${baseUrl.trim().removeSuffix("/")}/${versionInfo.downloadUrl.trimStart('/')}"
            }
        }

    private fun compareVersionNames(currentVersionName: String, latestVersionName: String): Int {
        val currentTokens = tokenizeVersionName(currentVersionName)
        val latestTokens = tokenizeVersionName(latestVersionName)
        val maxTokenCount = maxOf(currentTokens.size, latestTokens.size)

        for (index in 0 until maxTokenCount) {
            val comparison = compareVersionToken(
                currentTokens.getOrElse(index) { "0" },
                latestTokens.getOrElse(index) { "0" },
            )
            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    private fun tokenizeVersionName(versionName: String): List<String> =
        versionName
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(Regex("[^0-9A-Za-z]+"))
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("0") }

    private fun compareVersionToken(currentToken: String, latestToken: String): Int {
        val currentNumber = currentToken.toLongOrNull()
        val latestNumber = latestToken.toLongOrNull()

        return when {
            currentNumber != null && latestNumber != null -> currentNumber.compareTo(latestNumber)
            currentNumber != null -> 1
            latestNumber != null -> -1
            else -> currentToken.compareTo(latestToken, ignoreCase = true)
        }
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

    override fun onCleared() {
        transferJobs.values.forEach { job -> job.cancel() }
        transferJobs.clear()
        clearPreviewArtifacts()
        super.onCleared()
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
