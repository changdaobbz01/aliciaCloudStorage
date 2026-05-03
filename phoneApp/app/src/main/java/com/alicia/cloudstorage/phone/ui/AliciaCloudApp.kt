package com.alicia.cloudstorage.phone.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alicia.cloudstorage.phone.data.AppTab
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.User
import com.alicia.cloudstorage.phone.data.UserRole
import com.alicia.cloudstorage.phone.data.isAdmin
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import java.util.Locale

@Composable
fun AliciaCloudApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    when {
        uiState.isBooting -> BootScreen()
        uiState.authToken.isNullOrBlank() -> LoginScreen(
            baseUrl = uiState.baseUrl,
            isSubmitting = uiState.isSubmittingLogin,
            onBaseUrlChange = viewModel::updateBaseUrl,
            onLogin = viewModel::login,
        )

        else -> MainShell(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun BootScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "正在连接 Alicia Cloud…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoginScreen(
    baseUrl: String,
    isSubmitting: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LoginBackdrop(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AliciaBadge(text = "Alicia Cloud")
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "欢迎登录移动云盘",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Android 首版先聚焦首页、文件浏览和账号管理，用更适合手机阅读的布局承接 web 端能力。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))

            AliciaSectionCard {
                AliciaInputField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "手机号",
                    placeholder = "请输入 11 位手机号",
                )

                AliciaInputField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "密码",
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = "请输入登录密码",
                )

                AliciaInputField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "后端地址",
                    placeholder = "http://43.132.237.15",
                )

                AliciaPrimaryButton(
                    label = "登录",
                    onClick = { onLogin(phoneNumber, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isSubmitting,
                    loading = isSubmitting,
                )

                Text(
                    text = "默认直接连接正式服务 http://43.132.237.15，如需切回本地开发再改成 10.0.2.2:8090。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    uiState: AppUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: MainViewModel,
) {
    val currentUser = uiState.currentUser ?: return
    val isTrashMode = uiState.selectedTab == AppTab.TRASH
    val visibleTab = when (uiState.selectedTab) {
        AppTab.HOME -> AppTab.HOME
        AppTab.FILES, AppTab.TRASH -> AppTab.FILES
        AppTab.TEAM -> AppTab.TEAM
        AppTab.ME -> AppTab.HOME
    }

    var createFolderOpen by rememberSaveable { mutableStateOf(false) }
    var createFolderName by rememberSaveable { mutableStateOf("") }
    var createUserOpen by rememberSaveable { mutableStateOf(false) }
    var accountSheetOpen by rememberSaveable { mutableStateOf(false) }
    var changePasswordOpen by rememberSaveable { mutableStateOf(false) }
    var quotaTargetUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resetPasswordTargetUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actionSheetNode by remember { mutableStateOf<StorageNode?>(null) }
    var trashConfirmNode by remember { mutableStateOf<StorageNode?>(null) }
    var permanentDeleteNode by remember { mutableStateOf<StorageNode?>(null) }
    var pendingDownloadNode by remember { mutableStateOf<StorageNode?>(null) }
    val avatarUrl = remember(uiState.baseUrl, currentUser.id, currentUser.avatarUrl) {
        resolveUserAvatarUrl(uiState.baseUrl, currentUser)
    }
    val quotaTargetUser = remember(uiState.team.users, quotaTargetUserId) {
        uiState.team.users.firstOrNull { it.id == quotaTargetUserId }
    }
    val resetPasswordTargetUser = remember(uiState.team.users, resetPasswordTargetUserId) {
        uiState.team.users.firstOrNull { it.id == resetPasswordTargetUserId }
    }
    val activeExplorer = if (isTrashMode) uiState.trash else uiState.files
    val onExplorerKeywordChange: (String) -> Unit = { value ->
        if (isTrashMode) {
            viewModel.updateTrashKeyword(value)
        } else {
            viewModel.updateFileKeyword(value)
        }
    }
    val onExplorerSearch: () -> Unit = {
        if (isTrashMode) {
            viewModel.submitTrashSearch()
        } else {
            viewModel.submitFileSearch()
        }
    }
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.uploadDocument(uri)
        }
    }
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(uri)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val targetNode = pendingDownloadNode
        pendingDownloadNode = null
        if (uri != null && targetNode != null) {
            viewModel.downloadFileToUri(targetNode, uri)
        }
    }

    val bottomItems = remember(currentUser.role, visibleTab) {
        buildList {
            add(
                AliciaBottomNavItem(
                    label = "首页",
                    icon = Icons.Rounded.Home,
                    selected = visibleTab == AppTab.HOME,
                    onClick = { viewModel.selectTab(AppTab.HOME) },
                ),
            )
            if (currentUser.isAdmin) {
                add(
                    AliciaBottomNavItem(
                        label = "账号管理",
                        icon = Icons.Rounded.ManageAccounts,
                        selected = visibleTab == AppTab.TEAM,
                        onClick = { viewModel.selectTab(AppTab.TEAM) },
                        prominent = true,
                    ),
                )
            }
            add(
                AliciaBottomNavItem(
                    label = "文件",
                    icon = Icons.Rounded.FolderOpen,
                    selected = visibleTab == AppTab.FILES,
                    onClick = { viewModel.selectTab(AppTab.FILES) },
                ),
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { AliciaBottomBar(items = bottomItems) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (uiState.selectedTab) {
            AppTab.HOME -> HomeScreen(
                paddingValues = innerPadding,
                currentUser = currentUser,
                avatarUrl = avatarUrl,
                home = uiState.home,
                fileKeyword = uiState.files.keyword,
                recentNodes = uiState.files.items.take(4),
                onFileKeywordChange = viewModel::updateFileKeyword,
                onSubmitSearch = {
                    viewModel.selectTab(AppTab.FILES)
                    viewModel.submitFileSearch()
                },
                onOpenFiles = { viewModel.selectTab(AppTab.FILES) },
                onOpenTrash = { viewModel.selectTab(AppTab.TRASH) },
                onOpenTeam = { viewModel.selectTab(AppTab.TEAM) },
                onOpenAccount = { accountSheetOpen = true },
                onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                onRefresh = viewModel::refreshCurrentTab,
                onRecentMore = { node -> actionSheetNode = node },
            )

            AppTab.FILES, AppTab.TRASH -> FilesScreen(
                paddingValues = innerPadding,
                currentUser = currentUser,
                avatarUrl = avatarUrl,
                explorer = activeExplorer,
                isTrashMode = isTrashMode,
                onOpenAccount = { accountSheetOpen = true },
                onRefresh = viewModel::refreshCurrentTab,
                onSwitchMode = { trashMode ->
                    viewModel.selectTab(if (trashMode) AppTab.TRASH else AppTab.FILES)
                },
                onKeywordChange = onExplorerKeywordChange,
                onSearch = onExplorerSearch,
                onNodeClick = { node ->
                    if (isTrashMode) {
                        actionSheetNode = node
                    } else {
                        viewModel.openNode(node)
                    }
                },
                onCrumbClick = viewModel::jumpToCrumb,
                onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                onCreateFolder = { createFolderOpen = true },
                onNodeMore = { node -> actionSheetNode = node },
            )

            AppTab.TEAM -> TeamScreen(
                paddingValues = innerPadding,
                team = uiState.team,
                baseUrl = uiState.baseUrl,
                currentUser = currentUser,
                avatarUrl = avatarUrl,
                onOpenAccount = { accountSheetOpen = true },
                onRefresh = viewModel::refreshCurrentTab,
                onCreateUser = { createUserOpen = true },
                onEditQuota = { user -> quotaTargetUserId = user.id },
                onResetPassword = { user -> resetPasswordTargetUserId = user.id },
            )

            AppTab.ME -> {
                LaunchedEffect(Unit) {
                    accountSheetOpen = true
                    viewModel.selectTab(AppTab.HOME)
                }
            }
        }
    }

    if (accountSheetOpen) {
        AccountSheet(
            currentUser = currentUser,
            avatarUrl = avatarUrl,
            isUpdatingAvatar = uiState.isUpdatingAvatar,
            isChangingPassword = uiState.isChangingPassword,
            onChangeAvatar = { avatarLauncher.launch("image/*") },
            onChangePassword = { changePasswordOpen = true },
            onDismiss = { accountSheetOpen = false },
            onLogout = {
                accountSheetOpen = false
                viewModel.logout()
            },
        )
    }

    if (changePasswordOpen) {
        ChangePasswordDialog(
            isSubmitting = uiState.isChangingPassword,
            onDismiss = {
                if (!uiState.isChangingPassword) {
                    changePasswordOpen = false
                }
            },
            onSubmit = { oldPassword, newPassword ->
                viewModel.changePassword(
                    oldPassword = oldPassword,
                    newPassword = newPassword,
                    onSuccess = { changePasswordOpen = false },
                )
            },
        )
    }

    if (createUserOpen) {
        CreateUserDialog(
            isSubmitting = uiState.team.isCreatingUser,
            onDismiss = {
                if (!uiState.team.isCreatingUser) {
                    createUserOpen = false
                }
            },
            onSubmit = { phoneNumber, nickname, password, role, quotaGb ->
                viewModel.createUser(
                    phoneNumber = phoneNumber,
                    nickname = nickname,
                    password = password,
                    role = role,
                    quotaGb = quotaGb,
                    onSuccess = { createUserOpen = false },
                )
            },
        )
    }

    quotaTargetUser?.let { targetUser ->
        UpdateQuotaDialog(
            targetUser = targetUser,
            isSubmitting = uiState.team.quotaUserId == targetUser.id,
            onDismiss = {
                if (uiState.team.quotaUserId != targetUser.id) {
                    quotaTargetUserId = null
                }
            },
            onSubmit = { quotaGb ->
                viewModel.updateUserQuota(
                    user = targetUser,
                    quotaGb = quotaGb,
                    onSuccess = { quotaTargetUserId = null },
                )
            },
        )
    }

    resetPasswordTargetUser?.let { targetUser ->
        ResetUserPasswordDialog(
            targetUser = targetUser,
            isSubmitting = uiState.team.passwordUserId == targetUser.id,
            onDismiss = {
                if (uiState.team.passwordUserId != targetUser.id) {
                    resetPasswordTargetUserId = null
                }
            },
            onSubmit = { newPassword ->
                viewModel.resetUserPassword(
                    user = targetUser,
                    newPassword = newPassword,
                    onSuccess = { resetPasswordTargetUserId = null },
                )
            },
        )
    }

    actionSheetNode?.let { node ->
        val busy = if (isTrashMode) {
            uiState.trash.actionNodeId == node.id
        } else {
            uiState.files.actionNodeId == node.id
        }

        NodeActionSheet(
            node = node,
            isTrashMode = isTrashMode,
            busy = busy,
            onDismiss = { actionSheetNode = null },
            onOpen = {
                actionSheetNode = null
                viewModel.openNode(node)
            },
            onPreview = {
                actionSheetNode = null
                viewModel.previewFile(node)
            },
            onDownload = {
                actionSheetNode = null
                pendingDownloadNode = node
                saveLauncher.launch(node.name)
            },
            onMoveToTrash = {
                actionSheetNode = null
                trashConfirmNode = node
            },
            onRestore = {
                actionSheetNode = null
                viewModel.restoreNode(node)
            },
            onPermanentDelete = {
                actionSheetNode = null
                permanentDeleteNode = node
            },
        )
    }

    if (createFolderOpen) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.files.isCreatingFolder) {
                    createFolderOpen = false
                }
            },
            title = { Text("新建文件夹", fontWeight = FontWeight.Bold) },
            text = {
                AliciaInputField(
                    value = createFolderName,
                    onValueChange = { createFolderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "文件夹名称",
                    placeholder = "例如：项目资料",
                )
            },
            confirmButton = {
                AliciaDialogActionRow(
                    onDismiss = {
                        createFolderOpen = false
                        createFolderName = ""
                    },
                    onConfirm = {
                        viewModel.createFolder(createFolderName)
                        createFolderName = ""
                        createFolderOpen = false
                    },
                    confirmLabel = if (uiState.files.isCreatingFolder) "创建中..." else "确认创建",
                    enabled = !uiState.files.isCreatingFolder,
                    confirmLoading = uiState.files.isCreatingFolder,
                )
            },
        )
    }

    trashConfirmNode?.let { node ->
        AlertDialog(
            onDismissRequest = { trashConfirmNode = null },
            title = { Text("移入回收站") },
            text = { Text("确认将“${node.name}”移入回收站吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        trashConfirmNode = null
                        viewModel.moveNodeToTrash(node)
                    },
                ) {
                    Text("确认", color = Color(0xFFD84B2A))
                }
            },
            dismissButton = {
                TextButton(onClick = { trashConfirmNode = null }) {
                    Text("取消")
                }
            },
        )
    }

    permanentDeleteNode?.let { node ->
        AlertDialog(
            onDismissRequest = { permanentDeleteNode = null },
            title = { Text("彻底删除") },
            text = { Text("确认彻底删除“${node.name}”吗？此操作无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        permanentDeleteNode = null
                        viewModel.permanentlyDeleteNode(node)
                    },
                ) {
                    Text("确认删除", color = Color(0xFFD84B2A))
                }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteNode = null }) {
                    Text("取消")
                }
            },
        )
    }

    PreviewDialog(
        state = uiState.preview,
        onDismiss = viewModel::closePreview,
    )
}

@Composable
private fun HomeScreen(
    paddingValues: PaddingValues,
    currentUser: User,
    avatarUrl: String?,
    home: HomeUiState,
    fileKeyword: String,
    recentNodes: List<StorageNode>,
    onFileKeywordChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenTeam: () -> Unit,
    onOpenAccount: () -> Unit,
    onUpload: () -> Unit,
    onRefresh: () -> Unit,
    onRecentMore: (StorageNode) -> Unit,
) {
    val overview = home.overview
    val usedBytes = overview?.usedBytes ?: currentUser.usedBytes
    val totalBytes = overview?.totalSpaceBytes ?: currentUser.storageQuotaBytes
    val quickActions = remember(currentUser.role) {
        listOf(
            AliciaQuickAction(
                label = "全部文件",
                hint = "浏览目录",
                icon = Icons.Rounded.FolderOpen,
                onClick = onOpenFiles,
            ),
            AliciaQuickAction(
                label = "上传文件",
                hint = "快速入库",
                icon = Icons.Rounded.UploadFile,
                onClick = onUpload,
            ),
            AliciaQuickAction(
                label = "回收站",
                hint = "恢复删除",
                icon = Icons.Rounded.DeleteOutline,
                onClick = onOpenTrash,
            ),
            AliciaQuickAction(
                label = if (currentUser.isAdmin) "账号管理" else "我的账号",
                hint = if (currentUser.isAdmin) "查看成员" else "登录信息",
                icon = if (currentUser.isAdmin) Icons.Rounded.ManageAccounts else Icons.Rounded.AccountCircle,
                onClick = if (currentUser.isAdmin) onOpenTeam else onOpenAccount,
            ),
        )
    }

    AliciaPullRefreshContainer(
        refreshing = home.loading,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AliciaPageHeader(
                    title = "Alicia Cloud",
                    actions = {
                        AliciaHeaderAvatarButton(
                            label = currentUser.nickname,
                            imageUrl = avatarUrl,
                            contentDescription = "账号",
                            onClick = onOpenAccount,
                        )
                    },
                )
            }

            item {
                AliciaSearchBar(
                    value = fileKeyword,
                    onValueChange = onFileKeywordChange,
                    onSearch = onSubmitSearch,
                    placeholder = "搜索网盘文件",
                )
            }

            item {
                AliciaSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AliciaBadge(text = if (currentUser.isAdmin) "管理员空间" else "我的空间")
                        Text(
                            text = currentUser.nickname,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.End,
                        )
                    }
                    AliciaQuotaBanner(
                        usedBytes = usedBytes,
                        totalBytes = totalBytes,
                        actionLabel = "查看文件",
                        onAction = onOpenFiles,
                    )
                }
            }

            item {
                AliciaQuickActionGrid(actions = quickActions)
            }

            when {
                home.loading && overview == null -> {
                    item { AliciaLoadingCard(message = "正在加载概览…") }
                }

                home.error != null && overview == null -> {
                    item {
                        AliciaEmptyState(
                            title = "概览暂时不可用",
                            description = home.error,
                        )
                    }
                }

                else -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AliciaMetricCard(
                                    title = "总项目",
                                    value = (overview?.totalItems ?: 0).toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                AliciaMetricCard(
                                    title = "文件夹",
                                    value = (overview?.totalFolders ?: 0).toString(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AliciaMetricCard(
                                    title = "文件数",
                                    value = (overview?.totalFiles ?: 0).toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                AliciaMetricCard(
                                    title = "已用空间",
                                    value = formatBytes(usedBytes),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    item {
                        AliciaMiniTrendCard(
                            usedBytes = usedBytes,
                            totalBytes = totalBytes,
                            values = home.usageHistory.map { it.usedBytes },
                        )
                    }
                }
            }

            item {
                AliciaSectionCard(contentPadding = 0) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "最近文件",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            AliciaTextAction(
                                label = "全部",
                                onClick = onOpenFiles,
                            )
                        }

                        if (recentNodes.isEmpty()) {
                            AliciaInlineState(
                                title = "暂无最近文件",
                                description = "上传内容后会优先显示在这里。",
                            )
                        } else {
                            recentNodes.forEachIndexed { index, node ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                }
                                AliciaCompactNodeRow(
                                    node = node,
                                    busy = false,
                                    onClick = onOpenFiles,
                                    onMore = { onRecentMore(node) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesScreen(

    paddingValues: PaddingValues,
    currentUser: User,
    avatarUrl: String?,
    explorer: ExplorerUiState,
    isTrashMode: Boolean,
    onOpenAccount: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchMode: (Boolean) -> Unit,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onNodeClick: (StorageNode) -> Unit,
    onCrumbClick: (Int) -> Unit,
    onUpload: () -> Unit,
    onCreateFolder: () -> Unit,
    onNodeMore: (StorageNode) -> Unit,
) {
    val currentFolderLabel = explorer.breadcrumbs.lastOrNull()?.label ?: "根目录"

    AliciaPullRefreshContainer(
        refreshing = explorer.loading,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 10.dp,
                end = 16.dp,
                bottom = if (isTrashMode) 10.dp else 110.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AliciaPageHeader(
                    title = "文件",
                    actions = {
                        AliciaHeaderAvatarButton(
                            label = currentUser.nickname,
                            imageUrl = avatarUrl,
                            contentDescription = "账号",
                            onClick = onOpenAccount,
                        )
                    },
                )
            }

            item {
                AliciaSearchBar(
                    value = explorer.keyword,
                    onValueChange = onKeywordChange,
                    onSearch = onSearch,
                    placeholder = if (isTrashMode) "搜索回收站" else "搜索网盘文件",
                )
            }

            item {
                AliciaSectionCard(contentPadding = 0) {
                    Column {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AliciaSegmentTabs(
                                labels = listOf("文件", "回收站"),
                                selectedIndex = if (isTrashMode) 1 else 0,
                                onSelected = { index -> onSwitchMode(index == 1) },
                            )

                            if (isTrashMode) {
                                Text(
                                    text = "回收站内容",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            } else {
                                AliciaFolderSummary(
                                    currentLabel = currentFolderLabel,
                                    breadcrumbs = explorer.breadcrumbs.map { it.label },
                                    onTap = onCrumbClick,
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        when {
                            explorer.loading && explorer.items.isEmpty() -> {
                                AliciaInlineState(
                                    title = "正在加载目录",
                                    description = "正在同步当前目录内容，请稍等一下。",
                                )
                            }

                            explorer.error != null && explorer.items.isEmpty() -> {
                                AliciaInlineState(
                                    title = "列表暂时不可用",
                                    description = explorer.error,
                                )
                            }

                            explorer.items.isEmpty() -> {
                                AliciaInlineState(
                                    title = if (isTrashMode) "回收站为空" else "当前目录为空",
                                    description = if (isTrashMode) {
                                        "删除的文件会先出现在这里。"
                                    } else {
                                        "上传文件或新建文件夹后会显示在这里。"
                                    },
                                )
                            }

                            else -> {
                                explorer.items.forEachIndexed { index, node ->
                                    if (index > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    }
                                    AliciaCompactNodeRow(
                                        node = node,
                                        busy = explorer.actionNodeId == node.id,
                                        onClick = { onNodeClick(node) },
                                        onMore = { onNodeMore(node) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isTrashMode) {
            AliciaFloatingFileDock(
                onUpload = onUpload,
                onCreateFolder = onCreateFolder,
                uploading = explorer.isUploading,
                creating = explorer.isCreatingFolder,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun TeamScreen(
    paddingValues: PaddingValues,
    team: TeamUiState,
    baseUrl: String,
    currentUser: User,
    avatarUrl: String?,
    onOpenAccount: () -> Unit,
    onRefresh: () -> Unit,
    onCreateUser: () -> Unit,
    onEditQuota: (User) -> Unit,
    onResetPassword: (User) -> Unit,
) {
    if (!currentUser.isAdmin) {
        AliciaEmptyState(
            title = "当前账号没有管理权限",
            description = "只有管理员可以查看账号列表。",
        )
        return
    }

    val adminCount = team.users.count { it.isAdmin }
    val normalCount = team.users.size - adminCount

    AliciaPullRefreshContainer(
        refreshing = team.loading,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AliciaPageHeader(
                    title = "账号管理",
                    actions = {
                        AliciaTextAction(
                            label = if (team.isCreatingUser) "创建中..." else "新增账号",
                            onClick = onCreateUser,
                            enabled = !team.isCreatingUser,
                        )
                        AliciaHeaderAvatarButton(
                            label = currentUser.nickname,
                            imageUrl = avatarUrl,
                            contentDescription = "账号",
                            onClick = onOpenAccount,
                        )
                    },
                )
            }

            item {
                AliciaSectionCard {
                    TwoUpRow(
                        start = { AliciaMetricCard(title = "总账号", value = team.users.size.toString()) },
                        end = { AliciaMetricCard(title = "管理员", value = adminCount.toString()) },
                    )
                    AliciaMetricCard(
                        title = "普通用户",
                        value = normalCount.toString(),
                    )
                }
            }

            item {
                AliciaSectionCard(contentPadding = 0) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "成员列表",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            AliciaBadge(text = team.users.size.toString())
                        }

                        when {
                            team.loading && team.users.isEmpty() -> {
                                AliciaInlineState(
                                    title = "正在加载账号列表",
                                    description = "请稍等，成员信息马上就到。",
                                )
                            }

                            team.error != null && team.users.isEmpty() -> {
                                AliciaInlineState(
                                    title = "账号列表暂时不可用",
                                    description = team.error,
                                )
                            }

                            team.users.isEmpty() -> {
                                AliciaInlineState(
                                    title = "还没有成员记录",
                                    description = "等后台创建账号后，这里会显示出来。",
                                )
                            }

                            else -> {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                team.users.forEachIndexed { index, user ->
                                    if (index > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    }
                                    AliciaCompactUserRow(
                                        user = user,
                                        imageUrl = resolveUserAvatarUrl(baseUrl, user),
                                        isCurrentUser = user.id == currentUser.id,
                                        isUpdatingQuota = team.quotaUserId == user.id,
                                        isResettingPassword = team.passwordUserId == user.id,
                                        onEditQuota = if (user.id != currentUser.id && !user.isAdmin) {
                                            { onEditQuota(user) }
                                        } else {
                                            null
                                        },
                                        onResetPassword = if (user.id != currentUser.id) {
                                            { onResetPassword(user) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    currentUser: User,
    avatarUrl: String?,
    isUpdatingAvatar: Boolean,
    isChangingPassword: Boolean,
    onChangeAvatar: () -> Unit,
    onChangePassword: () -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AliciaSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AliciaUserAvatar(
                        label = currentUser.nickname,
                        imageUrl = avatarUrl,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = currentUser.nickname,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatRole(currentUser.role) + " · " + currentUser.phoneNumber,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AliciaTextAction(
                        label = if (isUpdatingAvatar) "上传中..." else "修改头像",
                        enabled = !isUpdatingAvatar,
                        onClick = onChangeAvatar,
                        modifier = Modifier.weight(1f),
                    )
                    AliciaTextAction(
                        label = if (isChangingPassword) "提交中..." else "修改密码",
                        enabled = !isChangingPassword,
                        onClick = onChangePassword,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AliciaPrimaryButton(
                label = "退出登录",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ChangePasswordDialog(

    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val passwordMismatch = confirmPassword.isNotBlank() && confirmPassword != newPassword

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp,
        title = { Text("修改密码", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "更新当前账号登录密码后，请使用新密码重新登录其他设备。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                AliciaInputField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "当前密码",
                    placeholder = "请输入当前密码",
                    visualTransformation = PasswordVisualTransformation(),
                )
                AliciaInputField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "新密码",
                    placeholder = "请输入新密码",
                    visualTransformation = PasswordVisualTransformation(),
                )
                AliciaInputField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "确认新密码",
                    placeholder = "请再次输入新密码",
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (passwordMismatch) {
                    Text(
                        text = "两次输入的新密码不一致。",
                        color = Color(0xFFD84B2A),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            AliciaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = { onSubmit(oldPassword, newPassword) },
                confirmLabel = if (isSubmitting) "提交中..." else "确认修改",
                enabled = !isSubmitting,
                confirmLoading = isSubmitting,
            )
        },
    )
}

@Composable
private fun CreateUserDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, UserRole, String) -> Unit,
) {
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var roleName by rememberSaveable { mutableStateOf(UserRole.USER.name) }
    var quotaGb by rememberSaveable { mutableStateOf("0.5") }
    val role = UserRole.valueOf(roleName)
    val passwordMismatch = confirmPassword.isNotBlank() && confirmPassword != password
    val quotaInvalid = role == UserRole.USER && quotaGb.trim().toDoubleOrNull()?.let { it > 0 } != true

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp,
        title = { Text("新增账号", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AliciaSegmentTabs(
                    labels = listOf("管理员", "普通用户"),
                    selectedIndex = if (role == UserRole.ADMIN) 0 else 1,
                    onSelected = { index ->
                        roleName = if (index == 0) UserRole.ADMIN.name else UserRole.USER.name
                    },
                )
                AliciaInputField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "手机号",
                    placeholder = "请输入 11 位手机号",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                AliciaInputField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "昵称",
                    placeholder = "例如：项目成员",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                AliciaInputField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "初始密码",
                    placeholder = "请输入初始密码",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                AliciaInputField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "确认密码",
                    placeholder = "请再次输入初始密码",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                if (role == UserRole.USER) {
                    AliciaInputField(
                        value = quotaGb,
                        onValueChange = { quotaGb = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "最大额度（GB）",
                        placeholder = "例如：0.5",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { }),
                    )
                } else {
                    Text(
                        text = "管理员账号默认不限制个人存储额度。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (passwordMismatch) {
                    Text(
                        text = "两次输入的密码不一致。",
                        color = Color(0xFFD84B2A),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (quotaInvalid) {
                    Text(
                        text = "普通用户请填写大于 0 的 GB 额度。",
                        color = Color(0xFFD84B2A),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            AliciaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = { onSubmit(phoneNumber, nickname, password, role, quotaGb) },
                confirmLabel = if (isSubmitting) "创建中..." else "确认创建",
                enabled = !isSubmitting,
                confirmLoading = isSubmitting,
            )
        },
    )
}

@Composable
private fun UpdateQuotaDialog(
    targetUser: User,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var quotaGb by rememberSaveable(targetUser.id) {
        mutableStateOf(formatGigabytesInput(targetUser.storageQuotaBytes ?: 0L))
    }
    val quotaInvalid = quotaGb.trim().toDoubleOrNull()?.let { it > 0 } != true

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp,
        title = { Text("修改额度", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "${targetUser.nickname} · ${targetUser.phoneNumber}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "当前已用 ${formatBytes(targetUser.usedBytes)}，请按 GB 填写新的最大额度。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                AliciaInputField(
                    value = quotaGb,
                    onValueChange = { quotaGb = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "最大额度（GB）",
                    placeholder = "例如：1",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (quotaInvalid) {
                    Text(
                        text = "请输入大于 0 的 GB 额度。",
                        color = Color(0xFFD84B2A),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            AliciaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = { onSubmit(quotaGb) },
                confirmLabel = if (isSubmitting) "提交中..." else "确认修改",
                enabled = !isSubmitting,
                confirmLoading = isSubmitting,
            )
        },
    )
}

@Composable
private fun ResetUserPasswordDialog(
    targetUser: User,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by rememberSaveable(targetUser.id) { mutableStateOf("") }
    var confirmPassword by rememberSaveable(targetUser.id) { mutableStateOf("") }
    val passwordMismatch = confirmPassword.isNotBlank() && confirmPassword != password

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp,
        title = { Text("重置密码", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "${targetUser.nickname} · ${targetUser.phoneNumber}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "重置后旧登录状态会失效，请通知对方使用新密码重新登录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                AliciaInputField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "新密码",
                    placeholder = "请输入新密码",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                AliciaInputField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "确认新密码",
                    placeholder = "请再次输入新密码",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (passwordMismatch) {
                    Text(
                        text = "两次输入的新密码不一致。",
                        color = Color(0xFFD84B2A),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            AliciaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = { onSubmit(password) },
                confirmLabel = if (isSubmitting) "重置中..." else "确认重置",
                enabled = !isSubmitting,
                confirmLoading = isSubmitting,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeActionSheet(
    node: StorageNode,
    isTrashMode: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = node.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatNodeMeta(node),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            if (busy) {
                AliciaLoadingCard(message = "正在处理这个文件…")
            } else {
                if (isTrashMode) {
                    SheetActionButton(
                        icon = Icons.Rounded.RestoreFromTrash,
                        label = "恢复文件",
                        hint = "还原到原目录",
                        onClick = onRestore,
                    )
                    SheetActionButton(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "彻底删除",
                        hint = "删除后将无法恢复",
                        onClick = onPermanentDelete,
                        danger = true,
                    )
                } else {
                    if (node.type == StorageNodeType.FOLDER) {
                        SheetActionButton(
                            icon = Icons.Rounded.FolderOpen,
                            label = "打开文件夹",
                            hint = "进入目录继续浏览",
                            onClick = onOpen,
                        )
                    } else {
                        SheetActionButton(
                            icon = Icons.Rounded.Preview,
                            label = "预览文件",
                            hint = "图片和文本支持内置预览",
                            onClick = onPreview,
                        )
                        SheetActionButton(
                            icon = Icons.Rounded.Download,
                            label = "下载到本地",
                            hint = "保存到设备目录",
                            onClick = onDownload,
                        )
                    }
                    SheetActionButton(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "移入回收站",
                        hint = "稍后还可以恢复",
                        onClick = onMoveToTrash,
                        danger = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hint: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    AliciaSectionCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (danger) Color(0xFFD84B2A) else MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    color = if (danger) Color(0xFFD84B2A) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PreviewDialog(
    state: FilePreviewState,
    onDismiss: () -> Unit,
) {
    if (!state.visible) {
        return
    }

    val bitmap = remember(state.imageBytes) {
        state.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = state.fileName,
                maxLines = 2,
            )
        },
        text = {
            when {
                state.loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "正在加载预览…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.error != null -> {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.kind == PreviewKind.TEXT -> {
                    SelectionContainer {
                        Text(
                            text = if (state.textContent.isBlank()) "文件内容为空。" else state.textContent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                state.kind == PreviewKind.IMAGE && bitmap != null -> {
                    Image(
                        bitmap = bitmap,
                        contentDescription = state.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    )
                }

                else -> {
                    Text(
                        text = "当前文件暂不支持内置预览，请先下载查看。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

