package com.alicia.cloudstorage.phone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alicia.cloudstorage.phone.BuildConfig
import com.alicia.cloudstorage.phone.AppUpdatePolicy
import com.alicia.cloudstorage.phone.ClipboardSharePolicy
import com.alicia.cloudstorage.phone.ShareLinkParser
import com.alicia.cloudstorage.phone.describeAccessEnvironment
import com.alicia.cloudstorage.phone.normalizeConfiguredBaseUrl
import com.alicia.cloudstorage.phone.data.AliciaRepository
import com.alicia.cloudstorage.phone.data.ApiException
import com.alicia.cloudstorage.phone.data.AppTab
import com.alicia.cloudstorage.phone.data.DriveOverview
import com.alicia.cloudstorage.phone.data.FolderCrumb
import com.alicia.cloudstorage.phone.data.ClipboardShareReceipt
import com.alicia.cloudstorage.phone.data.LoginResponse
import com.alicia.cloudstorage.phone.data.SessionStore
import com.alicia.cloudstorage.phone.data.ShareLinkDetailResponse
import com.alicia.cloudstorage.phone.data.ShareLinkStatusResponse
import com.alicia.cloudstorage.phone.data.StorageFileCategory
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeFilter
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.TransferHistoryPersistence
import com.alicia.cloudstorage.phone.data.TransferHistoryStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

private const val MAX_TEXT_PREVIEW_BYTES = 2L * 1024 * 1024
private const val BYTES_PER_GIB = 1024L * 1024 * 1024
private const val MAX_TRANSFER_HISTORY = 600
private const val MIN_GLOBAL_LOADING_MILLIS = 420L
private const val MIN_BOOT_SPLASH_MILLIS = 850L
private const val MAX_UPLOAD_BATCH_FILES = 500
private const val MAX_FOLDER_UPLOAD_DIRECTORIES = 500

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
    val refreshToken: String?,
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
    val submittedKeyword: String = "",
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
    val renameTarget: StorageNode? = null,
    val renameSubmitting: Boolean = false,
    val renameError: String? = null,
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

data class VersionUpdateUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val latestVersionName: String? = null,
    val releaseNotes: String = "",
    val downloadUrl: String? = null,
    val checking: Boolean = false,
    val updateAvailable: Boolean = false,
)

enum class IncomingShareSource {
    CLIPBOARD,
    DEEP_LINK,
    SEARCH_INPUT,
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
    val isSendingRegistrationCode: Boolean = false,
    val isSubmittingRegistration: Boolean = false,
    val isManualRefreshing: Boolean = false,
    val isRefreshingUser: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val isUpdatingAvatar: Boolean = false,
    val isChangingPassword: Boolean = false,
    val baseUrl: String = BuildConfig.DEFAULT_API_BASE_URL,
    val authToken: String? = null,
    val refreshToken: String? = null,
    val currentUser: User? = null,
    val selectedTab: AppTab = AppTab.HOME,
    val home: HomeUiState = HomeUiState(),
    val files: ExplorerUiState = ExplorerUiState(),
    val trash: ExplorerUiState = ExplorerUiState(breadcrumbs = emptyList()),
    val team: TeamUiState = TeamUiState(),
    val preview: FilePreviewState = FilePreviewState(),
    val appUpdate: AppUpdateState? = null,
    val versionUpdate: VersionUpdateUiState = VersionUpdateUiState(),
    val incomingShare: IncomingShareUiState = IncomingShareUiState(),
    val transfers: List<TransferTask> = emptyList(),
    val transferPanelOpen: Boolean = false,
    val transferPanelTab: TransferPanelTab = TransferPanelTab.DOWNLOADS,
)

class MainViewModel internal constructor(
    private val repository: AliciaRepository,
    private val sessionStore: SessionStore,
    private val transferHistoryPersistence: TransferHistoryPersistence,
    private val defaultBaseUrl: String,
    private val appContext: Context,
) : ViewModel() {
    private val localFolderUploadPlanner = LocalFolderUploadPlanner(
        maxFiles = MAX_UPLOAD_BATCH_FILES,
        maxDirectories = MAX_FOLDER_UPLOAD_DIRECTORIES,
    )
    private val bootStartedAtMillis = SystemClock.elapsedRealtime()
    private val _uiState = MutableStateFlow(AppUiState(baseUrl = defaultBaseUrl))
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val fileDirectoryCache = mutableMapOf<Long?, List<StorageNode>>()
    private var dismissedUpdateVersionName: String? = null
    private var currentPreviewCacheFile: File? = null
    private var lastHandledClipboardShareCode: String? = null
    private var lastHandledClipboardFingerprint: String? = null
    private var lastHandledClipboardAtMillis: Long = 0L
    private var clipboardReceiptLoaded = false
    private val clipboardReceiptMutex = Mutex()
    private var appUpdateCheckJob: Job? = null
    private var manualRefreshJob: Job? = null
    private var manualRefreshGeneration = 0L
    private var currentUserSyncJob: Job? = null
    private var currentUserSyncGeneration = 0L
    private var incomingShareRequestGeneration = 0L
    private var fileRefreshJob: Job? = null
    private var fileRefreshGeneration = 0L
    private var trashRefreshJob: Job? = null
    private var trashRefreshGeneration = 0L
    private var nextTransferId = 1L
    private val transferJobs = ConcurrentHashMap<Long, Job>()
    private val transferHistoryCoordinator = TransferHistoryCoordinator(
        persistence = transferHistoryPersistence,
        scope = viewModelScope,
        maxHistory = MAX_TRANSFER_HISTORY,
    )

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
            transferHistoryCoordinator.clearActive()
            nextTransferId = 1L
            cancelManualRefreshLoading()
            cancelCurrentUserSync()
            cancelExplorerRefreshes()
            incomingShareRequestGeneration += 1L
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

        when (tab.selectionLoad()) {
            TabSelectionLoad.HOME -> refreshHomeIfNeeded()
            TabSelectionLoad.FILES -> refreshFilesIfNeeded()
            TabSelectionLoad.TRASH -> refreshTrashIfNeeded()
            TabSelectionLoad.TEAM -> refreshTeamIfNeeded()
            TabSelectionLoad.NONE -> Unit
        }
    }

    fun login(identifier: String, password: String) {
        val normalizedBaseUrl = runCatching { normalizeBaseUrl(uiState.value.baseUrl) }
            .getOrElse { error ->
                emitMessage(error.message ?: "请输入正确的后端地址。")
                return
            }
        val trimmedIdentifier = identifier.trim()

        if (!isValidLoginIdentifier(trimmedIdentifier)) {
            emitMessage("请输入手机号或邮箱。")
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
                    identifier = trimmedIdentifier,
                    password = password,
                )
            }.onSuccess { response ->
                applyAuthenticatedSession(response, normalizedBaseUrl) { state ->
                    state.copy(isSubmittingLogin = false)
                }
                emitMessage("欢迎回来，${response.user.nickname}")
                refreshAll(syncUser = false)
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

    fun requestEmailRegistrationCode(email: String) {
        val normalizedBaseUrl = runCatching { normalizeBaseUrl(uiState.value.baseUrl) }
            .getOrElse { error ->
                emitMessage(error.message ?: "请输入正确的后端地址。")
                return
            }
        val trimmedEmail = email.trim()

        if (!isValidEmail(trimmedEmail)) {
            emitMessage("请输入有效邮箱地址。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isSendingRegistrationCode = true,
                    baseUrl = normalizedBaseUrl,
                )
            }

            runCatching {
                sessionStore.saveBaseUrl(normalizedBaseUrl)
                repository.requestEmailRegistrationCode(normalizedBaseUrl, trimmedEmail)
            }.onSuccess {
                emitMessage("如果邮箱可用，验证码会发送到该邮箱。")
            }.onFailure { error ->
                handleError(error)
            }

            _uiState.update { state -> state.copy(isSendingRegistrationCode = false) }
        }
    }

    fun registerWithEmail(email: String, code: String, nickname: String, password: String) {
        val normalizedBaseUrl = runCatching { normalizeBaseUrl(uiState.value.baseUrl) }
            .getOrElse { error ->
                emitMessage(error.message ?: "请输入正确的后端地址。")
                return
            }
        val trimmedEmail = email.trim()
        val trimmedCode = code.trim()
        val trimmedNickname = nickname.trim()

        if (!isValidEmail(trimmedEmail)) {
            emitMessage("请输入有效邮箱地址。")
            return
        }

        if (!trimmedCode.matches(Regex("^\\d{6}$"))) {
            emitMessage("请输入 6 位验证码。")
            return
        }

        if (trimmedNickname.isBlank()) {
            emitMessage("请输入昵称。")
            return
        }

        if (password.length < 6) {
            emitMessage("密码长度至少为 6 位。")
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isSubmittingRegistration = true,
                    baseUrl = normalizedBaseUrl,
                )
            }

            runCatching {
                sessionStore.saveBaseUrl(normalizedBaseUrl)
                repository.verifyEmailRegistration(
                    baseUrl = normalizedBaseUrl,
                    email = trimmedEmail,
                    code = trimmedCode,
                    nickname = trimmedNickname,
                    password = password,
                )
            }.onSuccess { response ->
                applyAuthenticatedSession(response, normalizedBaseUrl) { state ->
                    state.copy(isSubmittingRegistration = false)
                }
                emitMessage("注册成功，欢迎使用 Alicia 云盘。")
                refreshAll(syncUser = false)
                checkForAppUpdate(normalizedBaseUrl)
                refreshIncomingShareDetailIfReady()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        isBooting = false,
                        isSubmittingRegistration = false,
                    )
                }
                handleError(error)
            }
        }
    }

    private suspend fun applyAuthenticatedSession(
        response: LoginResponse,
        normalizedBaseUrl: String,
        statePatch: (AppUiState) -> AppUiState = { it },
    ) {
        sessionStore.saveSession(response.token, response.refreshToken, normalizedBaseUrl)
        val restoredTransfers = activateTransferHistory(normalizedBaseUrl, response.user.id)
        fileDirectoryCache.clear()
        clearPreviewArtifacts()
        _uiState.update { state ->
            statePatch(
                state.copy(
                    isBooting = false,
                    isSubmittingLogin = false,
                    isSendingRegistrationCode = false,
                    isSubmittingRegistration = false,
                    authToken = response.token,
                    refreshToken = response.refreshToken,
                    currentUser = response.user,
                    selectedTab = AppTab.HOME,
                    home = HomeUiState(),
                    files = ExplorerUiState(),
                    trash = ExplorerUiState(breadcrumbs = emptyList()),
                    team = TeamUiState(),
                    preview = FilePreviewState(),
                    transfers = restoredTransfers,
                    transferPanelOpen = false,
                    transferPanelTab = TransferPanelTab.DOWNLOADS,
                ),
            )
        }
    }

    private fun isValidLoginIdentifier(identifier: String): Boolean {
        if (identifier.isBlank()) {
            return false
        }

        return if (identifier.contains("@")) {
            isValidEmail(identifier)
        } else {
            identifier.matches(Regex("^1\\d{10}$"))
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))

    fun refreshCurrentTab() {
        val selectedTab = uiState.value.selectedTab
        if (selectedTab != AppTab.TRANSFERS) {
            keepManualRefreshLoadingVisible()
        }

        when (selectedTab) {
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
                }
            }
        }
    }

    fun refreshAfterFileDetailMutation() {
        refreshAfterMutation(refreshFiles = true, refreshTrash = true)
    }

    internal fun refreshAfterAiFileMutation(signal: AiChatFileMutationSignal) {
        when (signal.scope) {
            AiChatFileMutationScope.FILES_ONLY -> refreshAfterMutation(
                refreshFiles = true,
                refreshTrash = false,
            )
            AiChatFileMutationScope.FILES_AND_TRASH -> refreshAfterMutation(
                refreshFiles = true,
                refreshTrash = true,
            )
        }
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
        transferHistoryCoordinator.replace(uiState.value.transfers)
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
        val shouldExitSearch = value.isBlank() && uiState.value.files.submittedKeyword.isNotBlank()
        _uiState.update { state ->
            state.copy(files = state.files.copy(keyword = value, highlightedNodeId = null))
        }
        if (shouldExitSearch) {
            clearFileSearch()
        }
    }

    fun updateTrashKeyword(value: String) {
        val shouldExitSearch = value.isBlank() && uiState.value.trash.submittedKeyword.isNotBlank()
        _uiState.update { state -> state.copy(trash = state.trash.copy(keyword = value)) }
        if (shouldExitSearch) {
            clearTrashSearch()
        }
    }

    fun clearFileSearch() {
        val files = uiState.value.files
        val hadSubmittedSearch = files.submittedKeyword.isNotBlank()
        if (!hadSubmittedSearch && files.keyword.isBlank()) {
            return
        }

        val cachedItems = if (files.category == null && files.filter == StorageNodeFilter.ALL) {
            fileDirectoryCache[files.currentFolderId]
        } else {
            null
        }
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    keyword = "",
                    submittedKeyword = "",
                    searchScope = if (state.files.category == null) {
                        FileSearchScope.CURRENT_FOLDER
                    } else {
                        FileSearchScope.GLOBAL
                    },
                    items = if (hadSubmittedSearch) cachedItems.orEmpty() else state.files.items,
                    hasLoadedFolder = if (hadSubmittedSearch) cachedItems != null else state.files.hasLoadedFolder,
                    loading = hadSubmittedSearch && cachedItems == null,
                    error = null,
                    selectedNodeIds = emptySet(),
                    highlightedNodeId = null,
                ),
            )
        }
        if (hadSubmittedSearch) {
            refreshFiles(forceLoading = false)
        }
    }

    fun clearTrashSearch() {
        val trash = uiState.value.trash
        val hadSubmittedSearch = trash.submittedKeyword.isNotBlank()
        if (!hadSubmittedSearch && trash.keyword.isBlank()) {
            return
        }

        _uiState.update { state ->
            state.copy(
                trash = state.trash.copy(
                    keyword = "",
                    submittedKeyword = "",
                    items = if (hadSubmittedSearch) emptyList() else state.trash.items,
                    hasLoadedFolder = if (hadSubmittedSearch) false else state.trash.hasLoadedFolder,
                    loading = hadSubmittedSearch,
                    error = null,
                    selectedNodeIds = emptySet(),
                ),
            )
        }
        if (hadSubmittedSearch) {
            refreshTrash(forceLoading = false)
        }
    }

    internal fun applyFileFilterSelection(selection: FileFilterSelection) {
        val normalized = selection.normalized(trashMode = false)
        val current = uiState.value.files
        if (current.category == normalized.category && current.filter == normalized.nodeFilter) {
            return
        }
        val categoryChanged = current.category != normalized.category
        val nextSearchScope = nextFileFilterSearchScope(
            currentScope = current.searchScope,
            currentCategory = current.category,
            nextCategory = normalized.category,
        )
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    filter = normalized.nodeFilter,
                    category = normalized.category,
                    currentFolderId = if (categoryChanged) null else state.files.currentFolderId,
                    breadcrumbs = if (categoryChanged) defaultBreadCrumbs else state.files.breadcrumbs,
                    searchScope = nextSearchScope,
                    selectedNodeIds = emptySet(),
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
                    keyword = "",
                    submittedKeyword = "",
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
        if (uiState.value.trash.filter == filter) {
            return
        }
        _uiState.update { state -> state.copy(trash = state.trash.copy(filter = filter)) }
        refreshTrash(forceLoading = true)
    }

    fun submitFileSearch() {
        val normalizedKeyword = uiState.value.files.keyword.trim()
        if (openShareFromSearchInput(normalizedKeyword)) {
            clearFileSearch()
            return
        }
        if (normalizedKeyword.isBlank()) {
            clearFileSearch()
            return
        }
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    highlightedNodeId = null,
                    submittedKeyword = normalizedKeyword,
                    searchScope = if (state.files.category == null) {
                        FileSearchScope.CURRENT_FOLDER
                    } else {
                        FileSearchScope.GLOBAL
                    },
                ),
            )
        }
        refreshFiles(forceLoading = true)
    }

    fun submitHomeFileSearch() {
        val rootItems = fileDirectoryCache[null]
        val normalizedKeyword = uiState.value.files.keyword.trim()
        if (openShareFromSearchInput(normalizedKeyword)) {
            clearFileSearch()
            return
        }
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
                    submittedKeyword = normalizedKeyword,
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
        val normalizedKeyword = uiState.value.trash.keyword.trim()
        if (openShareFromSearchInput(normalizedKeyword)) {
            clearTrashSearch()
            return
        }
        if (normalizedKeyword.isBlank()) {
            clearTrashSearch()
            return
        }
        _uiState.update { state ->
            state.copy(trash = state.trash.copy(submittedKeyword = normalizedKeyword))
        }
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

    fun beginSelectedNodeRename() {
        val files = uiState.value.files
        val selectedId = files.selectedNodeIds.singleOrNull()
        val target = selectedId?.let { id -> files.items.firstOrNull { node -> node.id == id } }
        if (target == null) {
            emitMessage("请只选择一个要重命名的文件或文件夹。")
            return
        }

        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    renameTarget = target,
                    renameSubmitting = false,
                    renameError = null,
                ),
            )
        }
    }

    fun dismissNodeRename() {
        if (uiState.value.files.renameSubmitting) return
        _uiState.update { state ->
            state.copy(
                files = state.files.copy(
                    renameTarget = null,
                    renameError = null,
                ),
            )
        }
    }

    fun renameSelectedNode(rawName: String) {
        val session = authenticatedSession() ?: return
        val target = uiState.value.files.renameTarget ?: return
        val validation = validateNodeName(rawName, target.name)
        if (!validation.isValid) {
            _uiState.update { state ->
                state.copy(files = state.files.copy(renameError = validation.errorMessage))
            }
            return
        }
        if (uiState.value.files.renameSubmitting) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    files = state.files.copy(
                        renameSubmitting = true,
                        renameError = null,
                    ),
                )
            }

            try {
                val renamedNode = repository.renameNode(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    nodeId = target.id,
                    name = validation.normalizedName,
                )
                if (!session.isCurrent()) return@launch

                _uiState.update { state ->
                    state.copy(
                        home = state.home.copy(
                            recentNodes = state.home.recentNodes.replaceNode(renamedNode),
                        ),
                        files = state.files.copy(
                            items = state.files.items.replaceNode(renamedNode),
                            breadcrumbs = state.files.breadcrumbs.map { crumb ->
                                if (crumb.id == renamedNode.id) crumb.copy(label = renamedNode.name) else crumb
                            },
                            selectedNodeIds = emptySet(),
                            renameTarget = null,
                            renameSubmitting = false,
                            renameError = null,
                        ),
                    )
                }
                fileDirectoryCache.clear()
                emitMessage("已重命名为：${renamedNode.name}")
                refreshHome(forceLoading = false)
                refreshFiles(forceLoading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error is ApiException && error.status == 401) {
                    handleError(error)
                    return@launch
                }
                if (session.isCurrent()) {
                    _uiState.update { state ->
                        state.copy(
                            files = state.files.copy(
                                renameSubmitting = false,
                                renameError = error.readableMessage(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun revealNodeInFiles(node: StorageNode) {
        val currentFiles = uiState.value.files
        val targetFolderId = node.parentId
        val sameFolder = currentFiles.currentFolderId == targetFolderId
        if (!sameFolder) {
            rememberCurrentDirectorySnapshot()
        }

        val cachedItems = if (sameFolder && currentFiles.canPopulateDirectoryCache()) {
            currentFiles.items
        } else {
            fileDirectoryCache[targetFolderId]
        }
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
                    keyword = "",
                    submittedKeyword = "",
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

        if (!sameFolder || cachedItems == null) {
            refreshFiles(forceLoading = false)
        }
    }

    fun openFolderFromAssistant(folderId: Long?, folderName: String) {
        val session = authenticatedSession() ?: return
        val files = uiState.value.files
        val sameFolder = files.currentFolderId == folderId && files.category == null
        if (!sameFolder) {
            rememberCurrentDirectorySnapshot()
        }
        val cachedItems = fileDirectoryCache[folderId]
        _uiState.update { state ->
            state.copy(
                selectedTab = AppTab.FILES,
                files = state.files.copy(
                    currentFolderId = folderId,
                    breadcrumbs = if (folderId == null) {
                        defaultBreadCrumbs
                    } else {
                        defaultBreadCrumbs + FolderCrumb(id = folderId, label = folderName)
                    },
                    keyword = "",
                    submittedKeyword = "",
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

        if (folderId == null) {
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.fetchFolders(session.baseUrl, session.token)
            }.onSuccess { folders ->
                _uiState.update { state ->
                    if (state.files.currentFolderId == folderId) {
                        state.copy(
                            files = state.files.copy(
                                breadcrumbs = resolveFolderBreadcrumbs(folders, folderId, folderName),
                            ),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun openParentFolderFromAssistant() {
        val breadcrumbs = uiState.value.files.breadcrumbs
        val target = breadcrumbs.getOrNull((breadcrumbs.lastIndex - 1).coerceAtLeast(0))
            ?: defaultBreadCrumbs.first()
        openFolderFromAssistant(target.id, target.label)
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
                transferJobs.values.forEach { job -> job.cancel() }
                transferJobs.clear()
                transferHistoryCoordinator.clearActive()
                nextTransferId = 1L
                cancelManualRefreshLoading()
                cancelCurrentUserSync()
                cancelExplorerRefreshes()
                incomingShareRequestGeneration += 1L
                sessionStore.clearToken(session.baseUrl)
                fileDirectoryCache.clear()
                clearPreviewArtifacts()
                _uiState.value = AppUiState(
                    isBooting = false,
                    baseUrl = session.baseUrl,
                )
                emitMessage(response.message.ifBlank { "密码修改成功，请重新登录。" })
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
                    keyword = "",
                    submittedKeyword = "",
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
                    keyword = "",
                    submittedKeyword = "",
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

    fun createFolder(
        folderName: String,
        onSuccess: () -> Unit = {},
    ) {
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
                onSuccess()
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
        uploadDocumentsToFolder(uris, uiState.value.files.currentFolderId)
    }

    internal fun createFolderThenUploadDocuments(
        uris: List<Uri>,
        parentId: Long?,
        folderName: String,
    ) {
        uploadLocalSelections(
            selections = uris.map { uri -> LocalUploadSelection(uri, LocalUploadSelectionKind.FILE) },
            parentId = parentId,
            createFolderName = folderName,
        )
    }

    internal fun uploadDocumentsToFolder(
        uris: List<Uri>,
        parentId: Long?,
        locationLabelOverride: String? = null,
    ) {
        uploadLocalSelections(
            selections = uris.map { uri -> LocalUploadSelection(uri, LocalUploadSelectionKind.FILE) },
            parentId = parentId,
            locationLabelOverride = locationLabelOverride,
        )
    }

    internal fun uploadDocumentTree(treeUri: Uri, parentId: Long?) {
        uploadLocalSelections(
            selections = listOf(LocalUploadSelection(treeUri, LocalUploadSelectionKind.FOLDER)),
            parentId = parentId,
        )
    }

    internal fun uploadLocalSelections(
        selections: List<LocalUploadSelection>,
        parentId: Long?,
        createFolderName: String? = null,
        locationLabelOverride: String? = null,
        onCompleted: ((OperationOutcome) -> Unit)? = null,
    ) {
        val session = authenticatedSession()
        if (session == null) {
            onCompleted?.invoke(OperationOutcome.failed("登录状态已失效，请重新登录后再上传。"))
            return
        }
        val uniqueSelections = selections.distinctBy { selection -> selection.kind to selection.uri }
        if (uniqueSelections.isEmpty()) {
            onCompleted?.invoke(OperationOutcome.failed("没有可上传的文件。"))
            return
        }
        if (uniqueSelections.count { selection -> selection.kind == LocalUploadSelectionKind.FILE } > MAX_UPLOAD_BATCH_FILES) {
            val message = "单次最多上传 $MAX_UPLOAD_BATCH_FILES 个文件。"
            reportOperationOutcome(OperationOutcome.failed(message), onCompleted)
            return
        }
        val trimmedCreateFolderName = createFolderName?.trim()
        if (createFolderName != null && trimmedCreateFolderName.isNullOrBlank()) {
            val message = "请输入文件夹名称。"
            reportOperationOutcome(OperationOutcome.failed(message), onCompleted)
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    files = state.files.copy(
                        isUploading = true,
                        isCreatingFolder = trimmedCreateFolderName != null,
                    ),
                    transferPanelOpen = false,
                    transferPanelTab = TransferPanelTab.UPLOADS,
                )
            }

            val summary = UploadBatchSummary()
            runCatching {
                val directSelections = uniqueSelections
                    .filter { selection -> selection.kind == LocalUploadSelectionKind.FILE }
                val folderPlans = withContext(Dispatchers.IO) {
                    uniqueSelections
                        .filter { selection -> selection.kind == LocalUploadSelectionKind.FOLDER }
                        .map { selection -> localFolderUploadPlanner.build(appContext, selection.uri) }
                }
                val requestedFileCount = directSelections.size + folderPlans.sumOf { plan -> plan.files.size }
                if (requestedFileCount > MAX_UPLOAD_BATCH_FILES) {
                    throw ApiException("单次最多上传 $MAX_UPLOAD_BATCH_FILES 个文件。", 400)
                }

                var targetParentId = parentId
                var uploadLocationLabel = locationLabelOverride
                    ?.takeIf { it.isNotBlank() }
                    ?: resolveUploadLocationLabel(parentId)

                if (trimmedCreateFolderName != null) {
                    val createdFolder = repository.createFolder(
                        baseUrl = session.baseUrl,
                        token = session.token,
                        parentId = parentId,
                        folderName = trimmedCreateFolderName,
                    )
                    targetParentId = createdFolder.id
                    uploadLocationLabel = trimmedCreateFolderName
                    summary.createdFolderCount += 1
                    _uiState.update { state ->
                        state.copy(files = state.files.copy(isCreatingFolder = false))
                    }
                }

                val directFiles = directSelections.map { selection ->
                        val descriptor = runCatching {
                            repository.describeUploadAsset(appContext, selection.uri)
                        }.getOrNull()
                        ResolvedLocalUploadFile(
                            uri = selection.uri,
                            name = descriptor?.fileName ?: selection.uri.lastPathSegment ?: "upload.bin",
                            sizeBytes = descriptor?.sizeBytes,
                            parentId = targetParentId,
                            locationLabel = uploadLocationLabel,
                        )
                    }
                uploadFilesSequentially(session, directFiles, summary)

                folderPlans.forEach { folderPlan ->
                        uploadFolderPlan(
                            session = session,
                            plan = folderPlan,
                            parentId = targetParentId,
                            parentLocationLabel = uploadLocationLabel,
                            summary = summary,
                        )
                }
            }.onFailure { error ->
                if (summary.firstError == null) {
                    summary.firstError = error
                }
            }

            _uiState.update { state ->
                state.copy(
                    files = state.files.copy(
                        isUploading = false,
                        isCreatingFolder = false,
                    ),
                )
            }

            summary.firstError?.let { error ->
                handleError(error, emitUserMessage = false)
            }
            reportOperationOutcome(
                outcome = summary.toOutcome(summary.firstError?.readableMessage()),
                onCompleted = onCompleted,
            )
            if (summary.changedStorage) {
                refreshAfterMutation(refreshFiles = true, refreshTrash = false)
            }
        }
    }

    private suspend fun uploadFolderPlan(
        session: AuthSession,
        plan: LocalFolderUploadPlan,
        parentId: Long?,
        parentLocationLabel: String,
        summary: UploadBatchSummary,
    ) {
        if (summary.totalFiles + plan.files.size > MAX_UPLOAD_BATCH_FILES) {
            throw ApiException("单次最多上传 $MAX_UPLOAD_BATCH_FILES 个文件。", 400)
        }
        summary.totalFiles += plan.files.size
        runCatching {
            val rootFolder = repository.createFolder(
                baseUrl = session.baseUrl,
                token = session.token,
                parentId = parentId,
                folderName = plan.rootName,
            )
            summary.createdFolderCount += 1
            val folderIds = mutableMapOf<List<String>, Long>(emptyList<String>() to rootFolder.id)

            plan.directories
                .sortedWith(compareBy<List<String>> { path -> path.size }.thenBy { path -> path.joinToString("/") })
                .forEach { relativePath ->
                    val directory = repository.createFolder(
                        baseUrl = session.baseUrl,
                        token = session.token,
                        parentId = folderIds[relativePath.dropLast(1)]
                            ?: throw ApiException("无法还原本地文件夹层级。", 400),
                        folderName = relativePath.last(),
                    )
                    folderIds[relativePath] = directory.id
                    summary.createdFolderCount += 1
                }

            val resolvedFiles = plan.files.map { file ->
                ResolvedLocalUploadFile(
                    uri = file.uri,
                    name = file.name,
                    sizeBytes = file.sizeBytes,
                    parentId = folderIds[file.relativeDirectory],
                    locationLabel = listOf(
                        parentLocationLabel,
                        plan.rootName,
                        file.relativeDirectory.joinToString("/"),
                    ).filter { segment -> segment.isNotBlank() }.joinToString("/"),
                )
            }
            uploadFilesSequentially(session, resolvedFiles, summary, countTotal = false)
        }.onFailure { error ->
            if (summary.firstError == null) {
                summary.firstError = error
            }
        }
    }

    private suspend fun uploadFilesSequentially(
        session: AuthSession,
        files: List<ResolvedLocalUploadFile>,
        summary: UploadBatchSummary,
        countTotal: Boolean = true,
    ) {
        if (countTotal) {
            summary.totalFiles += files.size
        }
        val queuedUploads = files.map { file ->
            QueuedLocalUpload(
                file = file,
                taskId = appendTransfer(
                    TransferTask(
                        id = allocateTransferId(),
                        kind = TransferKind.UPLOAD,
                        itemKind = TransferItemKind.FILE,
                        title = file.name,
                        status = TransferStatus.QUEUED,
                        sourceUri = file.uri,
                        totalBytes = file.sizeBytes,
                        locationLabel = file.locationLabel,
                    ),
                ),
            )
        }

        queuedUploads.forEach { queued ->
            updateTransfer(queued.taskId) { task -> task.copy(status = TransferStatus.PREPARING) }
            val taskJob = viewModelScope.launch {
                runCatching {
                    repository.uploadFile(
                        context = appContext,
                        baseUrl = session.baseUrl,
                        token = session.token,
                        parentId = queued.file.parentId,
                        uri = queued.file.uri,
                        onProgress = { progress -> updateTransferProgress(queued.taskId, progress) },
                    )
                }.onSuccess {
                    summary.successCount += 1
                    updateTransfer(queued.taskId) { task ->
                        task.copy(
                            status = TransferStatus.COMPLETED,
                            transferredBytes = task.totalBytes ?: task.transferredBytes,
                            progressPercent = 100,
                            errorMessage = null,
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) {
                        updateTransfer(queued.taskId) { task -> task.copy(status = TransferStatus.CANCELED) }
                    } else {
                        if (summary.firstError == null) {
                            summary.firstError = error
                        }
                        updateTransfer(queued.taskId) { task ->
                            task.copy(
                                status = TransferStatus.FAILED,
                                errorMessage = error.readableMessage(),
                            )
                        }
                    }
                }
            }
            transferJobs[queued.taskId] = taskJob
            taskJob.invokeOnCompletion { transferJobs.remove(queued.taskId) }
            taskJob.join()
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

    suspend fun checkClipboardForShareLink() {
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
        ensureClipboardReceiptLoaded()
        val incomingShare = uiState.value.incomingShare

        if (
            incomingShare.activeShareCode == shareCode ||
            incomingShare.prompt?.shareCode == shareCode ||
            !ClipboardSharePolicy.shouldPrompt(
                clipLabel = clip.description.label?.toString(),
                shareCode = shareCode,
                fingerprint = clipboardFingerprint,
                lastHandledShareCode = lastHandledClipboardShareCode,
                lastHandledFingerprint = lastHandledClipboardFingerprint,
                lastHandledAtMillis = lastHandledClipboardAtMillis,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            return
        }

        rememberHandledClipboardShare(shareCode, clipboardFingerprint)
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

    private suspend fun ensureClipboardReceiptLoaded() {
        if (clipboardReceiptLoaded) return

        clipboardReceiptMutex.withLock {
            if (clipboardReceiptLoaded) return@withLock

            val receipt = runCatching {
                sessionStore.clipboardShareReceiptFlow().first()
            }.getOrNull()
            if (receipt != null && receipt.handledAtMillis >= lastHandledClipboardAtMillis) {
                lastHandledClipboardShareCode = receipt.shareCode
                lastHandledClipboardFingerprint = receipt.fingerprint
                lastHandledClipboardAtMillis = receipt.handledAtMillis
            }
            clipboardReceiptLoaded = true
        }
    }

    private fun rememberHandledClipboardShare(shareCode: String, fingerprint: String) {
        if (shareCode.isBlank() || fingerprint.isBlank()) return

        val receipt = ClipboardShareReceipt(
            shareCode = shareCode,
            fingerprint = fingerprint,
            handledAtMillis = System.currentTimeMillis(),
        )
        lastHandledClipboardShareCode = receipt.shareCode
        lastHandledClipboardFingerprint = receipt.fingerprint
        lastHandledClipboardAtMillis = receipt.handledAtMillis
        clipboardReceiptLoaded = true
        viewModelScope.launch {
            runCatching { sessionStore.saveClipboardShareReceipt(receipt) }
        }
    }

    private fun clipboardFingerprint(clip: ClipData, texts: List<String>): String =
        listOf(
            clip.description.timestamp.toString(),
            clip.description.label?.toString().orEmpty(),
            texts.joinToString(separator = "\u001F").take(4096),
        ).joinToString(separator = "\u001E")

    private fun clipboardItemTexts(item: ClipData.Item): List<String> =
        listOfNotNull(
            item.text?.toString()?.takeIf { it.isNotBlank() },
            item.htmlText?.takeIf { it.isNotBlank() },
            item.uri?.toString()?.takeIf { it.isNotBlank() },
            item.intent?.dataString?.takeIf { it.isNotBlank() },
            item.coerceToText(appContext)?.toString()?.takeIf { it.isNotBlank() },
        ).distinct()

    fun dismissIncomingSharePrompt() {
        val prompt = uiState.value.incomingShare.prompt
        val shareCode = prompt?.shareCode
        if (!shareCode.isNullOrBlank()) {
            prompt.clipboardFingerprint?.let { fingerprint ->
                rememberHandledClipboardShare(shareCode, fingerprint)
            }
        }

        _uiState.update { state ->
            state.copy(incomingShare = state.incomingShare.copy(prompt = null))
        }
    }

    fun confirmIncomingSharePrompt() {
        val prompt = uiState.value.incomingShare.prompt ?: return
        prompt.clipboardFingerprint?.let { fingerprint ->
            rememberHandledClipboardShare(prompt.shareCode, fingerprint)
        }
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
        incomingShareRequestGeneration += 1L
        val incomingShare = uiState.value.incomingShare
        if (incomingShare.source == IncomingShareSource.CLIPBOARD && !incomingShare.activeShareCode.isNullOrBlank()) {
            incomingShare.clipboardFingerprint?.let { fingerprint ->
                rememberHandledClipboardShare(incomingShare.activeShareCode, fingerprint)
            }
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
        val baseUrl = uiState.value.baseUrl
        val shareCode = uiState.value.incomingShare.activeShareCode ?: return
        val generation = ++incomingShareRequestGeneration
        val trimmedPassword = password.trim()
        if (trimmedPassword.isBlank()) {
            _uiState.update { state ->
                if (state.incomingShare.activeShareCode == shareCode) {
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            passwordError = "请输入提取码。",
                        ),
                    )
                } else {
                    state
                }
            }
            return
        }

        viewModelScope.launch {
            if (generation != incomingShareRequestGeneration) {
                return@launch
            }
            _uiState.update { state ->
                if (state.incomingShare.activeShareCode == shareCode) {
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            passwordChecking = true,
                            passwordError = null,
                            error = null,
                        ),
                    )
                } else {
                    state
                }
            }

            runCatching {
                repository.verifySharePassword(
                    baseUrl = baseUrl,
                    shareCode = shareCode,
                    password = trimmedPassword,
                )
            }.onSuccess { response ->
                if (generation != incomingShareRequestGeneration) {
                    return@onSuccess
                }
                val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
                if (accessToken == null) {
                    _uiState.update { state ->
                        if (state.incomingShare.activeShareCode == shareCode) {
                            state.copy(
                                incomingShare = state.incomingShare.copy(
                                    passwordChecking = false,
                                    passwordError = "提取码校验未返回访问凭证，请重试。",
                                ),
                            )
                        } else {
                            state
                        }
                    }
                    return@onSuccess
                }

                _uiState.update { state ->
                    if (state.incomingShare.activeShareCode == shareCode) {
                        state.copy(
                            incomingShare = state.incomingShare.copy(
                                passwordChecking = false,
                                shareAccessToken = accessToken,
                                passwordError = null,
                            ),
                        )
                    } else {
                        state
                    }
                }
                if (uiState.value.incomingShare.activeShareCode == shareCode) {
                    emitMessage("提取码校验通过。")
                    refreshIncomingShareDetailIfReady()
                }
            }.onFailure { error ->
                if (generation != incomingShareRequestGeneration) {
                    return@onFailure
                }
                _uiState.update { state ->
                    if (state.incomingShare.activeShareCode == shareCode) {
                        state.copy(
                            incomingShare = state.incomingShare.copy(
                                passwordChecking = false,
                                passwordError = error.readableMessage(),
                            ),
                        )
                    } else {
                        state
                    }
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
                            submittedKeyword = "",
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
                refreshAll(syncUser = true)
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

    fun checkForAppUpdateManually() {
        if (uiState.value.versionUpdate.checking) return
        checkForAppUpdate(uiState.value.baseUrl, requestedByUser = true)
    }

    fun openAppUpdateDownload() {
        val appUpdate = uiState.value.appUpdate ?: return
        openAppUpdateDownloadUrl(appUpdate.downloadUrl, dismissPromptAfterOpen = true)
    }

    fun openVersionUpdateDownload() {
        val update = uiState.value.versionUpdate
        if (!update.updateAvailable) {
            checkForAppUpdateManually()
            return
        }
        val downloadUrl = update.downloadUrl ?: return
        openAppUpdateDownloadUrl(downloadUrl, dismissPromptAfterOpen = false)
    }

    private fun openAppUpdateDownloadUrl(downloadUrl: String, dismissPromptAfterOpen: Boolean) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            appContext.startActivity(intent)
        }.onSuccess {
            if (dismissPromptAfterOpen) {
                dismissAppUpdate()
            }
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
        if (!appContext.canWriteTransferDestination(destinationUri)) {
            updateTransfer(taskId) { current ->
                current.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = "原保存位置的写入权限已失效，请重新选择保存位置。",
                )
            }
            emitMessage("原保存位置已不可用，请从文件列表重新选择保存位置。")
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
        val session = authenticatedSession()
        val baseUrl = session?.baseUrl ?: uiState.value.baseUrl
        viewModelScope.launch {
            if (session != null) {
                runCatching {
                    repository.logout(
                        baseUrl = session.baseUrl,
                        token = session.token,
                        refreshToken = session.refreshToken,
                    )
                }
            }
            clearLocalAuthenticatedState(baseUrl)
            emitMessage("你已退出登录。")
        }
    }

    private fun expireCurrentSession(emitUserMessage: Boolean) {
        val baseUrl = uiState.value.baseUrl
        viewModelScope.launch {
            clearExpiredSession(baseUrl, if (emitUserMessage) MOBILE_SESSION_EXPIRED_MESSAGE else null)
        }
    }

    private suspend fun clearExpiredSession(baseUrl: String, message: String?) {
        clearLocalAuthenticatedState(baseUrl)
        if (!message.isNullOrBlank()) {
            emitMessage(message)
        }
    }

    private suspend fun clearLocalAuthenticatedState(baseUrl: String) {
        transferJobs.values.forEach { job -> job.cancel() }
        transferJobs.clear()
        transferHistoryCoordinator.clearActive()
        nextTransferId = 1L
        cancelManualRefreshLoading()
        cancelCurrentUserSync()
        cancelExplorerRefreshes()
        incomingShareRequestGeneration += 1L
        sessionStore.clearToken(baseUrl)
        fileDirectoryCache.clear()
        clearPreviewArtifacts()
        _uiState.value = AppUiState(
            isBooting = false,
            baseUrl = baseUrl,
        )
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val session = sessionStore.sessionFlow(defaultBaseUrl).first()
            _uiState.update { state -> state.copy(baseUrl = session.baseUrl) }

            if (session.token.isNullOrBlank()) {
                nextTransferId = 1L
                awaitMinimumBootSplashDuration()
                _uiState.update { state -> state.copy(isBooting = false) }
                checkForAppUpdate(session.baseUrl)
                return@launch
            }
            if (session.refreshToken.isNullOrBlank()) {
                awaitMinimumBootSplashDuration()
                clearExpiredSession(session.baseUrl, MOBILE_SESSION_INCOMPLETE_MESSAGE)
                checkForAppUpdate(session.baseUrl)
                return@launch
            }

            runCatching {
                val refreshedSession = repository.refreshToken(session.baseUrl, session.token, session.refreshToken)
                sessionStore.saveSession(refreshedSession.token, refreshedSession.refreshToken, session.baseUrl)
                refreshedSession
            }.onSuccess { refreshedSession ->
                val restoredTransfers = activateTransferHistory(session.baseUrl, refreshedSession.user.id)
                awaitMinimumBootSplashDuration()
                fileDirectoryCache.clear()
                clearPreviewArtifacts()
                _uiState.update { state ->
                    state.copy(
                        isBooting = false,
                        authToken = refreshedSession.token,
                        refreshToken = refreshedSession.refreshToken,
                        currentUser = refreshedSession.user,
                        baseUrl = session.baseUrl,
                        transfers = restoredTransfers,
                    )
                }
                refreshAll(syncUser = false)
                checkForAppUpdate(session.baseUrl)
                refreshIncomingShareDetailIfReady()
            }.onFailure { error ->
                awaitMinimumBootSplashDuration()
                clearExpiredSession(session.baseUrl, error.readableMessage())
                checkForAppUpdate(session.baseUrl)
            }
        }
    }

    private suspend fun awaitMinimumBootSplashDuration() {
        val elapsedMillis = SystemClock.elapsedRealtime() - bootStartedAtMillis
        val remainingMillis = MIN_BOOT_SPLASH_MILLIS - elapsedMillis
        if (remainingMillis > 0L) {
            delay(remainingMillis)
        }
    }

    private fun refreshAll(syncUser: Boolean) {
        if (syncUser) {
            syncCurrentUser()
        }
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
        val generation = ++currentUserSyncGeneration
        val startedAtMillis = SystemClock.elapsedRealtime()

        currentUserSyncJob?.cancel()
        _uiState.update { state -> state.copy(isRefreshingUser = true) }
        currentUserSyncJob = viewModelScope.launch {
            try {
                val currentUser = repository.fetchCurrentUser(session.baseUrl, session.token)
                if (generation == currentUserSyncGeneration) {
                    _uiState.update { state -> state.copy(currentUser = currentUser) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == currentUserSyncGeneration) {
                    handleError(error, emitUserMessage = false)
                }
            } finally {
                if (generation == currentUserSyncGeneration) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAtMillis
                    delay((MIN_GLOBAL_LOADING_MILLIS - elapsed).coerceAtLeast(0L))
                    if (generation == currentUserSyncGeneration) {
                        _uiState.update { state -> state.copy(isRefreshingUser = false) }
                    }
                }
            }
        }
    }

    private fun keepManualRefreshLoadingVisible() {
        val generation = ++manualRefreshGeneration
        manualRefreshJob?.cancel()
        _uiState.update { state -> state.copy(isManualRefreshing = true) }
        manualRefreshJob = viewModelScope.launch {
            delay(MIN_GLOBAL_LOADING_MILLIS)
            if (generation == manualRefreshGeneration) {
                _uiState.update { state -> state.copy(isManualRefreshing = false) }
            }
        }
    }

    private fun cancelManualRefreshLoading() {
        manualRefreshGeneration += 1L
        manualRefreshJob?.cancel()
        manualRefreshJob = null
    }

    private fun cancelCurrentUserSync() {
        currentUserSyncGeneration += 1L
        currentUserSyncJob?.cancel()
        currentUserSyncJob = null
    }

    private fun cancelExplorerRefreshes() {
        fileRefreshGeneration += 1L
        fileRefreshJob?.cancel()
        fileRefreshJob = null
        trashRefreshGeneration += 1L
        trashRefreshJob?.cancel()
        trashRefreshJob = null
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
        val generation = ++fileRefreshGeneration
        fileRefreshJob?.cancel()
        fileRefreshJob = viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { state ->
                    state.copy(files = state.files.copy(loading = true, error = null))
                }
            }

            try {
                val page = repository.fetchStorageNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    parentId = if (files.category != null || files.searchScope == FileSearchScope.GLOBAL) {
                        null
                    } else {
                        files.currentFolderId
                    },
                    keyword = files.submittedKeyword,
                    filter = if (files.category == null) files.filter else StorageNodeFilter.FILE,
                    recursive = files.category != null || (files.searchScope == FileSearchScope.GLOBAL && files.submittedKeyword.isNotBlank()),
                    category = files.category,
                )
                if (generation != fileRefreshGeneration || !session.isCurrent() || uiState.value.files.fileQueryIdentity() != files.fileQueryIdentity()) {
                    return@launch
                }
                val visibleIds = page.items.mapTo(hashSetOf()) { it.id }
                if (files.canPopulateDirectoryCache()) {
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == fileRefreshGeneration && session.isCurrent() && uiState.value.files.fileQueryIdentity() == files.fileQueryIdentity()) {
                    _uiState.update { state ->
                        state.copy(files = state.files.copy(loading = false, error = error.readableMessage()))
                    }
                    handleError(error, emitUserMessage = false)
                }
            }
        }
    }

    private fun refreshTrash(forceLoading: Boolean) {
        val session = authenticatedSession() ?: return
        val trash = uiState.value.trash
        val generation = ++trashRefreshGeneration
        trashRefreshJob?.cancel()
        trashRefreshJob = viewModelScope.launch {
            if (forceLoading) {
                _uiState.update { state ->
                    state.copy(trash = state.trash.copy(loading = true, error = null))
                }
            }

            try {
                val page = repository.fetchTrashNodes(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    keyword = trash.submittedKeyword,
                    filter = trash.filter,
                )
                if (generation != trashRefreshGeneration || !session.isCurrent() || uiState.value.trash.trashQueryIdentity() != trash.trashQueryIdentity()) {
                    return@launch
                }
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == trashRefreshGeneration && session.isCurrent() && uiState.value.trash.trashQueryIdentity() == trash.trashQueryIdentity()) {
                    _uiState.update { state ->
                        state.copy(trash = state.trash.copy(loading = false, error = error.readableMessage()))
                    }
                    handleError(error, emitUserMessage = false)
                }
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

    private fun openShareFromSearchInput(input: String): Boolean {
        val shareCode = ShareLinkParser.findShareCodeInText(input, uiState.value.baseUrl) ?: return false
        openIncomingShare(
            shareCode = shareCode,
            source = IncomingShareSource.SEARCH_INPUT,
        )
        return true
    }

    private fun openIncomingShare(
        shareCode: String,
        source: IncomingShareSource,
        clipboardFingerprint: String? = null,
    ) {
        val baseUrl = uiState.value.baseUrl
        val generation = ++incomingShareRequestGeneration

        viewModelScope.launch {
            if (generation != incomingShareRequestGeneration) {
                return@launch
            }
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
                if (generation != incomingShareRequestGeneration) {
                    return@onSuccess
                }
                _uiState.update { state ->
                    if (state.incomingShare.activeShareCode == shareCode) {
                        state.copy(
                            incomingShare = state.incomingShare.copy(
                                statusLoading = false,
                                status = status,
                                error = if (status.available) null else shareUnavailableMessage(status),
                            ),
                        )
                    } else {
                        state
                    }
                }
                if (uiState.value.incomingShare.activeShareCode == shareCode) {
                    refreshIncomingShareDetailIfReady()
                }
            }.onFailure { error ->
                if (generation != incomingShareRequestGeneration) {
                    return@onFailure
                }
                _uiState.update { state ->
                    if (state.incomingShare.activeShareCode == shareCode) {
                        state.copy(
                            incomingShare = state.incomingShare.copy(
                                statusLoading = false,
                                error = error.readableMessage(),
                            ),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun refreshIncomingShareDetailIfReady() {
        val incomingShare = uiState.value.incomingShare
        val shareCode = incomingShare.activeShareCode ?: return
        val status = incomingShare.status ?: return
        val generation = incomingShareRequestGeneration

        if (!status.available || incomingShare.detailLoading) {
            return
        }

        if (status.requiresPassword && incomingShare.shareAccessToken.isNullOrBlank()) {
            return
        }

        val session = authenticatedSession() ?: return

        viewModelScope.launch {
            if (generation != incomingShareRequestGeneration) {
                return@launch
            }
            _uiState.update { state ->
                if (state.incomingShare.activeShareCode == shareCode) {
                    state.copy(
                        incomingShare = state.incomingShare.copy(
                            detailLoading = true,
                            error = null,
                        ),
                    )
                } else {
                    state
                }
            }

            runCatching {
                repository.fetchShareDetail(
                    baseUrl = session.baseUrl,
                    token = session.token,
                    shareCode = shareCode,
                    shareAccessToken = incomingShare.shareAccessToken,
                )
            }.onSuccess { detail ->
                if (generation != incomingShareRequestGeneration) {
                    return@onSuccess
                }
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
                if (generation != incomingShareRequestGeneration) {
                    return@onFailure
                }
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

    private fun allocateTransferId(): Long {
        val tasks = uiState.value.transfers
        val allocatedId = nextTransferId.takeIf { candidate ->
            candidate > 0L && tasks.none { task -> task.id == candidate }
        } ?: tasks.nextTransferId()
        nextTransferId = if (allocatedId == Long.MAX_VALUE) 1L else allocatedId + 1L
        return allocatedId
    }

    private fun appendTransfer(task: TransferTask): Long {
        _uiState.update { state ->
            state.copy(
                transfers = (listOf(task) + state.transfers).take(MAX_TRANSFER_HISTORY),
            )
        }
        transferHistoryCoordinator.persist(task, immediate = true)
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
        val updatedTask = uiState.value.transfers.firstOrNull { task -> task.id == taskId }
        if (updatedTask != null) {
            transferHistoryCoordinator.persist(
                task = updatedTask,
                immediate = updatedTask.status.isTerminalTransferStatus(),
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

    private suspend fun activateTransferHistory(baseUrl: String, userId: Long): List<TransferTask> {
        val restoredTasks = transferHistoryCoordinator.activate(baseUrl, userId)
        nextTransferId = restoredTasks.nextTransferId()
        return restoredTasks
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
        return AuthSession(token = token, refreshToken = state.refreshToken, baseUrl = state.baseUrl)
    }

    private fun AuthSession.isCurrent(): Boolean {
        val state = uiState.value
        return state.authToken == token && state.baseUrl == baseUrl
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
        if (!files.canPopulateDirectoryCache()) {
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
        fallbackLabel: String = "目标目录",
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
            return defaultBreadCrumbs + FolderCrumb(id = targetFolderId, label = fallbackLabel)
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

    private fun checkForAppUpdate(baseUrl: String, requestedByUser: Boolean = false) {
        val currentVersionName = resolveInstalledVersionName()
        if (!BuildConfig.APP_UPDATE_ENABLED) {
            _uiState.update { state ->
                state.copy(
                    appUpdate = if (requestedByUser) state.appUpdate else null,
                    versionUpdate = state.versionUpdate.copy(
                        currentVersionName = currentVersionName,
                        checking = false,
                        updateAvailable = false,
                        downloadUrl = null,
                    ),
                )
            }
            if (requestedByUser) {
                emitMessage("当前版本未启用在线更新。")
            }
            return
        }

        if (requestedByUser) {
            _uiState.update { state ->
                state.copy(
                    versionUpdate = state.versionUpdate.copy(
                        currentVersionName = currentVersionName,
                        checking = true,
                    ),
                )
            }
        }

        appUpdateCheckJob?.cancel()
        appUpdateCheckJob = viewModelScope.launch {
            runCatching {
                repository.fetchLatestAppVersion(baseUrl)
            }.onSuccess { versionInfo ->
                val latestVersionName = versionInfo.versionName?.trim().orEmpty()
                val releaseNotes = versionInfo.releaseNotes?.trim().orEmpty()
                val downloadUrl = AppUpdatePolicy.resolveDownloadUrl(baseUrl, versionInfo.downloadUrl)
                val newerVersionAvailable = versionInfo.available &&
                    latestVersionName.isNotBlank() &&
                    compareVersionNames(currentVersionName, latestVersionName) < 0
                val updateAvailable = newerVersionAvailable && downloadUrl != null

                _uiState.update { state ->
                    state.copy(
                        appUpdate = when {
                            requestedByUser -> state.appUpdate
                            !updateAvailable || dismissedUpdateVersionName == latestVersionName -> null
                            else -> AppUpdateState(
                                currentVersionName = currentVersionName,
                                latestVersionName = latestVersionName,
                                releaseNotes = releaseNotes,
                                downloadUrl = requireNotNull(downloadUrl),
                            )
                        },
                        versionUpdate = VersionUpdateUiState(
                            currentVersionName = currentVersionName,
                            latestVersionName = latestVersionName.ifBlank { null },
                            releaseNotes = releaseNotes,
                            downloadUrl = downloadUrl,
                            checking = false,
                            updateAvailable = updateAvailable,
                        ),
                    )
                }

                if (requestedByUser) {
                    when {
                        newerVersionAvailable && downloadUrl == null -> emitMessage("更新地址无效，请联系管理员。")
                        updateAvailable -> emitMessage("发现新版本 $latestVersionName。")
                        else -> emitMessage("当前已是最新版本。")
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update { state ->
                    state.copy(
                        versionUpdate = state.versionUpdate.copy(checking = false),
                    )
                }
                if (requestedByUser) {
                    emitMessage(error.message ?: "检查更新失败，请稍后再试。")
                }
            }
        }
    }

    private fun resolveInstalledVersionName(): String =
        BuildConfig.VERSION_NAME
            .trim()
            .ifBlank { "0.0.0" }

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

    private fun reportOperationOutcome(
        outcome: OperationOutcome,
        onCompleted: ((OperationOutcome) -> Unit)?,
    ) {
        if (onCompleted == null) {
            emitMessage(outcome.message)
        } else {
            onCompleted(outcome)
        }
    }

    private fun handleError(error: Throwable, emitUserMessage: Boolean = true) {
        val message = error.readableMessage()

        if (error.isMobileAuthExpired()) {
            expireCurrentSession(emitUserMessage)
            return
        }

        if (emitUserMessage) {
            emitMessage(message)
        }
    }

    override fun onCleared() {
        cancelExplorerRefreshes()
        transferJobs.values.forEach { job -> job.cancel() }
        transferJobs.clear()
        transferHistoryCoordinator.close()
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
                        transferHistoryPersistence = TransferHistoryStore.create(context.applicationContext),
                        defaultBaseUrl = BuildConfig.DEFAULT_API_BASE_URL,
                        appContext = context.applicationContext,
                    ) as T
                }
            }
    }
}

private fun List<StorageNode>.replaceNode(updatedNode: StorageNode): List<StorageNode> =
    map { node -> if (node.id == updatedNode.id) updatedNode else node }

private fun TransferStatus.isTerminalTransferStatus(): Boolean =
    this == TransferStatus.COMPLETED ||
        this == TransferStatus.FAILED ||
        this == TransferStatus.CANCELED

private fun List<TransferTask>.nextTransferId(): Long {
    val usedIds = asSequence().map(TransferTask::id).filter { it > 0L }.toHashSet()
    var candidate = (usedIds.maxOrNull() ?: 0L).let { maximum ->
        if (maximum == Long.MAX_VALUE) 1L else maximum + 1L
    }
    while (candidate in usedIds) {
        candidate = if (candidate == Long.MAX_VALUE) 1L else candidate + 1L
    }
    return candidate
}

private fun Context.canWriteTransferDestination(uri: Uri): Boolean {
    if (!uri.scheme.equals("content", ignoreCase = true)) {
        return false
    }
    val hasPersistedPermission = contentResolver.persistedUriPermissions.any { permission ->
        permission.uri == uri && permission.isWritePermission
    }
    if (hasPersistedPermission) {
        return true
    }
    return checkUriPermission(
        uri,
        Process.myPid(),
        Process.myUid(),
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Throwable.readableMessage(): String =
    mobileReadableMessage()
