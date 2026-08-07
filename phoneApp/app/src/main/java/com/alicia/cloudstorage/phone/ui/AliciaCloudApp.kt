package com.alicia.cloudstorage.phone.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.alicia.cloudstorage.phone.HONG_KONG_BASE_URL
import com.alicia.cloudstorage.phone.MAINLAND_BASE_URL
import com.alicia.cloudstorage.phone.R
import com.alicia.cloudstorage.phone.describeAccessEnvironment
import com.alicia.cloudstorage.phone.data.AppTab
import com.alicia.cloudstorage.phone.data.ShareLinkDetailResponse
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.User
import com.alicia.cloudstorage.phone.data.UserRole
import com.alicia.cloudstorage.phone.data.isAdmin
import com.alicia.cloudstorage.phone.normalizeConfiguredBaseUrl
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

private enum class AliciaTitleGraphicVariant {
    Hero,
    Page,
}

private data class AliciaTitleGraphicSpec(
    val resId: Int,
    val width: Dp,
    val height: Dp,
)

private data class ShareCreateFormValues(
    val title: String,
    val password: String?,
    val expiresInDays: Int?,
    val allowDownload: Boolean,
    val allowSave: Boolean,
)

private fun aliciaTitleGraphicSpec(title: String): AliciaTitleGraphicSpec? = when (title) {
    "Alicia 云盘" -> AliciaTitleGraphicSpec(
        resId = R.drawable.alicia_title_home_mecha,
        width = 195.dp,
        height = 60.dp,
    )

    "账号管理" -> AliciaTitleGraphicSpec(
        resId = R.drawable.alicia_title_account_mecha,
        width = 124.dp,
        height = 48.dp,
    )

    "文件管理" -> AliciaTitleGraphicSpec(
        resId = R.drawable.alicia_title_files_mecha,
        width = 126.dp,
        height = 48.dp,
    )

    else -> null
}

@Composable
private fun AliciaMechaTitleGraphic(
    title: String,
    variant: AliciaTitleGraphicVariant,
    modifier: Modifier = Modifier,
) {
    val graphic = aliciaTitleGraphicSpec(title)
    if (graphic != null) {
        Image(
            painter = painterResource(id = graphic.resId),
            contentDescription = title,
            modifier = modifier.size(width = graphic.width, height = graphic.height),
            contentScale = ContentScale.Fit,
        )
        return
    }

    val fontSize = if (variant == AliciaTitleGraphicVariant.Hero) 38.sp else 30.sp
    val lineHeight = if (variant == AliciaTitleGraphicVariant.Hero) 44.sp else 34.sp
    val shadowAlpha = if (variant == AliciaTitleGraphicVariant.Hero) 0.65f else 0.48f
    val shadowOffset = if (variant == AliciaTitleGraphicVariant.Hero) 2.5f else 1.8f
    val blurRadius = if (variant == AliciaTitleGraphicVariant.Hero) 8f else 6f

    Text(
        text = title,
        color = Color.White,
        fontFamily = AliciaMechaFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = if (variant == AliciaTitleGraphicVariant.Hero) 0.8.sp else 0.sp,
        maxLines = 1,
        style = TextStyle(
            shadow = Shadow(
                color = Color(0xFF48D5FF).copy(alpha = shadowAlpha),
                offset = androidx.compose.ui.geometry.Offset(shadowOffset, shadowOffset),
                blurRadius = blurRadius,
            ),
        ),
    )
}

@Composable
fun AliciaCloudApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingIncomingShareDownloadNode by remember { mutableStateOf<StorageNode?>(null) }
    val incomingShareDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val targetNode = pendingIncomingShareDownloadNode
        pendingIncomingShareDownloadNode = null
        if (uri != null && targetNode != null) {
            viewModel.downloadIncomingShareFileToUri(targetNode, uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isBooting -> BootScreen()
            uiState.authToken.isNullOrBlank() -> LoginScreen(
                baseUrl = uiState.baseUrl,
                isSubmitting = uiState.isSubmittingLogin,
                onLogin = viewModel::login,
            )

            else -> MainShell(
                uiState = uiState,
                viewModel = viewModel,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        )
    }

    uiState.incomingShare.prompt?.let {
        AliciaMechaConfirmDialog(
            title = "发现分享链接",
            message = "检测到剪切板中的 Alicia 云盘分享链接，是否查看？",
            onDismiss = viewModel::dismissIncomingSharePrompt,
            onConfirm = viewModel::confirmIncomingSharePrompt,
            confirmLabel = "查看分享",
        )
    }

    val incomingShare = uiState.incomingShare
    val incomingNeedsPassword = incomingShare.status?.available == true &&
        incomingShare.status.requiresPassword &&
        incomingShare.shareAccessToken.isNullOrBlank()
    val shouldShowIncomingShare = incomingShare.activeShareCode != null &&
        (
            !uiState.authToken.isNullOrBlank() ||
                !incomingShare.loginPromptDismissed ||
                incomingShare.statusLoading ||
                !incomingShare.error.isNullOrBlank() ||
                incomingNeedsPassword
            )

    if (shouldShowIncomingShare) {
        IncomingShareDialog(
            state = incomingShare,
            isLoggedIn = !uiState.authToken.isNullOrBlank(),
            onDismiss = viewModel::closeIncomingShare,
            onContinueLogin = viewModel::dismissIncomingShareLoginNotice,
            onVerifyPassword = viewModel::verifyIncomingSharePassword,
            onSave = viewModel::saveIncomingShareToDrive,
            onDownload = { node ->
                pendingIncomingShareDownloadNode = node
                incomingShareDownloadLauncher.launch(node.name)
            },
        )
    }

    uiState.appUpdate?.let { appUpdate ->
        AppUpdateDialog(
            updateInfo = appUpdate,
            onDismiss = viewModel::dismissAppUpdate,
            onConfirm = viewModel::openAppUpdateDownload,
        )
    }
}

@Composable
private fun BootScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071120)),
    ) {
        Image(
            painter = painterResource(id = R.drawable.alicia_boot_splash_v2),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x04071120),
                            Color(0x40071120),
                            Color(0xE8071120),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AliciaBadge(text = "SYSTEM BOOT")
            Text(
                text = "机甲终端正在初始化云盘模块",
                color = Color(0xFFD8E6FF),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            AliciaMechaPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 16.dp),
                backgroundResId = R.drawable.alicia_9_login_panel,
                backgroundSlice = 72.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF3F83FF),
                        strokeWidth = 2.5.dp,
                        trackColor = Color(0x223F83FF),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "连接 Alicia 云控中枢",
                            color = Color(0xFF101626),
                            fontFamily = AliciaMechaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        )
                        Text(
                            text = "请稍候，系统正在同步身份与空间数据",
                            color = Color(0xFF748094),
                            fontFamily = AliciaMechaFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0x5548D5FF),
                                    Color(0xFF48D5FF),
                                    Color(0x553F83FF),
                                ),
                            ),
                            shape = RoundedCornerShape(99.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    baseUrl: String,
    isSubmitting: Boolean,
    onLogin: (String, String) -> Unit,
) {
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AliciaMechaBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AliciaMechaLoginHero()
            AliciaMechaPanel(
                modifier = Modifier
                    .offset(y = (-26).dp)
                    .padding(horizontal = 18.dp),
                contentPadding = PaddingValues(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 18.dp),
                backgroundResId = R.drawable.alicia_9_login_panel,
                backgroundSlice = 72.dp,
            ) {
                AliciaBadge(text = "登录舱")
                AliciaMechaInputField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "手机号",
                    placeholder = "请输入 11 位手机号",
                )
                AliciaMechaInputField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "密码",
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = "请输入登录密码",
                )
                AliciaMechaActionButton(
                    label = if (isSubmitting) "登录中..." else "登录",
                    onClick = { onLogin(phoneNumber, password) },
                    tone = AliciaMechaActionButtonTone.Primary,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            }
            AliciaAccessEndpointHint(
                baseUrl = baseUrl,
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .padding(top = 10.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AliciaMechaLoginHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(284.dp)
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)),
    ) {
        Image(
            painter = painterResource(id = R.drawable.alicia_mecha_header),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x22071120),
                            Color(0x3A071120),
                            Color(0xD5071120),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AliciaBadge(text = "MOBILE TERMINAL")
            Text(
                text = "Alicia 网盘",
                color = Color.White,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xFF48D5FF).copy(alpha = 0.65f),
                        offset = androidx.compose.ui.geometry.Offset(2.5f, 2.5f),
                        blurRadius = 8f,
                    ),
                ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF48D5FF), Color(0x001E63FF)),
                        ),
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    uiState: AppUiState,
    viewModel: MainViewModel,
) {
    val currentUser = uiState.currentUser ?: return
    val context = LocalContext.current
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
    var changeNicknameOpen by rememberSaveable { mutableStateOf(false) }
    var changePasswordOpen by rememberSaveable { mutableStateOf(false) }
    var quotaTargetUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resetPasswordTargetUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actionSheetNode by remember { mutableStateOf<StorageNode?>(null) }
    var shareSetupNode by remember { mutableStateOf<StorageNode?>(null) }
    var trashConfirmNode by remember { mutableStateOf<StorageNode?>(null) }
    var permanentDeleteNode by remember { mutableStateOf<StorageNode?>(null) }
    var batchTrashConfirmOpen by rememberSaveable { mutableStateOf(false) }
    var batchPermanentDeleteConfirmOpen by rememberSaveable { mutableStateOf(false) }
    var batchMoveOpen by rememberSaveable { mutableStateOf(false) }
    var batchMoveTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDownloadNode by remember { mutableStateOf<StorageNode?>(null) }
    var pendingBaseUrlSwitch by rememberSaveable { mutableStateOf<String?>(null) }
    var logoutConfirmOpen by rememberSaveable { mutableStateOf(false) }
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
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadDocuments(uris)
        }
    }
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
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
                    ),
                )
            } else {
                add(
                    AliciaBottomNavItem(
                        label = "Alicia 中枢",
                        icon = Icons.Rounded.ManageAccounts,
                        selected = false,
                        onClick = { accountSheetOpen = true },
                        enabled = true,
                        shellResIdOverride = R.drawable.alicia_bottom_center_mecha,
                        showShellContent = false,
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

    val layoutDirection = LocalLayoutDirection.current
    val bottomBarContentOverlap = if (bottomItems.size == 3) 18.dp else 0.dp

    Scaffold(
        bottomBar = { AliciaBottomBar(items = bottomItems) },
        containerColor = Color(0xFF071120),
    ) { innerPadding ->
        val contentPadding = remember(innerPadding, layoutDirection, bottomBarContentOverlap) {
            PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = (innerPadding.calculateBottomPadding() - bottomBarContentOverlap).coerceAtLeast(0.dp),
            )
        }

        when (uiState.selectedTab) {
            AppTab.HOME -> HomeScreen(
                paddingValues = contentPadding,
                currentUser = currentUser,
                avatarUrl = avatarUrl,
                home = uiState.home,
                fileKeyword = uiState.files.keyword,
                recentNodes = uiState.home.recentNodes,
                onFileKeywordChange = viewModel::updateFileKeyword,
                onSubmitSearch = viewModel::submitHomeFileSearch,
                onOpenFiles = { viewModel.selectTab(AppTab.FILES) },
                onOpenRecentNode = viewModel::revealNodeInFiles,
                onOpenTrash = { viewModel.selectTab(AppTab.TRASH) },
                onOpenTeam = { viewModel.selectTab(AppTab.TEAM) },
                onOpenAccount = { accountSheetOpen = true },
                onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                onRefresh = viewModel::refreshCurrentTab,
                onRecentMore = { node -> actionSheetNode = node },
            )

            AppTab.FILES, AppTab.TRASH -> FilesScreen(
                paddingValues = contentPadding,
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
                    if (activeExplorer.selectedNodeIds.isNotEmpty()) {
                        viewModel.toggleNodeSelection(isTrashMode, node.id)
                    } else if (isTrashMode) {
                        actionSheetNode = node
                    } else {
                        viewModel.openNode(node)
                    }
                },
                onNodeLongPress = { node ->
                    viewModel.toggleNodeSelection(isTrashMode, node.id)
                },
                onCrumbClick = viewModel::jumpToCrumb,
                onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                onCreateFolder = { createFolderOpen = true },
                onNodeMore = { node -> actionSheetNode = node },
                onToggleSelection = { node -> viewModel.toggleNodeSelection(isTrashMode, node.id) },
                onClearSelection = { viewModel.clearNodeSelection(isTrashMode) },
                onSelectAll = { viewModel.selectAllVisibleNodes(isTrashMode) },
                onBatchMove = {
                    batchMoveTargetId = null
                    batchMoveOpen = true
                    viewModel.loadMoveTargets()
                },
                onBatchTrash = { batchTrashConfirmOpen = true },
                onBatchRestore = viewModel::restoreSelectedNodes,
                onBatchPermanentDelete = { batchPermanentDeleteConfirmOpen = true },
            )

            AppTab.TEAM -> TeamScreen(
                paddingValues = contentPadding,
                team = uiState.team,
                baseUrl = uiState.baseUrl,
                currentUser = currentUser,
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
            baseUrl = uiState.baseUrl,
            avatarUrl = avatarUrl,
            isUpdatingProfile = uiState.isUpdatingProfile,
            isUpdatingAvatar = uiState.isUpdatingAvatar,
            isChangingPassword = uiState.isChangingPassword,
            onChangeAvatar = { avatarLauncher.launch(arrayOf("image/*")) },
            onChangeNickname = { changeNicknameOpen = true },
            onChangePassword = { changePasswordOpen = true },
            onSwitchBaseUrl = { targetBaseUrl ->
                if (normalizeConfiguredBaseUrl(targetBaseUrl) == normalizeConfiguredBaseUrl(uiState.baseUrl)) {
                    viewModel.switchBaseUrl(targetBaseUrl)
                } else {
                    pendingBaseUrlSwitch = targetBaseUrl
                }
            },
            onDismiss = { accountSheetOpen = false },
            onLogout = { logoutConfirmOpen = true },
        )
    }

    if (changeNicknameOpen) {
        ChangeNicknameDialog(
            currentNickname = currentUser.nickname,
            isSubmitting = uiState.isUpdatingProfile,
            onDismiss = {
                if (!uiState.isUpdatingProfile) {
                    changeNicknameOpen = false
                }
            },
            onSubmit = { nickname ->
                viewModel.updateNickname(
                    nickname = nickname,
                    onSuccess = { changeNicknameOpen = false },
                )
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
            uiState.files.actionNodeId == node.id ||
                (uiState.shareCreation.isCreating && uiState.shareCreation.nodeId == node.id)
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
            onShare = {
                actionSheetNode = null
                shareSetupNode = node
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

    shareSetupNode?.let { node ->
        val submitting = uiState.shareCreation.isCreating && uiState.shareCreation.nodeId == node.id

        ShareCreateDialog(
            node = node,
            baseUrl = uiState.baseUrl,
            isSubmitting = submitting,
            onDismiss = {
                if (!submitting) {
                    shareSetupNode = null
                }
            },
            onSubmit = { values ->
                viewModel.createShareLink(
                    node = node,
                    title = values.title,
                    password = values.password,
                    expiresInDays = values.expiresInDays,
                    allowDownload = values.allowDownload,
                    allowSave = values.allowSave,
                    onSuccess = { shareSetupNode = null },
                )
            },
        )
    }

    uiState.shareCreation.createdShare?.let { share ->
        ShareCreatedDialog(
            share = share,
            onDismiss = viewModel::clearCreatedShare,
            onCopy = { viewModel.copyShareInfo(share) },
            onSystemShare = { viewModel.openSystemShareSheet(share) },
        )
    }

    if (createFolderOpen) {
        AliciaMechaDialogShell(
            title = "新建文件夹",
            onDismissRequest = {
                if (!uiState.files.isCreatingFolder) {
                    createFolderOpen = false
                }
            },
            dismissEnabled = !uiState.files.isCreatingFolder,
            supporting = {
                Text(
                    text = "新目录会创建在当前浏览位置下，并立即同步到目录树中。",
                    color = Color(0xFF748094),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            },
            body = {
                AliciaMechaInputField(
                    value = createFolderName,
                    onValueChange = { createFolderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "文件夹名称",
                    placeholder = "例如：项目资料",
                )
            },
            footer = {
                AliciaMechaDialogActionRow(
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
        AliciaMechaConfirmDialog(
            title = "移入回收站",
            message = "确认将“${node.name}”移入回收站吗？",
            onDismiss = { trashConfirmNode = null },
            onConfirm = {
                trashConfirmNode = null
                viewModel.moveNodeToTrash(node)
            },
            confirmLabel = "确认移入",
            confirmTone = AliciaMechaActionButtonTone.Danger,
        )
    }

    permanentDeleteNode?.let { node ->
        AliciaMechaConfirmDialog(
            title = "彻底删除",
            message = "确认彻底删除“${node.name}”吗？此操作无法恢复。",
            onDismiss = { permanentDeleteNode = null },
            onConfirm = {
                permanentDeleteNode = null
                viewModel.permanentlyDeleteNode(node)
            },
            confirmLabel = "确认删除",
            confirmTone = AliciaMechaActionButtonTone.Danger,
        )
    }

    if (batchTrashConfirmOpen) {
        AliciaMechaConfirmDialog(
            title = "批量移入回收站",
            message = "确认将当前选中的文件移入回收站吗？",
            onDismiss = { batchTrashConfirmOpen = false },
            onConfirm = {
                batchTrashConfirmOpen = false
                viewModel.moveSelectedNodesToTrash()
            },
            confirmLabel = "确认移入",
            confirmTone = AliciaMechaActionButtonTone.Danger,
        )
    }

    if (batchPermanentDeleteConfirmOpen) {
        AliciaMechaConfirmDialog(
            title = "批量彻底删除",
            message = "确认彻底删除当前选中的文件吗？此操作无法恢复。",
            onDismiss = { batchPermanentDeleteConfirmOpen = false },
            onConfirm = {
                batchPermanentDeleteConfirmOpen = false
                viewModel.permanentlyDeleteSelectedNodes()
            },
            confirmLabel = "确认删除",
            confirmTone = AliciaMechaActionButtonTone.Danger,
        )
    }

    if (batchMoveOpen) {
        MoveTargetDialog(
            moveTargets = uiState.files.moveTargetFolders,
            loading = uiState.files.moveTargetLoading,
            selectedTargetId = batchMoveTargetId,
            onSelectTarget = { batchMoveTargetId = it },
            onDismiss = { batchMoveOpen = false },
            onConfirm = {
                viewModel.moveSelectedNodes(batchMoveTargetId) {
                    batchMoveOpen = false
                }
            },
        )
    }

    pendingBaseUrlSwitch?.let { targetBaseUrl ->
        val accessLabel = describeAccessEnvironment(normalizeConfiguredBaseUrl(targetBaseUrl))
        AliciaMechaConfirmDialog(
            title = "切换接入环境",
            message = "确认切换到“$accessLabel”吗？切换后会退出当前登录，并需要重新认证。",
            onDismiss = { pendingBaseUrlSwitch = null },
            onConfirm = {
                pendingBaseUrlSwitch = null
                accountSheetOpen = false
                viewModel.switchBaseUrl(targetBaseUrl)
            },
            confirmLabel = "确认切换",
        )
    }

    if (logoutConfirmOpen) {
        AliciaMechaConfirmDialog(
            title = "退出登录",
            message = "确认退出当前登录吗？退出后需要重新输入账号和密码。",
            onDismiss = { logoutConfirmOpen = false },
            onConfirm = {
                logoutConfirmOpen = false
                accountSheetOpen = false
                viewModel.logout()
            },
            confirmLabel = "确认退出",
            confirmTone = AliciaMechaActionButtonTone.Danger,
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
    onOpenRecentNode: (StorageNode) -> Unit,
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
        buildList {
            add(
                AliciaQuickAction(
                    label = "全部文件",
                    hint = "",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = onOpenFiles,
                ),
            )
            add(
                AliciaQuickAction(
                    label = "上传文件",
                    hint = "",
                    icon = Icons.Rounded.UploadFile,
                    onClick = onUpload,
                ),
            )
            add(
                AliciaQuickAction(
                    label = "回收站",
                    hint = "",
                    icon = Icons.Rounded.DeleteOutline,
                    onClick = onOpenTrash,
                ),
            )
            if (currentUser.isAdmin) {
                add(
                    AliciaQuickAction(
                        label = "账号管理",
                        hint = "",
                        icon = Icons.Rounded.ManageAccounts,
                        onClick = onOpenTeam,
                    ),
                )
            }
        }
    }

    AliciaPullRefreshContainer(
        refreshing = home.loading,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        AliciaMechaBackdrop(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AliciaMechaDesignSpec.pagePadding, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(AliciaMechaDesignSpec.contentGap),
            ) {
            item {
                AliciaMechaHomeHero()
            }

            item {
                AliciaMechaHomeSearchBar(
                    value = fileKeyword,
                    onValueChange = onFileKeywordChange,
                    onSearch = onSubmitSearch,
                    placeholder = "搜索网盘文件",
                )
            }

            item {
                AliciaMechaHomeAdminPanel(
                    title = currentUser.nickname.ifBlank {
                        if (currentUser.isAdmin) "系统管理员" else "普通用户"
                    },
                    badgeText = if (currentUser.isAdmin) "管理员空间" else "我的空间",
                    usedBytes = usedBytes,
                    totalBytes = totalBytes,
                    onPrimaryAction = onOpenFiles,
                )
            }

            item {
                AliciaMechaQuickActionGrid(actions = quickActions)
            }

            when {
                home.loading && overview == null -> {
                    item {
                        AliciaMechaPanel {
                            AliciaInlineState(
                                title = "正在加载概览",
                                description = "",
                            )
                        }
                    }
                }

                home.error != null && overview == null -> {
                    item {
                        AliciaMechaPanel {
                            AliciaInlineState(
                                title = "概览暂时不可用",
                                description = "",
                            )
                        }
                    }
                }

                else -> {
                    if (currentUser.isAdmin) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AliciaMechaMetricCard(
                                        title = "总项目",
                                        value = (overview?.totalItems ?: 0).toString(),
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Rounded.Preview,
                                    )
                                    AliciaMechaMetricCard(
                                        title = "文件夹",
                                        value = (overview?.totalFolders ?: 0).toString(),
                                        modifier = Modifier.weight(1f),
                                        accent = Color(0xFFFF9D24),
                                        icon = Icons.Rounded.FolderOpen,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AliciaMechaMetricCard(
                                        title = "文件数",
                                        value = (overview?.totalFiles ?: 0).toString(),
                                        modifier = Modifier.weight(1f),
                                        accent = Color(0xFFFF9D24),
                                        icon = Icons.Rounded.UploadFile,
                                    )
                                    AliciaMechaMetricCard(
                                        title = "已用空间",
                                        value = formatBytes(usedBytes),
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Rounded.Download,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        AliciaMechaTrendCard(
                            usedBytes = usedBytes,
                            totalBytes = totalBytes,
                            values = home.usageHistory.map { it.usedBytes },
                        )
                    }
                }
            }

            item {
                AliciaMechaPanel(
                    contentPadding = PaddingValues(0.dp),
                    backgroundResId = R.drawable.alicia_9_recent,
                    backgroundSlice = 64.dp,
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "最近文件",
                                color = Color(0xFF101626),
                                fontFamily = AliciaMechaFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                lineHeight = 23.sp,
                            )
                            AliciaMechaBadgeAction(
                                label = "全部",
                                onClick = onOpenFiles,
                            )
                        }

                        AliciaMechaRecentFiles(
                            nodes = recentNodes,
                            onOpen = onOpenRecentNode,
                            onMore = onRecentMore,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                        )
                    }
                }
            }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun AliciaMechaHomeHero() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val sideBleed = AliciaMechaDesignSpec.pagePadding
        val heroWidth = maxWidth + sideBleed * 2
        Box(
            modifier = Modifier
                .requiredWidth(heroWidth)
                //.offset(y = 25.dp)
                .height(AliciaMechaDesignSpec.heroHeight)
                //.clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
        ) {
            Image(
                painter = painterResource(id = R.drawable.alicia_mecha_header),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    //.fillMaxHeight()
                    //.requiredWidth(heroWidth * 1.08f)
                ,
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x33071120),
                                Color(0x22071120),
                                Color(0xCC071120),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AliciaMechaTitleGraphic(
                    title = "Alicia 云盘",
                    variant = AliciaTitleGraphicVariant.Hero,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF48D5FF), Color(0x001E63FF)),
                            ),
                        ),
                )
            }
        }
    }
}


@Composable
private fun AliciaMechaAccountOrb(
    nickname: String,
    avatarUrl: String?,
    onOpenAccount: () -> Unit,
) {
    AliciaMechaAvatarFrame(
        label = nickname,
        imageUrl = avatarUrl,
        contentDescription = "账号",
        frameSize = 58.dp,
        avatarSize = 38.dp,
        onClick = onOpenAccount,
    )
}

@Composable
private fun AliciaMechaFilesHeader(
    title: String,
    nickname: String,
    avatarUrl: String?,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 0.dp, end = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AliciaMechaTitleGraphic(
                title = title,
                variant = AliciaTitleGraphicVariant.Page,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF48D5FF), Color(0x001E63FF)),
                        ),
                    ),
            )
        }
        AliciaMechaAccountOrb(
            nickname = nickname,
            avatarUrl = avatarUrl,
            onOpenAccount = onOpenAccount,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onNodeLongPress: (StorageNode) -> Unit,
    onCrumbClick: (Int) -> Unit,
    onUpload: () -> Unit,
    onCreateFolder: () -> Unit,
    onNodeMore: (StorageNode) -> Unit,
    onToggleSelection: (StorageNode) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBatchMove: () -> Unit,
    onBatchTrash: () -> Unit,
    onBatchRestore: () -> Unit,
    onBatchPermanentDelete: () -> Unit,
) {
    val currentFolderLabel = explorer.breadcrumbs.lastOrNull()?.label ?: "根目录"
    val selectionCount = explorer.selectedNodeIds.size
    val selectionMode = selectionCount > 0
    val listState = remember { LazyListState() }

    LaunchedEffect(explorer.highlightedNodeId, explorer.items) {
        val highlightedNodeId = explorer.highlightedNodeId ?: return@LaunchedEffect
        val highlightedIndex = explorer.items.indexOfFirst { it.id == highlightedNodeId }
        if (highlightedIndex >= 0) {
            listState.animateScrollToItem(3 + highlightedIndex)
        }
    }

    AliciaPullRefreshContainer(
        refreshing = explorer.loading,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        AliciaMechaBackdrop(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AliciaMechaDesignSpec.pagePadding,
                        top = 10.dp,
                        end = AliciaMechaDesignSpec.pagePadding,
                        bottom = if (isTrashMode) 18.dp else 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AliciaMechaDesignSpec.contentGap),
                ) {
                    item {
                        AliciaMechaFilesHeader(
                            title = if (isTrashMode) "回收站" else "文件管理",
                            nickname = currentUser.nickname,
                            avatarUrl = avatarUrl,
                            onOpenAccount = onOpenAccount,
                        )
                    }

                    item {
                        AliciaMechaSearchBar(
                            value = explorer.keyword,
                            onValueChange = onKeywordChange,
                            onSearch = onSearch,
                            placeholder = if (isTrashMode) "搜索回收站" else "搜索网盘文件",
                            modifier = Modifier.padding(horizontal = 17.dp),
                        )
                    }

                    item {
                        AliciaMechaPanel(
                            contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AliciaRoundedBadge(text = if (isTrashMode) "回收仓" else "文件舱")
                                if (!selectionMode && explorer.items.isNotEmpty()) {
                                    AliciaMechaBadgeAction(
                                        label = "全选",
                                        onClick = onSelectAll,
                                    )
                                }
                            }

                            AliciaMechaSegmentTabs(
                                labels = listOf("文件", "回收站"),
                                selectedIndex = if (isTrashMode) 1 else 0,
                                onSelected = { index -> onSwitchMode(index == 1) },
                            )

                            if (isTrashMode) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "回收站内容",
                                        color = Color(0xFF101626),
                                        fontFamily = AliciaMechaFontFamily,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        lineHeight = 24.sp,
                                    )
                                    Text(
                                        text = "支持批量恢复，也可以直接彻底清理。",
                                        color = Color(0xFF748094),
                                        fontFamily = AliciaMechaFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                    )
                                }
                            } else {
                                AliciaFolderSummary(
                                    currentLabel = currentFolderLabel,
                                    breadcrumbs = explorer.breadcrumbs.map { it.label },
                                    onTap = onCrumbClick,
                                )
                            }

                            if (selectionMode) {
                                AliciaBatchSelectionPanel(
                                    count = selectionCount,
                                    isTrashMode = isTrashMode,
                                    busy = explorer.isBatchActing,
                                    onSelectAll = onSelectAll,
                                    onClear = onClearSelection,
                                    onMove = onBatchMove,
                                    onTrash = onBatchTrash,
                                    onRestore = onBatchRestore,
                                    onPermanentDelete = onBatchPermanentDelete,
                                )
                            }
                        }
                    }

                    when {
                        explorer.loading && explorer.items.isEmpty() -> {
                            item {
                                AliciaMechaPanel(
                                    contentPadding = PaddingValues(0.dp),
                                    backgroundResId = R.drawable.alicia_9_recent,
                                    backgroundSlice = 64.dp,
                                ) {
                                    AliciaInlineState(
                                        title = "正在加载目录",
                                        description = "正在同步当前目录内容，请稍等一下。",
                                    )
                                }
                            }
                        }

                        explorer.error != null && explorer.items.isEmpty() -> {
                            item {
                                AliciaMechaPanel(
                                    contentPadding = PaddingValues(0.dp),
                                    backgroundResId = R.drawable.alicia_9_recent,
                                    backgroundSlice = 64.dp,
                                ) {
                                    AliciaInlineState(
                                        title = "列表暂时不可用",
                                        description = explorer.error,
                                    )
                                }
                            }
                        }

                        explorer.items.isEmpty() -> {
                            item {
                                AliciaMechaPanel(
                                    contentPadding = PaddingValues(0.dp),
                                    backgroundResId = R.drawable.alicia_9_recent,
                                    backgroundSlice = 64.dp,
                                ) {
                                    AliciaInlineState(
                                        title = if (isTrashMode) "回收站为空" else "当前目录为空",
                                        description = if (isTrashMode) {
                                            "删除的文件会先出现在这里。"
                                        } else {
                                            "上传文件或新建文件夹后会显示在这里。"
                                        },
                                    )
                                }
                            }
                        }

                        else -> {
                            itemsIndexed(
                                items = explorer.items,
                                key = { _, node -> node.id },
                            ) { _, node ->
                                AliciaCompactNodeRow(
                                    node = node,
                                    busy = explorer.isBatchActing || explorer.actionNodeId == node.id,
                                    selected = node.id in explorer.selectedNodeIds,
                                    highlighted = node.id == explorer.highlightedNodeId,
                                    selectionMode = selectionMode,
                                    onClick = { onNodeClick(node) },
                                    onLongPress = { onNodeLongPress(node) },
                                    onToggleSelect = { onToggleSelection(node) },
                                    onMore = { onNodeMore(node) },
                                )
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
                        .padding(end = 18.dp, bottom = 22.dp),
                )
            }
        }
    }
}

@Composable
private fun AliciaBatchSelectionPanel(
    count: Int,
    isTrashMode: Boolean,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选择 $count 项",
                color = Color(0xFF101626),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF1E63FF),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AliciaBatchActionChip(
                label = "全选",
                onClick = onSelectAll,
                enabled = !busy,
            )
            if (isTrashMode) {
                AliciaBatchActionChip(
                    label = "恢复",
                    onClick = onRestore,
                    enabled = !busy,
                    primary = true,
                )
                AliciaBatchActionChip(
                    label = "彻底删除",
                    onClick = onPermanentDelete,
                    enabled = !busy,
                    danger = true,
                )
            } else {
                AliciaBatchActionChip(
                    label = "移动",
                    onClick = onMove,
                    enabled = !busy,
                    primary = true,
                )
                AliciaBatchActionChip(
                    label = "删除",
                    onClick = onTrash,
                    enabled = !busy,
                    danger = true,
                )
            }
            AliciaBatchActionChip(
                label = "取消",
                onClick = onClear,
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun AliciaBatchActionChip(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
) {
    val background = when {
        primary -> Brush.verticalGradient(
            colors = listOf(Color(0xFF4A91FF), Color(0xFF1E63FF), Color(0xFF1A55D8)),
        )
        danger -> Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF4F1), Color(0xFFFFE6E0)),
        )
        else -> Brush.verticalGradient(
            colors = listOf(Color.White, Color(0xFFF1F6FF)),
        )
    }
    val textColor = when {
        primary -> Color.White
        danger -> Color(0xFFD65D42)
        else -> Color(0xFF5E718E)
    }

    Surface(
        modifier = Modifier
            .then(modifier)
            .clip(RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(background, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) textColor else textColor.copy(alpha = 0.45f),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun AliciaMechaWideActionButton(
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AliciaMechaActionButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        tone = if (danger) AliciaMechaActionButtonTone.Danger else AliciaMechaActionButtonTone.Primary,
        height = 42.dp,
    )
}

@Composable
private fun MoveTargetDialog(
    moveTargets: List<StorageNode>,
    loading: Boolean,
    selectedTargetId: Long?,
    onSelectTarget: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AliciaMechaDialogShell(
        title = "批量移动到",
        onDismissRequest = onDismiss,
        supporting = {
            Text(
                text = "选择新的落点目录后，系统会批量调整这些文件的层级关系。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2B67E7))
                    }
                } else {
                    MoveTargetOptionRow(
                        label = "根目录",
                        selected = selectedTargetId == null,
                        onClick = { onSelectTarget(null) },
                    )
                    moveTargets.forEach { folder ->
                        MoveTargetOptionRow(
                            label = folder.name,
                            selected = selectedTargetId == folder.id,
                            onClick = { onSelectTarget(folder.id) },
                        )
                    }
                }
            }
        },
        footer = {
            AliciaMechaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                confirmLabel = if (loading) "加载中..." else "确认移动",
                enabled = !loading,
                confirmLoading = false,
            )
        },
    )
}

@Composable
private fun MoveTargetOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AliciaMechaPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        backgroundResId = if (selected) R.drawable.alicia_9_team_summary else R.drawable.alicia_9_file_row,
        backgroundSlice = 30.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (selected) Color(0xFF2B67E7) else Color(0xFF101626),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                lineHeight = 20.sp,
            )
            if (selected) {
                AliciaMechaTeamCompactButton(
                    label = "当前选择",
                    minWidth = 84.dp,
                    height = 28.dp,
                )
            }
        }
    }
}

@Composable
private fun TeamScreen(
    paddingValues: PaddingValues,
    team: TeamUiState,
    baseUrl: String,
    currentUser: User,
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
        AliciaMechaBackdrop(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AliciaMechaDesignSpec.pagePadding,
                    top = 10.dp,
                    end = AliciaMechaDesignSpec.pagePadding,
                    bottom = 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(AliciaMechaDesignSpec.contentGap),
            ) {
                item {
                    AliciaMechaTeamHeader(
                        title = "账号管理",
                        actionLabel = if (team.isCreatingUser) "创建中..." else "新增账号",
                        actionEnabled = !team.isCreatingUser,
                        onCreateUser = onCreateUser,
                    )
                }

                item {
                    AliciaMechaPanel(
                        modifier = Modifier.padding(top = 10.dp),
                        backgroundResId = R.drawable.alicia_9_team_summary,
                        backgroundSlice = 60.dp,
                        contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 14.dp),
                    ) {
                        AliciaRoundedBadge(text = "账号概览")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AliciaMechaMetricCard(
                                    title = "总账号",
                                    value = team.users.size.toString(),
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.ManageAccounts,
                                )
                                AliciaMechaMetricCard(
                                    title = "管理员",
                                    value = adminCount.toString(),
                                    modifier = Modifier.weight(1f),
                                    accent = Color(0xFFFF9D24),
                                    icon = Icons.Rounded.AccountCircle,
                                )
                            }
                            AliciaMechaMetricCard(
                                title = "普通用户",
                                value = normalCount.toString(),
                                icon = Icons.Rounded.Preview,
                            )
                        }
                    }
                }

                item {
                    AliciaMechaPanel(
                        contentPadding = PaddingValues(0.dp),
                        backgroundResId = R.drawable.alicia_9_team_list,
                        backgroundSlice = 60.dp,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "成员列表",
                                    color = Color(0xFF101626),
                                    fontFamily = AliciaMechaFontFamily,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    lineHeight = 23.sp,
                                )
                                AliciaRoundedBadge(
                                    text = team.users.size.toString(),
                                    minWidth = 42.dp,
                                )
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
                                    Column(
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        team.users.forEach { user ->
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
    }
}

@Composable
private fun AliciaMechaTeamHeader(
    title: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onCreateUser: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 0.dp, end = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AliciaMechaTitleGraphic(
                title = title,
                variant = AliciaTitleGraphicVariant.Page,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF48D5FF), Color(0x001E63FF)),
                        ),
                    ),
            )
        }
        AliciaMechaTeamCompactButton(
            label = actionLabel,
            onClick = onCreateUser,
            enabled = actionEnabled,
            modifier = Modifier.padding(top = 12.dp),
            minWidth = 92.dp,
            height = 36.dp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    currentUser: User,
    baseUrl: String,
    avatarUrl: String?,
    isUpdatingProfile: Boolean,
    isUpdatingAvatar: Boolean,
    isChangingPassword: Boolean,
    onChangeAvatar: () -> Unit,
    onChangeNickname: () -> Unit,
    onChangePassword: () -> Unit,
    onSwitchBaseUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        AliciaMechaBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AliciaMechaPanel(
                    contentPadding = PaddingValues(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AliciaBadge(text = "账号面板")
                        Text(
                            text = formatRole(currentUser.role),
                            color = Color(0xFF748094),
                            fontFamily = AliciaMechaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AliciaMechaAvatarFrame(
                            label = currentUser.nickname,
                            imageUrl = avatarUrl,
                            frameSize = 92.dp,
                            avatarSize = 68.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = currentUser.nickname,
                                color = Color(0xFF101626),
                                fontFamily = AliciaMechaFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                lineHeight = 26.sp,
                            )
                            Text(
                                text = currentUser.phoneNumber,
                                color = Color(0xFF748094),
                                fontFamily = AliciaMechaFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AliciaMechaActionButton(
                            label = if (isUpdatingAvatar) "上传中..." else "修改头像",
                            onClick = onChangeAvatar,
                            tone = AliciaMechaActionButtonTone.Primary,
                            enabled = !isUpdatingAvatar,
                            modifier = Modifier.weight(1f),
                            height = 46.dp,
                        )
                        AliciaMechaActionButton(
                            label = if (isUpdatingProfile) "提交中..." else "修改昵称",
                            onClick = onChangeNickname,
                            tone = AliciaMechaActionButtonTone.Secondary,
                            enabled = !isUpdatingProfile,
                            modifier = Modifier.weight(1f),
                            height = 46.dp,
                        )
                        AliciaMechaActionButton(
                            label = if (isChangingPassword) "提交中..." else "修改密码",
                            onClick = onChangePassword,
                            tone = AliciaMechaActionButtonTone.Secondary,
                            enabled = !isChangingPassword,
                            modifier = Modifier.weight(1f),
                            height = 46.dp,
                        )
                    }
                }

                AliciaMechaPanel(
                    contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 14.dp),
                    backgroundResId = R.drawable.alicia_9_status,
                    backgroundSlice = 44.dp,
                ) {
                    val normalizedBaseUrl = normalizeConfiguredBaseUrl(baseUrl)
                    val usingMainland = normalizedBaseUrl == MAINLAND_BASE_URL
                    val usingHongKong = normalizedBaseUrl == HONG_KONG_BASE_URL

                    Text(
                        text = "接入环境",
                        color = Color(0xFF101626),
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "切换地址后会退出当前登录，请重新认证。",
                        color = Color(0xFF748094),
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AliciaMechaActionButton(
                            label = "内地正式服",
                            onClick = { onSwitchBaseUrl(MAINLAND_BASE_URL) },
                            tone = if (usingMainland) AliciaMechaActionButtonTone.Primary else AliciaMechaActionButtonTone.Secondary,
                            modifier = Modifier.weight(1f),
                            height = 46.dp,
                        )
                        AliciaMechaActionButton(
                            label = "香港测试服",
                            onClick = { onSwitchBaseUrl(HONG_KONG_BASE_URL) },
                            tone = if (usingHongKong) AliciaMechaActionButtonTone.Primary else AliciaMechaActionButtonTone.Secondary,
                            modifier = Modifier.weight(1f),
                            height = 46.dp,
                        )
                    }
                }

                AliciaMechaPanel(
                    contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 14.dp),
                    backgroundResId = R.drawable.alicia_9_status,
                    backgroundSlice = 44.dp,
                ) {
                    Text(
                        text = "当前设备账号操作",
                        color = Color(0xFF101626),
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                    )
                    AliciaMechaActionButton(
                        label = "退出登录",
                        onClick = onLogout,
                        tone = AliciaMechaActionButtonTone.Danger,
                        modifier = Modifier.fillMaxWidth(),
                        height = 54.dp,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AliciaAccessEndpointHint(
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    val normalizedBaseUrl = normalizeConfiguredBaseUrl(baseUrl)
    val accessLabel = describeAccessEnvironment(normalizedBaseUrl)

    Text(
        text = "当前接入：$accessLabel",
        modifier = modifier,
        color = Color(0xFFD8E6FF),
        fontFamily = AliciaMechaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun ChangeNicknameDialog(
    currentNickname: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var nickname by rememberSaveable(currentNickname) { mutableStateOf(currentNickname) }

    AliciaMechaDialogShell(
        title = "修改昵称",
        onDismissRequest = onDismiss,
        dismissEnabled = !isSubmitting,
        supporting = {
            Text(
                text = "新的昵称会同步展示在首页、账号面板和管理列表中。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            AliciaMechaInputField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                label = "昵称",
                placeholder = "请输入新的昵称",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        footer = {
            AliciaMechaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = { onSubmit(nickname) },
                confirmLabel = if (isSubmitting) "提交中..." else "确认修改",
                enabled = !isSubmitting,
                confirmLoading = isSubmitting,
            )
        },
    )
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

    AliciaMechaDialogShell(
        title = "修改密码",
        onDismissRequest = onDismiss,
        dismissEnabled = !isSubmitting,
        supporting = {
            Text(
                text = "更新当前账号登录密码后，请使用新密码重新登录其他设备。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            AliciaMechaInputField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = "当前密码",
                placeholder = "请输入当前密码",
                visualTransformation = PasswordVisualTransformation(),
            )
            AliciaMechaInputField(
                value = newPassword,
                onValueChange = { newPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = "新密码",
                placeholder = "请输入新密码",
                visualTransformation = PasswordVisualTransformation(),
            )
            AliciaMechaInputField(
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
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        },
        footer = {
            AliciaMechaDialogActionRow(
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
    var quotaGb by rememberSaveable { mutableStateOf("50") }
    val role = UserRole.valueOf(roleName)
    val passwordMismatch = confirmPassword.isNotBlank() && confirmPassword != password
    val quotaInvalid = role == UserRole.USER && quotaGb.trim().toDoubleOrNull()?.let { it > 0 } != true

    AliciaMechaDialogShell(
        title = "新增账号",
        onDismissRequest = onDismiss,
        dismissEnabled = !isSubmitting,
        body = {
            AliciaMechaSegmentTabs(
                labels = listOf("管理员", "普通用户"),
                selectedIndex = if (role == UserRole.ADMIN) 0 else 1,
                onSelected = { index ->
                    roleName = if (index == 0) UserRole.ADMIN.name else UserRole.USER.name
                },
            )
            AliciaMechaInputField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                modifier = Modifier.fillMaxWidth(),
                label = "手机号",
                placeholder = "请输入 11 位手机号",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            AliciaMechaInputField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                label = "昵称",
                placeholder = "例如：项目成员",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            AliciaMechaInputField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = "初始密码",
                placeholder = "请输入初始密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            AliciaMechaInputField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = "确认密码",
                placeholder = "请再次输入初始密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (role == UserRole.USER) {
                AliciaMechaInputField(
                    value = quotaGb,
                    onValueChange = { quotaGb = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "最大额度（GB）",
                    placeholder = "例如：50",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { }),
                )
            } else {
                Text(
                    text = "管理员账号默认不限制个人存储额度。",
                    color = Color(0xFF748094),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            if (passwordMismatch) {
                Text(
                    text = "两次输入的密码不一致。",
                    color = Color(0xFFD84B2A),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            } else if (quotaInvalid) {
                Text(
                    text = "普通用户请填写大于 0 的 GB 额度。",
                    color = Color(0xFFD84B2A),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        },
        footer = {
            AliciaMechaDialogActionRow(
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

    AliciaMechaDialogShell(
        title = "修改额度",
        onDismissRequest = onDismiss,
        dismissEnabled = !isSubmitting,
        supporting = {
            Text(
                text = "${targetUser.nickname} · ${targetUser.phoneNumber}",
                color = Color(0xFF101626),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = "当前已用 ${formatBytes(targetUser.usedBytes)}，请按 GB 填写新的最大额度。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            AliciaMechaInputField(
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
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        },
        footer = {
            AliciaMechaDialogActionRow(
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

    AliciaMechaDialogShell(
        title = "重置密码",
        onDismissRequest = onDismiss,
        dismissEnabled = !isSubmitting,
        supporting = {
            Text(
                text = "${targetUser.nickname} · ${targetUser.phoneNumber}",
                color = Color(0xFF101626),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = "重置后旧登录状态会失效，请通知对方使用新密码重新登录。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            AliciaMechaInputField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = "新密码",
                placeholder = "请输入新密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            AliciaMechaInputField(
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
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        },
        footer = {
            AliciaMechaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = { onSubmit(password) },
                confirmLabel = if (isSubmitting) "重置中..." else "确认重置",
                enabled = !isSubmitting,
                confirmLoading = isSubmitting,
            )
        },
    )
}

@Composable
private fun ShareCreateDialog(
    node: StorageNode,
    baseUrl: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ShareCreateFormValues) -> Unit,
) {
    val expireLabels = listOf("1天", "7天", "30天", "永久")
    val expireDays = listOf<Int?>(1, 7, 30, null)
    var title by rememberSaveable(node.id) { mutableStateOf(node.name) }
    var expireIndex by rememberSaveable(node.id) { mutableStateOf(1) }
    var passwordEnabled by rememberSaveable(node.id) { mutableStateOf(false) }
    var password by rememberSaveable(node.id) { mutableStateOf("") }
    var allowSave by rememberSaveable(node.id) { mutableStateOf(true) }
    var allowDownload by rememberSaveable(node.id) { mutableStateOf(true) }
    val trimmedTitle = title.trim()
    val trimmedPassword = password.trim()
    val titleError = trimmedTitle.isBlank()
    val passwordError = passwordEnabled && trimmedPassword.length !in 4..32
    val canSubmit = !isSubmitting && !titleError && !passwordError

    AliciaMechaDialogShell(
        title = "创建分享",
        onDismissRequest = onDismiss,
        dismissEnabled = !isSubmitting,
        supporting = {
            Text(
                text = "为“${node.name}”生成访问链接。当前接入 ${describeAccessEnvironment(baseUrl)}。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            AliciaMechaInputField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = "分享标题",
                placeholder = node.name,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (titleError) {
                Text(
                    text = "分享标题不能为空。",
                    color = Color(0xFFD84B2A),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }

            Text(
                text = "有效期",
                color = Color(0xFF7C879A),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
            AliciaMechaSegmentTabs(
                labels = expireLabels,
                selectedIndex = expireIndex,
                onSelected = { expireIndex = it },
            )

            Text(
                text = "提取码",
                color = Color(0xFF7C879A),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
            AliciaMechaSegmentTabs(
                labels = listOf("无提取码", "设置提取码"),
                selectedIndex = if (passwordEnabled) 1 else 0,
                onSelected = { index ->
                    passwordEnabled = index == 1
                    if (passwordEnabled && password.isBlank()) {
                        password = generateSharePassword()
                    }
                },
            )
            if (passwordEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AliciaMechaInputField(
                        value = password,
                        onValueChange = { value -> password = value.take(32) },
                        modifier = Modifier.weight(1f),
                        label = "分享提取码",
                        placeholder = "4 到 32 位",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    AliciaMechaActionButton(
                        label = "随机",
                        onClick = { password = generateSharePassword() },
                        tone = AliciaMechaActionButtonTone.Secondary,
                        enabled = !isSubmitting,
                        modifier = Modifier.width(82.dp),
                        height = 48.dp,
                    )
                }
                if (passwordError) {
                    Text(
                        text = "提取码长度需在 4 到 32 位之间。",
                        color = Color(0xFFD84B2A),
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }

            ShareToggleRow(
                title = "允许保存到网盘",
                subtitle = "对方登录后可以保存到自己的网盘",
                checked = allowSave,
                onToggle = { allowSave = !allowSave },
            )
            ShareToggleRow(
                title = "允许下载",
                subtitle = "对方登录后可以下载分享内文件",
                checked = allowDownload,
                onToggle = { allowDownload = !allowDownload },
            )
        },
        footer = {
            AliciaMechaActionButton(
                label = "取消",
                onClick = onDismiss,
                tone = AliciaMechaActionButtonTone.Secondary,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                height = 42.dp,
            )
            AliciaMechaActionButton(
                label = if (isSubmitting) "生成中..." else "生成分享",
                onClick = {
                    if (canSubmit) {
                        onSubmit(
                            ShareCreateFormValues(
                                title = trimmedTitle,
                                password = if (passwordEnabled) trimmedPassword else null,
                                expiresInDays = expireDays[expireIndex],
                                allowDownload = allowDownload,
                                allowSave = allowSave,
                            ),
                        )
                    }
                },
                tone = AliciaMechaActionButtonTone.Primary,
                enabled = canSubmit,
                modifier = Modifier.weight(1f),
                height = 42.dp,
            )
        },
    )
}

@Composable
private fun ShareToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    AliciaMechaPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        backgroundResId = if (checked) R.drawable.alicia_9_team_summary else R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = Color(0xFF101626),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF748094),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            AliciaMechaActionButton(
                label = if (checked) "已开启" else "已关闭",
                onClick = onToggle,
                tone = if (checked) AliciaMechaActionButtonTone.Primary else AliciaMechaActionButtonTone.Secondary,
                modifier = Modifier.width(84.dp),
                height = 34.dp,
            )
        }
    }
}

@Composable
private fun ShareCreatedDialog(
    share: CreatedShareState,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onSystemShare: () -> Unit,
) {
    AliciaMechaDialogShell(
        title = "分享已创建",
        onDismissRequest = onDismiss,
        supporting = {
            Text(
                text = "链接已生成。对方打开后需要登录查看详情；设置了提取码时会先校验提取码。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            ShareResultBlock(label = "分享标题", value = share.title)
            ShareResultBlock(label = "分享链接", value = share.shareUrl)
            if (!share.password.isNullOrBlank()) {
                ShareResultBlock(label = "提取码", value = share.password)
            }
            ShareResultBlock(
                label = "有效期",
                value = formatShareExpiresAt(share.expiresAt),
            )
            Text(
                text = "权限：${if (share.allowSave) "允许保存" else "禁止保存"} · ${if (share.allowDownload) "允许下载" else "禁止下载"}",
                color = Color(0xFF2B67E7),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            AliciaMechaWideActionButton(
                label = "复制分享信息",
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        footer = {
            AliciaMechaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = onSystemShare,
                dismissLabel = "完成",
                confirmLabel = "系统分享",
            )
        },
    )
}

@Composable
private fun IncomingShareDialog(
    state: IncomingShareUiState,
    isLoggedIn: Boolean,
    onDismiss: () -> Unit,
    onContinueLogin: () -> Unit,
    onVerifyPassword: (String) -> Unit,
    onSave: () -> Unit,
    onDownload: (StorageNode) -> Unit,
) {
    val status = state.status
    val detail = state.detail
    val needsPassword = status?.available == true &&
        status.requiresPassword &&
        state.shareAccessToken.isNullOrBlank()
    val needsLogin = status?.available == true && !needsPassword && !isLoggedIn
    var password by rememberSaveable(state.activeShareCode) { mutableStateOf("") }

    AliciaMechaDialogShell(
        title = status?.title ?: detail?.title ?: "分享详情",
        onDismissRequest = onDismiss,
        dismissEnabled = !state.passwordChecking && !state.saving,
        supporting = {
            Text(
                text = "来自 Alicia 云盘的访问分享，登录后可查看详情、保存或下载。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        },
        body = {
            when {
                state.statusLoading -> IncomingShareLoadingBlock("正在确认分享状态...")
                !state.error.isNullOrBlank() && detail == null -> IncomingShareMessageBlock(state.error)
                needsPassword -> {
                    IncomingShareMessageBlock("这个分享设置了提取码，通过校验后继续查看。")
                    AliciaMechaInputField(
                        value = password,
                        onValueChange = { password = it.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "提取码",
                        placeholder = "请输入分享提取码",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!state.passwordChecking) {
                                    onVerifyPassword(password)
                                }
                            },
                        ),
                    )
                    state.passwordError?.takeIf { it.isNotBlank() }?.let { error ->
                        Text(
                            text = error,
                            color = Color(0xFFD84B2A),
                            fontFamily = AliciaMechaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                needsLogin -> IncomingShareMessageBlock("登录后才能查看分享详情。点击“去登录”后完成登录，系统会继续打开这个分享。")
                state.detailLoading || detail == null -> IncomingShareLoadingBlock("正在加载分享详情...")
                else -> IncomingShareDetailBody(
                    detail = detail,
                    downloadingNodeId = state.downloadingNodeId,
                    onDownload = onDownload,
                )
            }
        },
        footer = {
            when {
                needsPassword -> {
                    AliciaMechaActionButton(
                        label = "取消",
                        onClick = onDismiss,
                        tone = AliciaMechaActionButtonTone.Secondary,
                        enabled = !state.passwordChecking,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                    )
                    AliciaMechaActionButton(
                        label = if (state.passwordChecking) "校验中..." else "校验",
                        onClick = { onVerifyPassword(password) },
                        tone = AliciaMechaActionButtonTone.Primary,
                        enabled = !state.passwordChecking,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                    )
                }
                needsLogin -> {
                    AliciaMechaActionButton(
                        label = "取消",
                        onClick = onDismiss,
                        tone = AliciaMechaActionButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                    )
                    AliciaMechaActionButton(
                        label = "去登录",
                        onClick = onContinueLogin,
                        tone = AliciaMechaActionButtonTone.Primary,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                    )
                }
                detail?.allowSave == true -> {
                    AliciaMechaActionButton(
                        label = "关闭",
                        onClick = onDismiss,
                        tone = AliciaMechaActionButtonTone.Secondary,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                    )
                    AliciaMechaActionButton(
                        label = if (state.saving) "保存中..." else "保存到网盘",
                        onClick = onSave,
                        tone = AliciaMechaActionButtonTone.Primary,
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                        height = 42.dp,
                    )
                }
                else -> {
                    AliciaMechaActionButton(
                        label = "关闭",
                        onClick = onDismiss,
                        tone = AliciaMechaActionButtonTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                        height = 42.dp,
                    )
                }
            }
        },
    )
}

@Composable
private fun IncomingShareLoadingBlock(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color(0xFF2B67E7),
            strokeWidth = 2.5.dp,
        )
        Text(
            text = message,
            color = Color(0xFF748094),
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun IncomingShareMessageBlock(message: String) {
    AliciaMechaPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 13.dp, end = 16.dp, bottom = 13.dp),
        backgroundResId = R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        Text(
            text = message,
            color = Color(0xFF748094),
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun IncomingShareDetailBody(
    detail: ShareLinkDetailResponse,
    downloadingNodeId: Long?,
    onDownload: (StorageNode) -> Unit,
) {
    val flattenedItems = remember(detail) { flattenIncomingShareItems(detail) }
    val displayedItems = flattenedItems.take(60)

    ShareResultBlock(label = "分享者", value = detail.ownerNickname)
    ShareResultBlock(label = "有效期", value = formatShareExpiresAt(detail.expiresAt))
    Text(
        text = "权限：${if (detail.allowSave) "允许保存" else "禁止保存"} · ${if (detail.allowDownload) "允许下载" else "禁止下载"}",
        color = Color(0xFF2B67E7),
        fontFamily = AliciaMechaFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Text(
        text = "分享内容",
        color = Color(0xFF7C879A),
        fontFamily = AliciaMechaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 14.sp,
    )

    if (displayedItems.isEmpty()) {
        IncomingShareMessageBlock("分享内容暂不可用。")
    } else {
        displayedItems.forEach { item ->
            IncomingShareNodeRow(
                node = item.node,
                depth = item.depth,
                allowDownload = detail.allowDownload,
                downloading = downloadingNodeId == item.node.id,
                onDownload = onDownload,
            )
        }
    }

    if (flattenedItems.size > displayedItems.size) {
        Text(
            text = "已展示前 ${displayedItems.size} 项，保存到网盘后可查看完整内容。",
            color = Color(0xFF748094),
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun IncomingShareNodeRow(
    node: StorageNode,
    depth: Int,
    allowDownload: Boolean,
    downloading: Boolean,
    onDownload: (StorageNode) -> Unit,
) {
    AliciaMechaPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 10).dp),
        contentPadding = PaddingValues(start = 13.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        backgroundResId = R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (node.type == StorageNodeType.FOLDER) Icons.Rounded.FolderOpen else Icons.Rounded.Preview,
                contentDescription = node.name,
                tint = if (node.type == StorageNodeType.FOLDER) Color(0xFF2B67E7) else Color(0xFF748094),
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = node.name,
                    color = Color(0xFF101626),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatNodeMeta(node),
                    color = Color(0xFF748094),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (node.type == StorageNodeType.FILE && allowDownload) {
                AliciaMechaTeamCompactButton(
                    label = if (downloading) "保存中" else "下载",
                    onClick = { onDownload(node) },
                    enabled = !downloading,
                    minWidth = 58.dp,
                    height = 28.dp,
                )
            }
        }
    }
}

@Composable
private fun ShareResultBlock(
    label: String,
    value: String,
) {
    AliciaMechaPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        backgroundResId = R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = Color(0xFF7C879A),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
            SelectionContainer {
                Text(
                    text = value,
                    color = Color(0xFF101626),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

private data class IncomingShareTreeItem(
    val node: StorageNode,
    val depth: Int,
)

private fun flattenIncomingShareItems(detail: ShareLinkDetailResponse): List<IncomingShareTreeItem> {
    val nodeById = detail.items.associateBy { it.id }
    val rootIdSet = detail.rootNodeIds.toSet()
    val childNodesByParentId = detail.items
        .filter { it.parentId != null && it.id !in rootIdSet }
        .groupBy { it.parentId }
    val visitedNodeIds = mutableSetOf<Long>()
    val flattenedItems = mutableListOf<IncomingShareTreeItem>()

    fun sorted(nodes: List<StorageNode>): List<StorageNode> =
        nodes.sortedWith(
            compareBy<StorageNode> { it.type != StorageNodeType.FOLDER }
                .thenBy { it.name.lowercase(Locale.getDefault()) },
        )

    fun visit(node: StorageNode, depth: Int) {
        if (!visitedNodeIds.add(node.id)) {
            return
        }

        flattenedItems += IncomingShareTreeItem(node = node, depth = depth)
        sorted(childNodesByParentId[node.id].orEmpty()).forEach { child ->
            visit(child, depth + 1)
        }
    }

    val roots = detail.rootNodeIds
        .mapNotNull(nodeById::get)
        .ifEmpty {
            detail.items.filter { item ->
                item.parentId == null || item.parentId !in nodeById
            }
        }

    sorted(roots).forEach { root -> visit(root, 0) }

    return flattenedItems
}

private fun generateSharePassword(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return buildString {
        repeat(6) {
            append(alphabet[Random.nextInt(alphabet.length)])
        }
    }
}

private fun formatShareExpiresAt(expiresAt: String?): String =
    expiresAt
        ?.takeIf { it.isNotBlank() }
        ?.replace('T', ' ')
        ?: "永久有效"

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
    onShare: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = Color(0xC4111826),
        dragHandle = null,
        tonalElevation = 0.dp,
    ) {
        AliciaMechaPanel(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentPadding = PaddingValues(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 16.dp),
            backgroundResId = R.drawable.alicia_9_dialog_panel,
            backgroundSlice = 72.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFB4BACA)),
                )
            }
            Text(
                text = node.name,
                color = Color(0xFF101626),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatNodeMeta(node),
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
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
                            hint = "图片、文本、PDF 与音视频支持内置预览",
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
                        icon = Icons.Rounded.Share,
                        label = "分享",
                        hint = "生成访问链接，可设置提取码和权限",
                        onClick = onShare,
                    )
                    SheetActionButton(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "移入回收站",
                        hint = "稍后还可以恢复",
                        onClick = onMoveToTrash,
                        danger = true,
                    )
                }
            }
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
    AliciaMechaPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(start = 16.dp, top = 13.dp, end = 16.dp, bottom = 13.dp),
        backgroundResId = R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (danger) Color(0xFFE45E2D) else Color(0xFF2B67E7),
                modifier = Modifier.size(30.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    color = if (danger) Color(0xFFE45E2D) else Color(0xFF101626),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = hint,
                    color = Color(0xFF748094),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun AppUpdateDialog(
    updateInfo: AppUpdateState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AliciaMechaDialogShell(
        title = "发现新版本",
        onDismissRequest = onDismiss,
        supporting = {
            Text(
                text = "当前版本 ${updateInfo.currentVersionName}",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = "最新版本 ${updateInfo.latestVersionName}",
                color = Color(0xFF101626),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                lineHeight = 20.sp,
            )
        },
        body = {
            Text(
                text = "检测到新的 Alicia 云盘客户端，建议尽快更新以获得最新功能与修复。",
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            AliciaMechaPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
                backgroundResId = R.drawable.alicia_9_file_row,
                backgroundSlice = 42.dp,
            ) {
                Text(
                    text = "更新说明",
                    color = Color(0xFF101626),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = updateInfo.releaseNotes.ifBlank { "本次更新暂未填写详细说明。" },
                    color = Color(0xFF748094),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        },
        footer = {
            AliciaMechaDialogActionRow(
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                dismissLabel = "稍后",
                confirmLabel = "立即更新",
            )
        },
    )
}

@Composable
private fun PreviewDialog(
    state: FilePreviewState,
    onDismiss: () -> Unit,
) {
    if (!state.visible) {
        return
    }

    AliciaMechaDialogShell(
        title = state.fileName,
        onDismissRequest = onDismiss,
        body = {
            when {
                state.loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2B67E7))
                        Text(
                            text = "正在加载预览…",
                            color = Color(0xFF748094),
                            fontFamily = AliciaMechaFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                    }
                }

                state.error != null -> PreviewFallbackText(message = state.error)
                state.kind == PreviewKind.TEXT -> TextPreviewContent(textContent = state.textContent)
                state.kind == PreviewKind.IMAGE && !state.previewUrl.isNullOrBlank() -> ImagePreviewContent(
                    previewUrl = state.previewUrl,
                    fileName = state.fileName,
                )
                state.kind == PreviewKind.PDF && !state.localFilePath.isNullOrBlank() -> PdfPreviewContent(
                    filePath = state.localFilePath,
                )
                state.kind == PreviewKind.VIDEO && !state.previewUrl.isNullOrBlank() -> VideoPreviewContent(
                    previewUrl = state.previewUrl,
                )
                state.kind == PreviewKind.AUDIO && !state.previewUrl.isNullOrBlank() -> AudioPreviewContent(
                    previewUrl = state.previewUrl,
                )
                else -> PreviewFallbackText(message = "当前文件暂不支持内置预览，请先下载查看。")
            }
        },
        footer = {
            AliciaMechaActionButton(
                label = "关闭",
                onClick = onDismiss,
                tone = AliciaMechaActionButtonTone.Secondary,
                modifier = Modifier.fillMaxWidth(),
                height = 44.dp,
            )
        },
    )
}

@Composable
private fun PreviewFallbackText(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier,
        color = Color(0xFF748094),
        fontFamily = AliciaMechaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun TextPreviewContent(textContent: String) {
    SelectionContainer {
        Text(
            text = if (textContent.isBlank()) "文件内容为空。" else textContent,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            color = Color(0xFF101626),
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ImagePreviewContent(
    previewUrl: String,
    fileName: String,
) {
    AliciaMechaPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
        backgroundResId = R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        SubcomposeAsyncImage(
            model = previewUrl,
            contentDescription = fileName,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color(0xFF2B67E7))
                }
            },
            error = {
                PreviewFallbackText(message = "图片预览加载失败，请先下载查看。")
            },
        )
    }
}

@Composable
private fun PdfPreviewContent(filePath: String) {
    val document = remember(filePath) {
        runCatching { PdfPreviewDocument(filePath) }.getOrNull()
    }
    var pageIndex by rememberSaveable(filePath) { mutableStateOf(0) }
    val pageCount = document?.pageCount ?: 0
    val pageBitmap = remember(filePath, pageIndex) {
        document?.renderPage(pageIndex)
    }

    DisposableEffect(document) {
        onDispose { document?.close() }
    }

    DisposableEffect(pageBitmap) {
        onDispose { pageBitmap?.recycle() }
    }

    if (document == null || pageBitmap == null) {
        PreviewFallbackText(message = "PDF 预览加载失败，请先下载查看。")
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AliciaMechaActionButton(
                    label = "上一页",
                    onClick = { pageIndex = max(pageIndex - 1, 0) },
                    tone = AliciaMechaActionButtonTone.Secondary,
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                    enabled = pageIndex > 0,
                )
                Text(
                    text = "${pageIndex + 1} / $pageCount",
                    color = Color(0xFF4B5563),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                AliciaMechaActionButton(
                    label = "下一页",
                    onClick = { pageIndex = minOf(pageIndex + 1, pageCount - 1) },
                    tone = AliciaMechaActionButtonTone.Secondary,
                    modifier = Modifier.weight(1f),
                    height = 40.dp,
                    enabled = pageIndex < pageCount - 1,
                )
            }
        }

        AliciaMechaPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(10.dp),
            backgroundResId = R.drawable.alicia_9_file_row,
            backgroundSlice = 42.dp,
        ) {
            Image(
                bitmap = pageBitmap.asImageBitmap(),
                contentDescription = "PDF 页面预览",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun VideoPreviewContent(previewUrl: String) {
    var isPreparing by remember(previewUrl) { mutableStateOf(true) }
    var errorMessage by remember(previewUrl) { mutableStateOf<String?>(null) }
    var videoView by remember(previewUrl) { mutableStateOf<VideoView?>(null) }

    DisposableEffect(previewUrl) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    AliciaMechaPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
        backgroundResId = R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF020817)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        videoView = this
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(Uri.parse(previewUrl))
                        setOnPreparedListener {
                            isPreparing = false
                            errorMessage = null
                            controller.show(0)
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            isPreparing = false
                            errorMessage = "视频预览加载失败，请先下载查看。"
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.tag != previewUrl) {
                        view.tag = previewUrl
                        isPreparing = true
                        errorMessage = null
                        view.setVideoURI(Uri.parse(previewUrl))
                        view.start()
                    }
                },
            )

            if (isPreparing) {
                CircularProgressIndicator(color = Color(0xFF2B67E7))
            }

            errorMessage?.let { message ->
                PreviewFallbackText(
                    message = message,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioPreviewContent(previewUrl: String) {
    var isPreparing by remember(previewUrl) { mutableStateOf(true) }
    var isPlaying by remember(previewUrl) { mutableStateOf(false) }
    var durationMs by remember(previewUrl) { mutableStateOf(0) }
    var positionMs by remember(previewUrl) { mutableStateOf(0f) }
    var errorMessage by remember(previewUrl) { mutableStateOf<String?>(null) }
    var mediaPlayer by remember(previewUrl) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(previewUrl) {
        val player = MediaPlayer()
        mediaPlayer = player

        runCatching {
            player.setDataSource(previewUrl)
            player.setOnPreparedListener { prepared ->
                isPreparing = false
                durationMs = prepared.duration.coerceAtLeast(0)
                positionMs = 0f
                errorMessage = null
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs.toFloat()
            }
            player.setOnErrorListener { _, _, _ ->
                isPreparing = false
                isPlaying = false
                errorMessage = "音频预览加载失败，请先下载查看。"
                true
            }
            player.prepareAsync()
        }.onFailure {
            isPreparing = false
            errorMessage = "音频预览加载失败，请先下载查看。"
        }

        onDispose {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            player.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(mediaPlayer, isPlaying, isPreparing) {
        while (mediaPlayer != null && isPlaying && !isPreparing) {
            positionMs = mediaPlayer?.currentPosition?.toFloat() ?: positionMs
            delay(250)
        }
    }

    AliciaMechaPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        backgroundResId = R.drawable.alicia_9_file_row,
        backgroundSlice = 42.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                errorMessage != null -> PreviewFallbackText(message = errorMessage!!)
                isPreparing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2B67E7))
                    }
                }

                else -> {
                    Text(
                        text = "${formatMediaTimestamp(positionMs.roundToInt())} / ${formatMediaTimestamp(durationMs)}",
                        color = Color(0xFF4B5563),
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                    Slider(
                        value = positionMs.coerceIn(0f, durationMs.toFloat().coerceAtLeast(0f)),
                        onValueChange = { next ->
                            positionMs = next
                        },
                        onValueChangeFinished = {
                            mediaPlayer?.seekTo(positionMs.roundToInt())
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(0f),
                        enabled = durationMs > 0,
                    )
                    AliciaMechaActionButton(
                        label = if (isPlaying) "暂停播放" else "开始播放",
                        onClick = {
                            val player = mediaPlayer ?: return@AliciaMechaActionButton
                            if (isPlaying) {
                                player.pause()
                                positionMs = player.currentPosition.toFloat()
                                isPlaying = false
                            } else {
                                player.seekTo(positionMs.roundToInt())
                                player.start()
                                isPlaying = true
                            }
                        },
                        tone = AliciaMechaActionButtonTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                        height = 42.dp,
                    )
                }
            }
        }
    }
}

private class PdfPreviewDocument(filePath: String) {
    private val descriptor = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int
        get() = renderer.pageCount

    fun renderPage(index: Int, maxWidthPx: Int = 1440): Bitmap? {
        if (index !in 0 until renderer.pageCount) {
            return null
        }

        renderer.openPage(index).use { page ->
            val scale = if (page.width > maxWidthPx) maxWidthPx.toFloat() / page.width else 1f
            val bitmapWidth = max((page.width * scale).roundToInt(), 1)
            val bitmapHeight = max((page.height * scale).roundToInt(), 1)
            return Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    fun close() {
        renderer.close()
        descriptor.close()
    }
}

private fun formatMediaTimestamp(totalMillis: Int): String {
    val totalSeconds = (totalMillis.coerceAtLeast(0) / 1000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun AliciaMechaConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    confirmTone: AliciaMechaActionButtonTone = AliciaMechaActionButtonTone.Primary,
) {
    AliciaMechaDialogShell(
        title = title,
        onDismissRequest = onDismiss,
        body = {
            Text(
                text = message,
                color = Color(0xFF748094),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        },
        footer = {
            AliciaMechaConfirmActionRow(
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                confirmLabel = confirmLabel,
                confirmTone = confirmTone,
            )
        },
    )
}

@Composable
private fun RowScope.AliciaMechaConfirmActionRow(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    confirmTone: AliciaMechaActionButtonTone,
) {
    AliciaMechaActionButton(
        label = "取消",
        onClick = onDismiss,
        tone = AliciaMechaActionButtonTone.Secondary,
        modifier = Modifier.weight(1f),
        height = 42.dp,
    )
    AliciaMechaActionButton(
        label = confirmLabel,
        onClick = onConfirm,
        tone = confirmTone,
        modifier = Modifier.weight(1f),
        height = 42.dp,
    )
}
