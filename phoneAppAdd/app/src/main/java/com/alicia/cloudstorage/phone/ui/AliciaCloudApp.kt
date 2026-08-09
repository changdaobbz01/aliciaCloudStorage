package com.alicia.cloudstorage.phone.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.alicia.cloudstorage.phone.BuildConfig
import com.alicia.cloudstorage.phone.FileDetailActivity
import com.alicia.cloudstorage.phone.HONG_KONG_BASE_URL
import com.alicia.cloudstorage.phone.MAINLAND_BASE_URL
import com.alicia.cloudstorage.phone.R
import com.alicia.cloudstorage.phone.ShareCreateActivity
import com.alicia.cloudstorage.phone.data.AppTab
import com.alicia.cloudstorage.phone.data.StorageFileCategory
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeFilter
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.User
import com.alicia.cloudstorage.phone.data.UserRole
import com.alicia.cloudstorage.phone.data.isAdmin
import java.io.File

private val ScreenBackground = Color(0xFFF7F8FD)
private val PanelWhite = Color(0xFFFFFFFF)
private val PrimaryBlue = Color(0xFF0B6BFF)
private val PrimaryBlueDeep = Color(0xFF005BFF)
private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF8993A6)
private val SoftText = Color(0xFFADB5C4)
private val SoftLine = Color(0xFFEDEFF6)
private val SoftBlue = Color(0xFFEAF2FF)
private val Danger = Color(0xFFE84D3D)
private val Success = Color(0xFF16B56F)
private val WarmOrange = Color(0xFFFF7A1A)

private fun Modifier.addCardChrome(shape: RoundedCornerShape): Modifier =
    shadow(
        elevation = 3.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.012f),
        spotColor = Color.Black.copy(alpha = 0.02f),
    ).border(1.dp, SoftLine.copy(alpha = 0.76f), shape)

private data class NodeActionContext(
    val node: StorageNode,
    val isTrashMode: Boolean,
)

private data class FileCategorySpec(
    val category: StorageFileCategory?,
    val label: String,
    val icon: AliciaGlyph,
)

private val fileCategories = listOf(
    FileCategorySpec(null, "全部", AliciaGlyph.Folder),
    FileCategorySpec(StorageFileCategory.IMAGE, "相册", AliciaGlyph.ImageFile),
    FileCategorySpec(StorageFileCategory.VIDEO, "视频", AliciaGlyph.VideoFile),
    FileCategorySpec(StorageFileCategory.DOCUMENT, "文档", AliciaGlyph.DocumentFile),
    FileCategorySpec(StorageFileCategory.AUDIO, "音频", AliciaGlyph.AudioFile),
    FileCategorySpec(StorageFileCategory.ARCHIVE, "压缩包", AliciaGlyph.ArchiveFile),
)

private enum class AliciaGlyph {
    Search,
    Refresh,
    Filter,
    ChevronDown,
    ListView,
    GridView,
    Folder,
    Trash,
    ImageFile,
    VideoFile,
    DocumentFile,
    AudioFile,
    ArchiveFile,
    Download,
    Share,
    Move,
    Restore,
    Check,
    Home,
    Person,
    Plus,
}

private enum class ReferenceAsset(@DrawableRes val resId: Int) {
    HomeBlack(R.drawable.ic_add_home_black),
    HomeBlue(R.drawable.ic_add_home_blue),
    FolderBlack(R.drawable.ic_add_folder_black),
    FolderBlue(R.drawable.ic_add_folder_blue),
    FolderSolidBlue(R.drawable.ic_add_folder_solid_blue),
    FolderOrange(R.drawable.ic_add_folder_orange),
    FolderGray(R.drawable.ic_add_folder_gray),
    TransferBlack(R.drawable.ic_add_transfer_black),
    TransferBlue(R.drawable.ic_add_transfer_blue),
    MeBlack(R.drawable.ic_add_me_black),
    MeBlue(R.drawable.ic_add_me_blue),
    SearchGray(R.drawable.ic_add_search_gray),
    SearchBlue(R.drawable.ic_add_search_blue),
    ListBlack(R.drawable.ic_add_list_black),
    ListBlue(R.drawable.ic_add_list_blue),
    GridBlack(R.drawable.ic_add_grid_black),
    GridBlue(R.drawable.ic_add_grid_blue),
    FilterBlack(R.drawable.ic_add_filter_black),
    RefreshBlack(R.drawable.ic_add_refresh_black),
    ChevronDownBlack(R.drawable.ic_add_chevron_down_black),
    ChevronRightGray(R.drawable.ic_add_chevron_right_gray),
    BackBlack(R.drawable.ic_add_back_black),
    CheckBlueCircle(R.drawable.ic_add_check_blue_circle),
    DownloadBlack(R.drawable.ic_add_download_black),
    UploadBlue(R.drawable.ic_add_upload_blue),
    UploadFileBlue(R.drawable.ic_add_upload_file_blue),
    CloudUploadBlue(R.drawable.ic_add_cloud_upload_blue),
    NewFolderBlue(R.drawable.ic_add_new_folder_blue),
    ShareBlack(R.drawable.ic_add_share_black),
    MoveBlack(R.drawable.ic_add_move_black),
    DeleteRed(R.drawable.ic_add_delete_red),
    PlusBlue(R.drawable.ic_add_plus_blue),
    BellBlack(R.drawable.ic_add_bell_black),
    ExpandBlack(R.drawable.ic_add_expand_black),
    MoreBlack(R.drawable.ic_add_more_black),
    EyeGray(R.drawable.ic_add_eye_gray),
    InfoBlack(R.drawable.ic_add_info_black),
    CloseBlack(R.drawable.ic_add_close_black),
    VideoColor(R.drawable.ic_add_video_color),
    PhotoColor(R.drawable.ic_add_photo_color),
    DocumentColor(R.drawable.ic_add_document_color),
    AudioColor(R.drawable.ic_add_audio_color),
    ArchiveColor(R.drawable.ic_add_archive_color),
    TrashGray(R.drawable.ic_add_trash_gray),
    PdfRed(R.drawable.ic_add_pdf_red),
    DocBlue(R.drawable.ic_add_doc_blue),
    ImageGray(R.drawable.ic_add_image_gray),
    VideoGray(R.drawable.ic_add_video_gray),
    DocumentGray(R.drawable.ic_add_document_gray),
    AudioGray(R.drawable.ic_add_audio_gray),
    ArchiveGray(R.drawable.ic_add_archive_gray),
    RetryBlue(R.drawable.ic_add_retry_blue),
    CancelRed(R.drawable.ic_add_cancel_red),
    RestoreGreen(R.drawable.ic_add_restore_action),
    EditBlue(R.drawable.ic_add_edit_blue),
    ProfileSettings(R.drawable.ic_add_profile_settings),
    AccountSecurity(R.drawable.ic_add_account_security),
    EnvironmentSettings(R.drawable.ic_add_environment_settings),
    StorageDetails(R.drawable.ic_add_storage_details),
    VersionUpdate(R.drawable.ic_add_version_update),
    AccountManage(R.drawable.ic_add_account_manage),
}

@Composable
private fun ReferenceIcon(
    asset: ReferenceAsset,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    opacity: Float = 1f,
) {
    Image(
        painter = painterResource(asset.resId),
        contentDescription = contentDescription,
        modifier = modifier.alpha(opacity),
        contentScale = ContentScale.Fit,
    )
}

private fun navReferenceAsset(glyph: AliciaGlyph, selected: Boolean): ReferenceAsset? =
    when (glyph) {
        AliciaGlyph.Home -> if (selected) ReferenceAsset.HomeBlue else ReferenceAsset.HomeBlack
        AliciaGlyph.Folder -> if (selected) ReferenceAsset.FolderSolidBlue else ReferenceAsset.FolderBlack
        AliciaGlyph.Download -> if (selected) ReferenceAsset.TransferBlue else ReferenceAsset.TransferBlack
        AliciaGlyph.Person -> if (selected) ReferenceAsset.MeBlue else ReferenceAsset.MeBlack
        else -> null
    }

private fun dockReferenceAsset(glyph: AliciaGlyph): ReferenceAsset? =
    when (glyph) {
        AliciaGlyph.Download -> ReferenceAsset.DownloadBlack
        AliciaGlyph.Share -> ReferenceAsset.ShareBlack
        AliciaGlyph.Move -> ReferenceAsset.MoveBlack
        AliciaGlyph.Trash -> ReferenceAsset.DeleteRed
        AliciaGlyph.Restore -> ReferenceAsset.RestoreGreen
        else -> null
    }

private fun categoryReferenceAsset(category: StorageFileCategory?): ReferenceAsset =
    when (category) {
        StorageFileCategory.VIDEO -> ReferenceAsset.VideoColor
        StorageFileCategory.IMAGE -> ReferenceAsset.PhotoColor
        StorageFileCategory.DOCUMENT -> ReferenceAsset.DocumentColor
        StorageFileCategory.AUDIO -> ReferenceAsset.AudioColor
        StorageFileCategory.ARCHIVE -> ReferenceAsset.ArchiveColor
        null -> ReferenceAsset.FolderSolidBlue
    }

private fun nodeReferenceAsset(node: StorageNode): ReferenceAsset =
    when {
        node.type == StorageNodeType.FOLDER -> ReferenceAsset.FolderSolidBlue
        node.mimeType.orEmpty().startsWith("image/") -> ReferenceAsset.PhotoColor
        node.mimeType.orEmpty().startsWith("video/") -> ReferenceAsset.VideoColor
        node.mimeType.orEmpty().startsWith("audio/") -> ReferenceAsset.AudioColor
        node.extension.equals("zip", true) || node.extension.equals("rar", true) || node.extension.equals("7z", true) -> ReferenceAsset.ArchiveColor
        node.extension.equals("pdf", true) -> ReferenceAsset.PdfRed
        node.extension.equals("doc", true) || node.extension.equals("docx", true) -> ReferenceAsset.DocBlue
        else -> ReferenceAsset.DocumentColor
    }

private enum class TransferStatusFilter {
    ALL,
    FINISHED,
    RUNNING,
    FAILED,
}

private enum class MePage {
    MAIN,
    PROFILE,
    SECURITY,
    ENVIRONMENT,
    STORAGE,
    UPDATES,
    ADMIN,
}

@Composable
fun AliciaCloudApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                submitting = uiState.isSubmittingLogin,
                onLogin = viewModel::login,
            )

            else -> MainShell(
                uiState = uiState,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
            )
        }

        if (uiState.authToken.isNullOrBlank()) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }

        if (uiState.incomingShare.activeShareCode != null) {
            IncomingSharePage(
                state = uiState.incomingShare,
                loggedIn = !uiState.authToken.isNullOrBlank(),
                baseUrl = uiState.baseUrl,
                authToken = uiState.authToken.orEmpty(),
                onDismiss = viewModel::closeIncomingShare,
                onContinueLogin = viewModel::dismissIncomingShareLoginNotice,
                onVerifyPassword = viewModel::verifyIncomingSharePassword,
                onToggleFolder = viewModel::toggleIncomingShareFolder,
                onToggleNode = viewModel::toggleIncomingShareNodeSelection,
                onOpenSaveTarget = viewModel::openIncomingShareSaveTargetPicker,
                onCloseSaveTarget = viewModel::closeIncomingShareSaveTargetPicker,
                onSelectSaveTarget = viewModel::selectIncomingShareSaveTarget,
                onSave = viewModel::saveIncomingShareToDrive,
            )
        }
    }

    uiState.incomingShare.prompt?.let {
        IncomingSharePromptDialog(
            onDismissRequest = viewModel::dismissIncomingSharePrompt,
            onConfirm = viewModel::confirmIncomingSharePrompt,
        )
    }

    uiState.appUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAppUpdate,
            title = { Text("发现新版本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${update.currentVersionName} -> ${update.latestVersionName}")
                    if (update.releaseNotes.isNotBlank()) {
                        Text(update.releaseNotes, color = Muted)
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::openAppUpdateDownload) {
                    Text("立即更新")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAppUpdate) {
                    Text("稍后")
                }
            },
        )
    }
}

@Composable
private fun BootScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEFF6FF), ScreenBackground, Color.White),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(74.dp)
                    .addCardChrome(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
            ) {
                ReferenceIcon(ReferenceAsset.FolderSolidBlue, contentDescription = null, modifier = Modifier.padding(17.dp))
            }
            Text("Alicia 云盘", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("正在同步你的云端空间", color = Muted)
            CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun LoginScreen(
    baseUrl: String,
    submitting: Boolean,
    onLogin: (String, String) -> Unit,
) {
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Surface(
            modifier = Modifier
                .size(72.dp)
                .addCardChrome(RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
        ) {
            ReferenceIcon(ReferenceAsset.FolderSolidBlue, contentDescription = null, modifier = Modifier.padding(16.dp))
        }
        Text("Alicia 云盘", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("腾讯 COS 文件工作台", color = Muted, fontSize = 18.sp)
        Card(
            modifier = Modifier.addCardChrome(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onLogin(phoneNumber, password) }),
                )
                Button(
                    onClick = { onLogin(phoneNumber, password) },
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(if (submitting) "登录中" else "登录")
                }
            }
        }
        Text("当前接入：$baseUrl", color = Muted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    uiState: AppUiState,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val currentUser = uiState.currentUser ?: return
    val context = LocalContext.current
    val isTrashMode = uiState.selectedTab == AppTab.TRASH
    val explorer = if (isTrashMode) uiState.trash else uiState.files
    val visibleTab = when (uiState.selectedTab) {
        AppTab.HOME -> AppTab.HOME
        AppTab.FILES, AppTab.TRASH -> AppTab.FILES
        AppTab.TRANSFERS -> AppTab.TRANSFERS
        AppTab.TEAM, AppTab.ME -> AppTab.ME
    }

    var uploadSheetOpen by rememberSaveable { mutableStateOf(false) }
    var createFolderOpen by rememberSaveable { mutableStateOf(false) }
    var actionContext by remember { mutableStateOf<NodeActionContext?>(null) }
    var shareSelectionWarning by remember { mutableStateOf<String?>(null) }
    var moveSheetOpen by rememberSaveable { mutableStateOf(false) }
    var moveTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var createUserOpen by rememberSaveable { mutableStateOf(false) }
    var quotaUser by remember { mutableStateOf<User?>(null) }
    var passwordUser by remember { mutableStateOf<User?>(null) }
    var pendingDownloadNode by remember { mutableStateOf<StorageNode?>(null) }
    var pendingArchiveNodeIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            viewModel.uploadDocuments(uris)
        }
    }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.uploadAvatar(uri)
        }
    }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val node = pendingDownloadNode
        pendingDownloadNode = null
        if (uri != null && node != null) {
            viewModel.downloadFileToUri(node, uri)
        }
    }
    val saveArchiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val nodeIds = pendingArchiveNodeIds
        pendingArchiveNodeIds = emptyList()
        if (uri != null && nodeIds.isNotEmpty()) {
            viewModel.downloadArchiveToUri(nodeIds, uri)
        }
    }
    val shareCreateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && ShareCreateActivity.shareCreated(result.data)) {
            viewModel.clearNodeSelection(false)
        }
    }

    LaunchedEffect(shareSelectionWarning) {
        shareSelectionWarning?.let { warning ->
            snackbarHostState.showSnackbar(warning)
            shareSelectionWarning = null
        }
    }

    fun openShareSelection(nodes: List<StorageNode>) {
        val uniqueNodes = nodes.distinctBy(StorageNode::id)
        when {
            uniqueNodes.isEmpty() -> shareSelectionWarning = "请先选择要分享的文件或文件夹。"
            uniqueNodes.size > 20 -> shareSelectionWarning = "单个分享最多包含 20 个项目。"
            else -> shareCreateLauncher.launch(
                ShareCreateActivity.createIntent(
                    context = context,
                    nodes = uniqueNodes,
                    baseUrl = uiState.baseUrl,
                    authToken = uiState.authToken.orEmpty(),
                ),
            )
        }
    }

    fun downloadNode(node: StorageNode) {
        if (node.type == StorageNodeType.FILE) {
            pendingDownloadNode = node
            saveFileLauncher.launch(node.name.ifBlank { "download.bin" })
        } else {
            pendingArchiveNodeIds = listOf(node.id)
            saveArchiveLauncher.launch("${node.name.ifBlank { "AliciaCloud" }}.zip")
        }
    }

    val fileDetailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && FileDetailActivity.contentChanged(result.data)) {
            viewModel.refreshAfterFileDetailMutation()
        }
    }

    fun openFileDetail(node: StorageNode) {
        fileDetailLauncher.launch(
            FileDetailActivity.createIntent(
                context = context,
                node = node,
                baseUrl = uiState.baseUrl,
                authToken = uiState.authToken.orEmpty(),
            ),
        )
    }

    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        floatingActionButton = {
            val selectionActive = visibleTab == AppTab.FILES && explorer.selectedNodeIds.isNotEmpty()
            val addAvailable = visibleTab == AppTab.HOME || visibleTab == AppTab.FILES || visibleTab == AppTab.TRANSFERS
            if (addAvailable && !isTrashMode && !selectionActive) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(18.dp, CircleShape, ambientColor = PrimaryBlue.copy(alpha = 0.24f), spotColor = PrimaryBlue.copy(alpha = 0.38f))
                        .clip(CircleShape)
                        .noRippleClickable {
                            if (visibleTab == AppTab.HOME) {
                                viewModel.applyFileCategory(null)
                            }
                            uploadSheetOpen = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ReferenceIcon(
                        asset = ReferenceAsset.PlusBlue,
                        contentDescription = "新增",
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.55f),
                    )
                }
            }
        },
        bottomBar = {
            AliciaBottomBar(
                selectedTab = visibleTab,
                onSelect = viewModel::selectTab,
            )
        },
    ) { paddingValues ->
        when (uiState.selectedTab) {
            AppTab.HOME -> HomeScreen(
                paddingValues = paddingValues,
                user = currentUser,
                baseUrl = uiState.baseUrl,
                authToken = uiState.authToken.orEmpty(),
                home = uiState.home,
                onRefresh = viewModel::refreshCurrentTab,
                onOpenCategory = viewModel::applyFileCategory,
                onOpenTrash = { viewModel.selectTab(AppTab.TRASH) },
                onOpenFiles = { viewModel.selectTab(AppTab.FILES) },
                onOpenRecentNode = viewModel::revealNodeInFiles,
            )

            AppTab.FILES, AppTab.TRASH -> FilesScreen(
                paddingValues = paddingValues,
                explorer = explorer,
                trashMode = isTrashMode,
                transfers = uiState.transfers,
                baseUrl = uiState.baseUrl,
                authToken = uiState.authToken.orEmpty(),
                onRefresh = viewModel::refreshCurrentTab,
                onSwitchTrash = { trash -> viewModel.selectTab(if (trash) AppTab.TRASH else AppTab.FILES) },
                onKeywordChange = { value ->
                    if (isTrashMode) viewModel.updateTrashKeyword(value) else viewModel.updateFileKeyword(value)
                },
                onSearch = {
                    if (isTrashMode) viewModel.submitTrashSearch() else viewModel.submitFileSearch()
                },
                onCategory = viewModel::applyFileCategory,
                onFilter = viewModel::applyFileFilter,
                onTrashFilter = viewModel::applyTrashFilter,
                onCrumb = viewModel::jumpToCrumb,
                onNodeClick = { node ->
                    if (explorer.selectedNodeIds.isNotEmpty()) {
                        viewModel.toggleNodeSelection(isTrashMode, node.id)
                    } else if (isTrashMode) {
                        actionContext = NodeActionContext(node, true)
                    } else if (node.type == StorageNodeType.FOLDER) {
                        viewModel.openNode(node)
                    } else {
                        openFileDetail(node)
                    }
                },
                onNodeLongPress = { node -> viewModel.toggleNodeSelection(isTrashMode, node.id) },
                onToggleSelection = { node -> viewModel.toggleNodeSelection(isTrashMode, node.id) },
                onClearSelection = { viewModel.clearNodeSelection(isTrashMode) },
                onSelectAll = { viewModel.selectAllVisibleNodes(isTrashMode) },
                onDownloadSelected = {
                    val ids = explorer.selectedNodeIds.toList()
                    pendingArchiveNodeIds = ids
                    saveArchiveLauncher.launch(suggestedArchiveName(explorer.items, ids))
                },
                onShareSelected = {
                    openShareSelection(explorer.items.filter { it.id in explorer.selectedNodeIds })
                },
                onMoveSelected = {
                    moveTargetId = null
                    moveSheetOpen = true
                    viewModel.loadMoveTargets()
                },
                onTrashSelected = viewModel::moveSelectedNodesToTrash,
                onRestoreSelected = viewModel::restoreSelectedNodes,
                onDeleteSelectedForever = viewModel::permanentlyDeleteSelectedNodes,
            )

            AppTab.TRANSFERS -> TransferScreen(
                paddingValues = paddingValues,
                tasks = uiState.transfers,
                selectedTab = uiState.transferPanelTab,
                onTab = viewModel::selectTransferPanelTab,
                onCancel = viewModel::cancelTransfer,
                onRetryDownload = viewModel::retryDownloadTransfer,
            )

            AppTab.TEAM -> TeamScreen(
                paddingValues = paddingValues,
                team = uiState.team,
                currentUser = currentUser,
                onRefresh = viewModel::refreshCurrentTab,
                onCreateUser = { createUserOpen = true },
                onEditQuota = { quotaUser = it },
                onResetPassword = { passwordUser = it },
            )

            AppTab.ME -> MeScreen(
                paddingValues = paddingValues,
                user = currentUser,
                baseUrl = uiState.baseUrl,
                avatarUrl = remember(uiState.baseUrl, currentUser.id, currentUser.avatarUrl) {
                    resolveUserAvatarUrl(uiState.baseUrl, currentUser)
                },
                updatingAvatar = uiState.isUpdatingAvatar,
                updatingProfile = uiState.isUpdatingProfile,
                changingPassword = uiState.isChangingPassword,
                team = uiState.team,
                onRefresh = viewModel::refreshCurrentTab,
                onAvatar = { avatarLauncher.launch(arrayOf("image/*")) },
                onNickname = viewModel::updateNickname,
                onPassword = viewModel::changePassword,
                onSwitchBaseUrl = viewModel::switchBaseUrl,
                onLogout = viewModel::logout,
                onCreateUser = { createUserOpen = true },
                onEditQuota = { quotaUser = it },
                onResetPassword = { passwordUser = it },
            )
        }
    }

    if (uploadSheetOpen) {
        UploadSheet(
            onDismiss = { uploadSheetOpen = false },
            onUpload = { mimeTypes ->
                uploadSheetOpen = false
                uploadLauncher.launch(mimeTypes)
            },
            onCreateFolder = {
                uploadSheetOpen = false
                createFolderOpen = true
            },
        )
    }

    if (createFolderOpen) {
        CreateFolderDialog(
            creating = uiState.files.isCreatingFolder,
            onDismiss = { createFolderOpen = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                createFolderOpen = false
            },
        )
    }

    actionContext?.let { target ->
        NodeActionSheet(
            target = target,
            busy = if (target.isTrashMode) uiState.trash.actionNodeId == target.node.id else uiState.files.actionNodeId == target.node.id,
            onDismiss = { actionContext = null },
            onPreview = {
                actionContext = null
                if (target.isTrashMode) {
                    viewModel.previewFile(target.node)
                } else {
                    openFileDetail(target.node)
                }
            },
            onDownload = {
                actionContext = null
                downloadNode(target.node)
            },
            onShare = {
                actionContext = null
                openShareSelection(listOf(target.node))
            },
            onMove = {
                actionContext = null
                viewModel.clearNodeSelection(false)
                viewModel.toggleNodeSelection(false, target.node.id)
                moveTargetId = null
                moveSheetOpen = true
                viewModel.loadMoveTargets()
            },
            onTrash = {
                actionContext = null
                viewModel.moveNodeToTrash(target.node)
            },
            onRestore = {
                actionContext = null
                viewModel.restoreNode(target.node)
            },
            onDeleteForever = {
                actionContext = null
                viewModel.permanentlyDeleteNode(target.node)
            },
        )
    }

    if (moveSheetOpen) {
        MoveTargetSheet(
            explorer = uiState.files,
            selectedTargetId = moveTargetId,
            onSelect = { moveTargetId = it },
            onDismiss = { moveSheetOpen = false },
            onMove = {
                viewModel.moveSelectedNodes(moveTargetId) {
                    moveSheetOpen = false
                }
            },
        )
    }

    if (createUserOpen) {
        CreateUserDialog(
            creating = uiState.team.isCreatingUser,
            onDismiss = { createUserOpen = false },
            onCreate = { phone, nickname, password, role, quota ->
                viewModel.createUser(phone, nickname, password, role, quota) {
                    createUserOpen = false
                }
            },
        )
    }

    quotaUser?.let { user ->
        UpdateQuotaDialog(
            user = user,
            busy = uiState.team.quotaUserId == user.id,
            onDismiss = { quotaUser = null },
            onSubmit = { quota ->
                viewModel.updateUserQuota(user, quota) {
                    quotaUser = null
                }
            },
        )
    }

    passwordUser?.let { user ->
        ResetPasswordDialog(
            user = user,
            busy = uiState.team.passwordUserId == user.id,
            onDismiss = { passwordUser = null },
            onSubmit = { password ->
                viewModel.resetUserPassword(user, password) {
                    passwordUser = null
                }
            },
        )
    }

    if (uiState.preview.visible) {
        PreviewDialog(
            state = uiState.preview,
            onDismiss = viewModel::closePreview,
        )
    }
}

@Composable
private fun AliciaBottomBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    val items = listOf(
        AppTab.HOME to ("首页" to AliciaGlyph.Home),
        AppTab.FILES to ("文件" to AliciaGlyph.Folder),
        AppTab.TRANSFERS to ("传输" to AliciaGlyph.Download),
        AppTab.ME to ("我的" to AliciaGlyph.Person),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black.copy(alpha = 0.025f), spotColor = Color.Black.copy(alpha = 0.04f)),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(76.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (tab, pair) ->
                AliciaNavItem(
                    label = pair.first,
                    glyph = pair.second,
                    selected = selectedTab == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AliciaNavItem(
    label: String,
    glyph: AliciaGlyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) PrimaryBlueDeep else Color(0xFF121826)
    val referenceAsset = navReferenceAsset(glyph, selected)
        ?: if (selected) ReferenceAsset.FolderSolidBlue else ReferenceAsset.FolderBlack
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ReferenceIcon(
            asset = referenceAsset,
            contentDescription = label,
            modifier = Modifier
                .size(34.dp)
                .scale(1.5f),
        )
        Text(
            label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeScreen(
    paddingValues: PaddingValues,
    user: User,
    baseUrl: String,
    authToken: String,
    home: HomeUiState,
    onRefresh: () -> Unit,
    onOpenCategory: (StorageFileCategory?) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenRecentNode: (StorageNode) -> Unit,
) {
    val avatarUrl = remember(baseUrl, user.id, user.avatarUrl) { resolveUserAvatarUrl(baseUrl, user) }
    val overview = home.overview
    val usedBytes = overview?.usedBytes ?: user.usedBytes
    val totalBytes = overview?.totalSpaceBytes ?: user.storageQuotaBytes
    val recent = home.recentNodes.take(4)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(ScreenBackground),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alicia 云盘", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text("轻量文件工作台", color = Muted, fontSize = 14.sp)
                }
                Avatar(url = avatarUrl, fallback = user.nickname, size = 48.dp)
            }
        }

        item {
            HomeSearchPill(
                placeholder = "搜索网盘文件",
                onClick = onOpenFiles,
            )
        }

        item {
            HomeSpaceCard(
                user = user,
                avatarUrl = avatarUrl,
                usedBytes = usedBytes,
                totalBytes = totalBytes,
                onClick = onOpenFiles,
            )
        }

        item {
            SectionHeader(title = "文件分类")
            Spacer(modifier = Modifier.height(8.dp))
            HomeCategoryPanel(
                onOpenCategory = onOpenCategory,
                onOpenTrash = onOpenTrash,
            )
        }

        item {
            SectionHeader(
                title = "最近文件",
                actionAsset = ReferenceAsset.RefreshBlack,
                actionDescription = "刷新",
                onAction = onRefresh,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                home.loading -> LoadingCard("正在加载最近文件")
                home.error != null -> ErrorCard(home.error, onRefresh)
                home.recentNodes.isEmpty() -> EmptyCard("暂无最近文件")
                else -> HomeRecentPanel(
                    nodes = recent,
                    baseUrl = baseUrl,
                    authToken = authToken,
                    onOpenNode = onOpenRecentNode,
                )
            }
        }
    }
}

@Composable
private fun HomeSearchPill(
    placeholder: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .addCardChrome(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .noRippleClickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReferenceIcon(
                asset = ReferenceAsset.SearchGray,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(placeholder, color = SoftText, fontSize = 15.sp)
        }
    }
}

@Composable
private fun HomeSpaceCard(
    user: User,
    avatarUrl: String?,
    usedBytes: Long,
    totalBytes: Long?,
    onClick: () -> Unit,
) {
    val progressFraction = if (totalBytes != null && totalBytes > 0L) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .addCardChrome(RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .noRippleClickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(url = avatarUrl, fallback = user.nickname, size = 54.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("我的空间", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFE8EDF7)),
                    ) {
                        if (progressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(PrimaryBlueDeep),
                            )
                        }
                    }
                    Text("${formatBytes(usedBytes)} / ${totalBytes?.let(::formatBytes) ?: "无限制"}", color = PrimaryBlueDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                ReferenceIcon(
                    asset = ReferenceAsset.ChevronRightGray,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeCategoryPanel(
    onOpenCategory: (StorageFileCategory?) -> Unit,
    onOpenTrash: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .addCardChrome(RoundedCornerShape(20.dp)),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                HomeCategoryItem("视频", ReferenceAsset.VideoColor) { onOpenCategory(StorageFileCategory.VIDEO) }
                HomeCategoryItem("相册", ReferenceAsset.PhotoColor) { onOpenCategory(StorageFileCategory.IMAGE) }
                HomeCategoryItem("文档", ReferenceAsset.DocumentColor) { onOpenCategory(StorageFileCategory.DOCUMENT) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                HomeCategoryItem("音频", ReferenceAsset.AudioColor) { onOpenCategory(StorageFileCategory.AUDIO) }
                HomeCategoryItem("压缩包", ReferenceAsset.ArchiveColor) { onOpenCategory(StorageFileCategory.ARCHIVE) }
                HomeCategoryItem("回收站", ReferenceAsset.TrashGray, onClick = onOpenTrash)
            }
        }
    }
}

@Composable
private fun HomeCategoryItem(
    label: String,
    asset: ReferenceAsset,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ReferenceIcon(
            asset = asset,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .scale(1.5f),
        )
        Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeRecentPanel(
    nodes: List<StorageNode>,
    baseUrl: String,
    authToken: String,
    onOpenNode: (StorageNode) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .addCardChrome(RoundedCornerShape(22.dp)),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            nodes.forEach { node ->
                CompactNodeCard(
                    node = node,
                    baseUrl = baseUrl,
                    authToken = authToken,
                    onClick = { onOpenNode(node) },
                    elevated = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilesScreen(
    paddingValues: PaddingValues,
    explorer: ExplorerUiState,
    trashMode: Boolean,
    transfers: List<TransferTask>,
    baseUrl: String,
    authToken: String,
    onRefresh: () -> Unit,
    onSwitchTrash: (Boolean) -> Unit,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCategory: (StorageFileCategory?) -> Unit,
    onFilter: (StorageNodeFilter) -> Unit,
    onTrashFilter: (StorageNodeFilter) -> Unit,
    onCrumb: (Int) -> Unit,
    onNodeClick: (StorageNode) -> Unit,
    onNodeLongPress: (StorageNode) -> Unit,
    onToggleSelection: (StorageNode) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDeleteSelectedForever: () -> Unit,
) {
    val selectedCount = explorer.selectedNodeIds.size
    val title = when {
        trashMode -> "回收站"
        else -> "文件"
    }
    var gridMode by rememberSaveable(trashMode, explorer.category) {
        mutableStateOf(explorer.category == StorageFileCategory.IMAGE || explorer.category == StorageFileCategory.VIDEO)
    }
    var filterSheetOpen by rememberSaveable(trashMode) { mutableStateOf(false) }
    val filterHandler = if (trashMode) onTrashFilter else onFilter

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(ScreenBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, top = 16.dp, end = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedCount > 0) {
                FileSelectionHeader(
                    count = selectedCount,
                    onClear = onClearSelection,
                    onSelectAll = onSelectAll,
                )
            } else {
                FileHeader(
                    title = title,
                    subtitle = if (trashMode) "管理已删除内容" else "文件、分类和批量操作",
                    onRefresh = onRefresh,
                )
            }

            if (selectedCount == 0) {
                FileLocationTabBar(
                    trashMode = trashMode,
                    onSwitchTrash = onSwitchTrash,
                )
                FilesSearchField(
                    value = explorer.keyword,
                    placeholder = if (trashMode) "搜索回收站" else if (explorer.category != null) "搜索全盘${categoryLabel(explorer.category)}" else "搜索网盘文件",
                    onValueChange = onKeywordChange,
                    onSearch = onSearch,
                )
            }

            FileSortViewRow(
                gridMode = gridMode,
                onGridMode = { gridMode = it },
                onFilter = { filterSheetOpen = true },
            )

            if (!trashMode && explorer.category == null) {
                CrumbStrip(
                    breadcrumbs = explorer.breadcrumbs,
                    onCrumb = onCrumb,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    explorer.loading -> LoadingCard("正在加载文件")
                    explorer.error != null -> ErrorCard(explorer.error, onRefresh)
                    explorer.items.isEmpty() -> EmptyCard(if (trashMode) "回收站为空" else "这里还没有文件")
                    gridMode -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = rememberLazyGridState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        gridItems(explorer.items, key = { it.id }) { node ->
                            NodeGridCard(
                                node = node,
                                baseUrl = baseUrl,
                                authToken = authToken,
                                selected = node.id in explorer.selectedNodeIds,
                                highlighted = explorer.highlightedNodeId == node.id,
                                transfer = transfers.firstOrNull { task ->
                                    task.kind == TransferKind.DOWNLOAD &&
                                        node.id in task.sourceNodeIds &&
                                        task.isTransferActive()
                                },
                                onClick = { onNodeClick(node) },
                                onLongPress = { onNodeLongPress(node) },
                                onToggleSelection = { onToggleSelection(node) },
                            )
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(explorer.items, key = { it.id }) { node ->
                            NodeListItem(
                                node = node,
                                baseUrl = baseUrl,
                                authToken = authToken,
                                selected = node.id in explorer.selectedNodeIds,
                                highlighted = explorer.highlightedNodeId == node.id,
                                transfer = transfers.firstOrNull { task ->
                                    task.kind == TransferKind.DOWNLOAD &&
                                        node.id in task.sourceNodeIds &&
                                        task.isTransferActive()
                                },
                                onClick = { onNodeClick(node) },
                                onLongPress = { onNodeLongPress(node) },
                                onToggleSelection = { onToggleSelection(node) },
                            )
                        }
                    }
                }
            }
        }

        if (selectedCount > 0) {
            SelectionActionDock(
                trashMode = trashMode,
                busy = explorer.isBatchActing,
                onDownload = onDownloadSelected,
                onShare = onShareSelected,
                shareEnabled = !trashMode,
                onMove = onMoveSelected,
                onTrash = onTrashSelected,
                onRestore = onRestoreSelected,
                onDeleteForever = onDeleteSelectedForever,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp),
            )
        }
    }

    if (filterSheetOpen) {
        FileFilterSheet(
            gridMode = gridMode,
            trashMode = trashMode,
            explorer = explorer,
            onDismiss = { filterSheetOpen = false },
            onApply = { draftGridMode, draftCategory, draftFilter ->
                gridMode = draftGridMode
                if (trashMode) {
                    filterHandler(draftFilter)
                } else {
                    if (draftCategory != explorer.category) {
                        onCategory(draftCategory)
                    } else if (draftCategory == null && draftFilter != explorer.filter) {
                        filterHandler(draftFilter)
                    }
                }
                filterSheetOpen = false
            },
        )
    }
}

@Composable
private fun FileLocationTabBar(
    trashMode: Boolean,
    onSwitchTrash: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnderlineTabButton(
            label = "文件",
            selected = !trashMode,
            onClick = { onSwitchTrash(false) },
        )
        UnderlineTabButton(
            label = "回收站",
            selected = trashMode,
            onClick = { onSwitchTrash(true) },
        )
    }
}

@Composable
private fun FileHeader(
    title: String,
    subtitle: String,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        HeaderIconButton(ReferenceAsset.RefreshBlack, "刷新", onRefresh)
    }
}

@Composable
private fun HeaderIconButton(
    asset: ReferenceAsset,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ReferenceIcon(
            asset = asset,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(32.dp)
                .scale(1.6f),
        )
    }
}

@Composable
private fun FileSelectionHeader(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "取消",
            color = Ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .noRippleClickable(onClick = onClear)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Text(
            "已选 $count 项",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            "全选",
            color = Ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .noRippleClickable(onClick = onSelectAll)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FilesSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .border(1.5.dp, PrimaryBlueDeep, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            cursorColor = PrimaryBlueDeep,
        ),
        leadingIcon = {
            ReferenceIcon(
                asset = ReferenceAsset.SearchGray,
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .scale(1.45f),
            )
        },
        placeholder = { Text(placeholder, color = SoftText, fontSize = 15.sp) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryBlueDeep.copy(alpha = 0.10f))
                    .noRippleClickable(onClick = onSearch)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("搜索", color = PrimaryBlueDeep, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        },
    )
}

@Composable
private fun FileSortViewRow(
    gridMode: Boolean,
    onGridMode: (Boolean) -> Unit,
    onFilter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .noRippleClickable(onClick = onFilter)
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("综合排序", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            ReferenceIcon(
                asset = ReferenceAsset.ChevronDownBlack,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FileToolbarIconButton(
            asset = if (gridMode) ReferenceAsset.ListBlack else ReferenceAsset.ListBlue,
            contentDescription = "列表视图",
            onClick = { onGridMode(false) },
        )
        Spacer(Modifier.width(6.dp))
        FileToolbarIconButton(
            asset = if (gridMode) ReferenceAsset.GridBlue else ReferenceAsset.GridBlack,
            contentDescription = "宫格视图",
            onClick = { onGridMode(true) },
        )
        Spacer(Modifier.width(6.dp))
        FileToolbarIconButton(
            asset = ReferenceAsset.FilterBlack,
            contentDescription = "筛选设置",
            onClick = onFilter,
        )
    }
}

@Composable
private fun FileToolbarIconButton(
    asset: ReferenceAsset,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ReferenceIcon(
            asset = asset,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(30.dp)
                .scale(1.6f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FileFilterSheet(
    gridMode: Boolean,
    trashMode: Boolean,
    explorer: ExplorerUiState,
    onDismiss: () -> Unit,
    onApply: (Boolean, StorageFileCategory?, StorageNodeFilter) -> Unit,
) {
    var draftGridMode by rememberSaveable { mutableStateOf(gridMode) }
    var draftCategory by rememberSaveable { mutableStateOf(explorer.category) }
    var draftFilter by rememberSaveable { mutableStateOf(explorer.filter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFD4D8E2)),
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("筛选设置", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilterSheetModeButton(
                    label = "列表视图",
                    asset = if (draftGridMode) ReferenceAsset.ListBlack else ReferenceAsset.ListBlue,
                    selected = !draftGridMode,
                    onClick = { draftGridMode = false },
                    modifier = Modifier.weight(1f),
                )
                FilterSheetModeButton(
                    label = "宫格视图",
                    asset = if (draftGridMode) ReferenceAsset.GridBlue else ReferenceAsset.GridBlack,
                    selected = draftGridMode,
                    onClick = { draftGridMode = true },
                    modifier = Modifier.weight(1f),
                )
            }

            if (!trashMode) {
                Text("文件类型", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    fileCategories.forEach { category ->
                        FilterSheetChoice(
                            label = filterCategoryLabel(category.category),
                            selected = draftCategory == category.category,
                            onClick = {
                                draftCategory = category.category
                                draftFilter = if (category.category == null) StorageNodeFilter.ALL else StorageNodeFilter.FILE
                            },
                            modifier = Modifier.width(86.dp),
                        )
                    }
                }
            }

            if (draftCategory == null) {
                Text("内容类型", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        StorageNodeFilter.ALL to "全部",
                        StorageNodeFilter.FOLDER to "文件夹",
                        StorageNodeFilter.FILE to "文件",
                    ).forEach { (filter, label) ->
                        FilterSheetChoice(
                            label = label,
                            selected = draftFilter == filter,
                            onClick = { draftFilter = filter },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterFooterButton(
                    label = "重置",
                    onClick = {
                        draftGridMode = false
                        draftCategory = null
                        draftFilter = StorageNodeFilter.ALL
                    },
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
                FilterFooterButton(
                    label = "确定",
                    onClick = { onApply(draftGridMode, draftCategory, draftFilter) },
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterSheetModeButton(
    label: String,
    asset: ReferenceAsset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .noRippleClickable(onClick = onClick)
            .border(1.2.dp, if (selected) PrimaryBlueDeep.copy(alpha = 0.72f) else Color.Transparent, RoundedCornerShape(14.dp)),
        color = if (selected) Color.White else Color(0xFFF0F2F6),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReferenceIcon(
                asset = asset,
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .scale(1.6f),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) PrimaryBlueDeep else Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FilterSheetChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .noRippleClickable(onClick = onClick)
            .border(
                width = 1.2.dp,
                color = if (selected) PrimaryBlueDeep.copy(alpha = 0.72f) else Color.Transparent,
                shape = RoundedCornerShape(13.dp),
            ),
        color = if (selected) SoftBlue else Color(0xFFF5F6FA),
        shape = RoundedCornerShape(13.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (selected) PrimaryBlueDeep else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilterFooterButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) PrimaryBlueDeep else Color(0xFFEFF1F6))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color.White else Ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun AliciaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .noRippleClickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) PrimaryBlueDeep.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            ),
        color = if (selected) SoftBlue else Color(0xFFF4F6FA),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (selected) PrimaryBlueDeep else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CrumbStrip(
    breadcrumbs: List<com.alicia.cloudstorage.phone.data.FolderCrumb>,
    onCrumb: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        breadcrumbs.forEachIndexed { index, crumb ->
            item {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .noRippleClickable { onCrumb(index) },
                    color = Color(0xFFF1F3F8),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (index == 0) {
                            ReferenceIcon(
                                ReferenceAsset.HomeBlue,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(1.5f),
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(crumb.label, color = if (index == breadcrumbs.lastIndex) PrimaryBlueDeep else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionAsset: ReferenceAsset? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (actionAsset != null && actionDescription != null && onAction != null) {
            HeaderIconButton(actionAsset, actionDescription, onAction)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeListItem(
    node: StorageNode,
    baseUrl: String,
    authToken: String,
    selected: Boolean,
    highlighted: Boolean,
    transfer: TransferTask?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val borderColor = when {
        highlighted -> PrimaryBlue.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleCombinedClickable(onClick = onClick, onLongClick = onLongPress)
            .border(if (highlighted) 1.2.dp else 0.dp, borderColor, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NodeThumbnailBox(
                node = node,
                baseUrl = baseUrl,
                authToken = authToken,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = if (node.type == StorageNodeType.FILE && node.mimeType.orEmpty().startsWith("image/")) {
                    nodeThumbnailBackground(node)
                } else {
                    Color.Transparent
                },
                fallbackIconScale = 1.45f,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(node.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatNodeMeta(node), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (transfer != null) {
                    LinearProgressIndicator(
                        progress = { (transfer.progressPercent ?: 0) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = PrimaryBlue,
                        trackColor = Color(0xFFE7EDF8),
                    )
                }
            }
            SelectionCircle(selected = selected, onClick = onToggleSelection, size = 22.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeGridCard(
    node: StorageNode,
    baseUrl: String,
    authToken: String,
    selected: Boolean,
    highlighted: Boolean,
    transfer: TransferTask?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleCombinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = if (highlighted) 1.3.dp else 0.dp,
                    color = when {
                        highlighted -> PrimaryBlue.copy(alpha = 0.65f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(16.dp),
                ),
        ) {
            NodeThumbnailBox(
                node = node,
                baseUrl = baseUrl,
                authToken = authToken,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                fallbackIconScale = 2f,
            )
            SelectionCircle(
                selected = selected,
                onClick = onToggleSelection,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
                size = 22.dp,
            )
            if (transfer != null) {
                LinearProgressIndicator(
                    progress = { (transfer.progressPercent ?: 0) / 100f },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
                    color = PrimaryBlueDeep,
                    trackColor = Color(0xFFE7EDF8),
                )
            }
        }
        Text(node.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(gridNodeMeta(node), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NodeThumbnailBox(
    node: StorageNode,
    baseUrl: String,
    authToken: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    backgroundColor: Color = nodeThumbnailBackground(node),
    fallbackIconScale: Float = 1f,
) {
    val context = LocalContext.current
    val previewable = node.type == StorageNodeType.FILE && node.mimeType.orEmpty().startsWith("image/")
    val model = remember(baseUrl, authToken, node.id, previewable) {
        if (previewable && authToken.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(storageFileDownloadUrl(baseUrl, node.id))
                .addHeader("Authorization", "Bearer $authToken")
                .crossfade(false)
                .build()
        } else {
            null
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = node.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { NodeThumbnailFallback(node, fallbackIconScale) },
                error = { NodeThumbnailFallback(node, fallbackIconScale) },
            )
        } else {
            NodeThumbnailFallback(node, fallbackIconScale)
        }
    }
}

@Composable
private fun NodeThumbnailFallback(node: StorageNode, iconScale: Float = 1f) {
    ReferenceIcon(
        asset = nodeReferenceAsset(node),
        contentDescription = null,
        modifier = Modifier
            .size(if (node.type == StorageNodeType.FOLDER) 30.dp else 36.dp)
            .scale(iconScale),
    )
}

@Composable
private fun SelectionCircle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    SelectionIndicator(
        selected = selected,
        modifier = modifier.noRippleClickable(onClick = onClick),
        size = size,
    )
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val borderColor = Color(0xFFCBD1DC)
    val fillColor = Color.White.copy(alpha = 0.68f)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            ReferenceIcon(
                asset = ReferenceAsset.CheckBlueCircle,
                contentDescription = "已选择",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.9f),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size - 3.dp)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(1.5.dp, borderColor, CircleShape),
            )
        }
    }
}

@Composable
private fun SelectionActionDock(
    trashMode: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    shareEnabled: Boolean,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.025f), spotColor = Color.Black.copy(alpha = 0.045f)),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (trashMode) {
                DockAction(AliciaGlyph.Restore, "恢复", Success, busy, onRestore)
                DockAction(AliciaGlyph.Trash, "删除", Danger, busy, onDeleteForever)
            } else {
                DockAction(AliciaGlyph.Download, "下载", Ink, busy, onDownload)
                DockAction(AliciaGlyph.Share, "分享", Ink, busy || !shareEnabled, onShare)
                DockAction(AliciaGlyph.Move, "移动", Ink, busy, onMove)
                DockAction(AliciaGlyph.Trash, "删除", Danger, busy, onTrash)
            }
        }
    }
}

@Composable
private fun DockAction(
    glyph: AliciaGlyph,
    label: String,
    tint: Color,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (disabled) SoftText else tint
    val referenceAsset = dockReferenceAsset(glyph) ?: ReferenceAsset.DownloadBlack
    Column(
        modifier = Modifier
            .width(60.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .noRippleClickable(enabled = !disabled, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ReferenceIcon(
            asset = referenceAsset,
            contentDescription = label,
            modifier = Modifier
                .size(32.dp)
                .scale(1.75f),
            opacity = if (disabled) 0.36f else 1f,
        )
        Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun TransferScreen(
    paddingValues: PaddingValues,
    tasks: List<TransferTask>,
    selectedTab: TransferPanelTab,
    onTab: (TransferPanelTab) -> Unit,
    onCancel: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
) {
    var statusFilter by rememberSaveable(selectedTab) { mutableStateOf(TransferStatusFilter.ALL) }
    val tabTasks = tasks.filter {
        when (selectedTab) {
            TransferPanelTab.DOWNLOADS -> it.kind == TransferKind.DOWNLOAD
            TransferPanelTab.UPLOADS -> it.kind == TransferKind.UPLOAD
        }
    }
    val visibleTasks = tabTasks.filter { task ->
        when (statusFilter) {
            TransferStatusFilter.ALL -> true
            TransferStatusFilter.FINISHED -> task.status == TransferStatus.COMPLETED
            TransferStatusFilter.RUNNING -> task.isTransferActive()
            TransferStatusFilter.FAILED -> task.status == TransferStatus.FAILED
        }
    }
    val groupedTasks = visibleTasks.groupBy(::transferDayLabel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(ScreenBackground),
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("传输列表", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        item {
            TransferTabBar(selectedTab = selectedTab, onTab = onTab)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item { TransferStatusButton("全部", statusFilter == TransferStatusFilter.ALL) { statusFilter = TransferStatusFilter.ALL } }
                item { TransferStatusButton("已完成", statusFilter == TransferStatusFilter.FINISHED) { statusFilter = TransferStatusFilter.FINISHED } }
                item { TransferStatusButton("进行中", statusFilter == TransferStatusFilter.RUNNING) { statusFilter = TransferStatusFilter.RUNNING } }
                item { TransferStatusButton("失败", statusFilter == TransferStatusFilter.FAILED) { statusFilter = TransferStatusFilter.FAILED } }
            }
        }
        if (visibleTasks.isEmpty()) {
            item {
                TransferEmptyState(if (selectedTab == TransferPanelTab.DOWNLOADS) "暂无下载记录" else "暂无上传记录")
            }
        } else {
            groupedTasks.forEach { (label, group) ->
                item {
                    Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                items(group, key = { it.id }) { task ->
                    TransferTaskCard(
                        task = task,
                        onCancel = { onCancel(task.id) },
                        onRetry = { onRetryDownload(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferTabBar(
    selectedTab: TransferPanelTab,
    onTab: (TransferPanelTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnderlineTabButton(
            label = "上传",
            selected = selectedTab == TransferPanelTab.UPLOADS,
            onClick = { onTab(TransferPanelTab.UPLOADS) },
        )
        UnderlineTabButton(
            label = "下载",
            selected = selectedTab == TransferPanelTab.DOWNLOADS,
            onClick = { onTab(TransferPanelTab.DOWNLOADS) },
        )
    }
}

@Composable
private fun UnderlineTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) PrimaryBlueDeep else Color.Transparent),
        )
    }
}

@Composable
private fun TransferStatusButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) SoftBlue else Color(0xFFF1F3F7))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) PrimaryBlueDeep else Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TransferEmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 44.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReferenceIcon(
            ReferenceAsset.FolderSolidBlue,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .scale(1.35f),
        )
        Text(message, color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MeScreen(
    paddingValues: PaddingValues,
    user: User,
    baseUrl: String,
    avatarUrl: String?,
    updatingAvatar: Boolean,
    updatingProfile: Boolean,
    changingPassword: Boolean,
    team: TeamUiState,
    onRefresh: () -> Unit,
    onAvatar: () -> Unit,
    onNickname: (String, () -> Unit) -> Unit,
    onPassword: (String, String, () -> Unit) -> Unit,
    onSwitchBaseUrl: (String) -> Unit,
    onLogout: () -> Unit,
    onCreateUser: () -> Unit,
    onEditQuota: (User) -> Unit,
    onResetPassword: (User) -> Unit,
) {
    var page by rememberSaveable(user.id) { mutableStateOf(MePage.MAIN) }
    var nickname by rememberSaveable(user.id) { mutableStateOf(user.nickname) }
    var oldPassword by rememberSaveable(user.id) { mutableStateOf("") }
    var newPassword by rememberSaveable(user.id) { mutableStateOf("") }

    when (page) {
        MePage.MAIN -> MeMainPage(
            paddingValues = paddingValues,
            user = user,
            avatarUrl = avatarUrl,
            updatingAvatar = updatingAvatar,
            baseUrl = baseUrl,
            onRefresh = onRefresh,
            onAvatar = onAvatar,
            onOpenPage = { page = it },
            onLogout = onLogout,
        )

        MePage.PROFILE -> MeProfilePage(
            paddingValues = paddingValues,
            nickname = nickname,
            updating = updatingProfile,
            avatarUrl = avatarUrl,
            user = user,
            onBack = { page = MePage.MAIN },
            onAvatar = onAvatar,
            onNicknameChange = { nickname = it },
            onSave = { onNickname(nickname) { page = MePage.MAIN } },
        )

        MePage.SECURITY -> MeSecurityPage(
            paddingValues = paddingValues,
            oldPassword = oldPassword,
            newPassword = newPassword,
            changing = changingPassword,
            onBack = { page = MePage.MAIN },
            onOldPasswordChange = { oldPassword = it },
            onNewPasswordChange = { newPassword = it },
            onSave = {
                onPassword(oldPassword, newPassword) {
                    oldPassword = ""
                    newPassword = ""
                    page = MePage.MAIN
                }
            },
        )

        MePage.ENVIRONMENT -> MeEnvironmentPage(
            paddingValues = paddingValues,
            baseUrl = baseUrl,
            onBack = { page = MePage.MAIN },
            onSwitchBaseUrl = onSwitchBaseUrl,
        )

        MePage.STORAGE -> MeStoragePage(
            paddingValues = paddingValues,
            user = user,
            onBack = { page = MePage.MAIN },
        )

        MePage.UPDATES -> MeUpdatePage(
            paddingValues = paddingValues,
            onBack = { page = MePage.MAIN },
        )

        MePage.ADMIN -> MeAdminPage(
            paddingValues = paddingValues,
            team = team,
            currentUser = user,
            onBack = { page = MePage.MAIN },
            onRefresh = onRefresh,
            onCreateUser = onCreateUser,
            onEditQuota = onEditQuota,
            onResetPassword = onResetPassword,
        )
    }
}

@Composable
private fun MeMainPage(
    paddingValues: PaddingValues,
    user: User,
    avatarUrl: String?,
    updatingAvatar: Boolean,
    baseUrl: String,
    onRefresh: () -> Unit,
    onAvatar: () -> Unit,
    onOpenPage: (MePage) -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(ScreenBackground),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("我的", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text("账号、空间和接入环境", color = Muted, fontSize = 14.sp)
                }
                HeaderIconButton(ReferenceAsset.RefreshBlack, "刷新", onRefresh)
            }
        }
        item {
            MeAccountCard(
                user = user,
                avatarUrl = avatarUrl,
                updatingAvatar = updatingAvatar,
                onAvatar = onAvatar,
                onOpenStorage = { onOpenPage(MePage.STORAGE) },
            )
        }
        item {
            MeMenuSection("账号设置") {
                MeListRow(ReferenceAsset.ProfileSettings, Color(0xFFEAF2FF), "个人资料", "头像、昵称") { onOpenPage(MePage.PROFILE) }
                MeListRow(ReferenceAsset.AccountSecurity, Color(0xFFF1EBFF), "账号安全", "修改登录密码") { onOpenPage(MePage.SECURITY) }
            }
        }
        item {
            MeMenuSection("空间与服务") {
                MeListRow(ReferenceAsset.EnvironmentSettings, Color(0xFFE5F6FF), "接入环境", baseUrl) { onOpenPage(MePage.ENVIRONMENT) }
                MeListRow(ReferenceAsset.StorageDetails, Color(0xFFEAF2FF), "空间详情", userUsageLabel(user)) { onOpenPage(MePage.STORAGE) }
                MeListRow(ReferenceAsset.VersionUpdate, Color(0xFFFFF0E7), "版本更新", "当前 ${BuildConfig.VERSION_NAME}") { onOpenPage(MePage.UPDATES) }
                if (user.isAdmin) {
                    MeListRow(ReferenceAsset.AccountManage, Color(0xFFE9F8F2), "账号管理", "用户、额度和密码") { onOpenPage(MePage.ADMIN) }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .addCardChrome(RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .noRippleClickable(onClick = onLogout),
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                    ReferenceIcon(
                        ReferenceAsset.CloseBlack,
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .scale(1.35f),
                    )
                    Text("退出登录", color = Danger, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MeAccountCard(
    user: User,
    avatarUrl: String?,
    updatingAvatar: Boolean,
    onAvatar: () -> Unit,
    onOpenStorage: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .addCardChrome(RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .noRippleClickable(onClick = onOpenStorage),
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(url = avatarUrl, fallback = user.nickname, size = 64.dp, onClick = onAvatar)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.nickname, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.phoneNumber, color = Muted, fontSize = 13.sp)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFF6F8FC)) {
                    Text(
                        if (updatingAvatar) "上传中" else if (user.isAdmin) "管理员" else "个人空间",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已用空间", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(userUsageLabel(user), color = PrimaryBlueDeep, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            MeStorageProgress(formatPercent(user.usedBytes, user.storageQuotaBytes) / 100f)
        }
    }
}

@Composable
private fun MeStorageProgress(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFE8EDF7)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(PrimaryBlueDeep),
        )
    }
}

@Composable
private fun MeMenuSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .addCardChrome(RoundedCornerShape(20.dp)),
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

@Composable
private fun MeListRow(
    asset: ReferenceAsset,
    iconBackground: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            ReferenceIcon(
                asset = asset,
                contentDescription = null,
                modifier = Modifier.size(27.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ReferenceIcon(
            ReferenceAsset.ChevronRightGray,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .scale(1.25f),
        )
    }
}

@Composable
private fun MePageScaffold(
    paddingValues: PaddingValues,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(ScreenBackground),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .noRippleClickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    ReferenceIcon(ReferenceAsset.BackBlack, contentDescription = "返回", modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text(subtitle, color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .addCardChrome(RoundedCornerShape(24.dp)),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
        }
    }
}

@Composable
private fun MeProfilePage(
    paddingValues: PaddingValues,
    nickname: String,
    updating: Boolean,
    avatarUrl: String?,
    user: User,
    onBack: () -> Unit,
    onAvatar: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    MePageScaffold(paddingValues, "个人资料", "头像和昵称", onBack) {
        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Avatar(url = avatarUrl, fallback = user.nickname, size = 88.dp, onClick = onAvatar)
        }
        OutlinedButton(onClick = onAvatar, enabled = !updating, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (updating) "头像上传中" else "更换头像")
        }
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text("昵称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Button(onClick = onSave, enabled = !updating, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (updating) "保存中" else "保存昵称")
        }
    }
}

@Composable
private fun MeSecurityPage(
    paddingValues: PaddingValues,
    oldPassword: String,
    newPassword: String,
    changing: Boolean,
    onBack: () -> Unit,
    onOldPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    MePageScaffold(paddingValues, "账号安全", "修改登录密码", onBack) {
        OutlinedTextField(
            value = oldPassword,
            onValueChange = onOldPasswordChange,
            label = { Text("当前密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = { Text("新密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Button(onClick = onSave, enabled = !changing, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (changing) "修改中" else "修改密码")
        }
    }
}

@Composable
private fun MeEnvironmentPage(
    paddingValues: PaddingValues,
    baseUrl: String,
    onBack: () -> Unit,
    onSwitchBaseUrl: (String) -> Unit,
) {
    MePageScaffold(paddingValues, "接入环境", "切换 API 服务", onBack) {
        Text(baseUrl, color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onSwitchBaseUrl(MAINLAND_BASE_URL) },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (baseUrl == MAINLAND_BASE_URL) PrimaryBlueDeep else Color(0xFFEAF2FF), contentColor = if (baseUrl == MAINLAND_BASE_URL) Color.White else PrimaryBlueDeep),
            ) { Text("国内") }
            Button(
                onClick = { onSwitchBaseUrl(HONG_KONG_BASE_URL) },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (baseUrl == HONG_KONG_BASE_URL) PrimaryBlueDeep else Color(0xFFEAF2FF), contentColor = if (baseUrl == HONG_KONG_BASE_URL) Color.White else PrimaryBlueDeep),
            ) { Text("香港") }
        }
    }
}

@Composable
private fun MeStoragePage(
    paddingValues: PaddingValues,
    user: User,
    onBack: () -> Unit,
) {
    val percent = formatPercent(user.usedBytes, user.storageQuotaBytes)
    MePageScaffold(paddingValues, "空间详情", "容量使用情况", onBack) {
        Text(userUsageLabel(user), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
            color = PrimaryBlueDeep,
            trackColor = Color(0xFFE8EDF7),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("已用 ${formatBytes(user.usedBytes)}", color = Muted)
            Text(user.storageQuotaBytes?.let { "$percent% / ${formatBytes(it)}" } ?: "无限制", color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MeUpdatePage(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
) {
    MePageScaffold(paddingValues, "版本更新", "Add 版本保留启动检查", onBack) {
        Text("当前版本", color = Muted, fontSize = 13.sp)
        Text(BuildConfig.VERSION_NAME, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("应用启动和恢复登录后会按现有逻辑检查服务器版本。若发现新版本，会弹出更新提示。", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun MeAdminPage(
    paddingValues: PaddingValues,
    team: TeamUiState,
    currentUser: User,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateUser: () -> Unit,
    onEditQuota: (User) -> Unit,
    onResetPassword: (User) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(ScreenBackground),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .noRippleClickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    ReferenceIcon(ReferenceAsset.BackBlack, contentDescription = "返回", modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("账号管理", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text("当前账号：${currentUser.nickname}", color = Muted, fontSize = 13.sp)
                }
                Text(
                    "刷新",
                    color = PrimaryBlueDeep,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .noRippleClickable(onClick = onRefresh)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        item {
            Button(onClick = onCreateUser, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                ReferenceIcon(ReferenceAsset.MeBlack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("新建账号")
            }
        }
        when {
            team.loading -> item { LoadingCard("正在加载账号") }
            team.error != null -> item { ErrorCard(team.error, onRefresh) }
            team.users.isEmpty() -> item { EmptyCard("暂无账号") }
            else -> items(team.users, key = { it.id }) { itemUser ->
                UserCard(
                    user = itemUser,
                    busy = team.quotaUserId == itemUser.id || team.passwordUserId == itemUser.id,
                    onEditQuota = { onEditQuota(itemUser) },
                    onResetPassword = { onResetPassword(itemUser) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.addCardChrome(RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun TeamScreen(
    paddingValues: PaddingValues,
    team: TeamUiState,
    currentUser: User,
    onRefresh: () -> Unit,
    onCreateUser: () -> Unit,
    onEditQuota: (User) -> Unit,
    onResetPassword: (User) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(ScreenBackground),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("账号管理", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("当前账号：${currentUser.nickname}", color = Muted, fontSize = 12.sp)
                }
                HeaderIconButton(ReferenceAsset.RefreshBlack, "刷新", onRefresh)
            }
        }

        item {
            Button(
                onClick = onCreateUser,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                ReferenceIcon(ReferenceAsset.MeBlack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("新建账号")
            }
        }

        item {
            when {
                team.loading -> LoadingCard("正在加载账号")
                team.error != null -> ErrorCard(team.error, onRefresh)
                team.users.isEmpty() -> EmptyCard("暂无账号")
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    team.users.forEach { user ->
                        UserCard(
                            user = user,
                            busy = team.quotaUserId == user.id || team.passwordUserId == user.id,
                            onEditQuota = { onEditQuota(user) },
                            onResetPassword = { onResetPassword(user) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSheet(
    onDismiss: () -> Unit,
    onUpload: (Array<String>) -> Unit,
    onCreateFolder: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFD7DBE5)),
            )
            Text("上传文件", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    UploadChoiceTile("相册", ReferenceAsset.PhotoColor, Modifier.weight(1f)) {
                        onUpload(arrayOf("image/*", "video/*"))
                    }
                    UploadChoiceTile("文档", ReferenceAsset.DocumentColor, Modifier.weight(1f)) {
                        onUpload(arrayOf("application/pdf", "text/*", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    }
                    UploadChoiceTile("压缩包", ReferenceAsset.ArchiveColor, Modifier.weight(1f)) {
                        onUpload(arrayOf("application/zip", "application/x-rar-compressed", "application/x-7z-compressed", "application/octet-stream"))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    UploadChoiceTile("音频", ReferenceAsset.AudioColor, Modifier.weight(1f)) {
                        onUpload(arrayOf("audio/*"))
                    }
                    UploadChoiceTile("其他文件", ReferenceAsset.UploadFileBlue, Modifier.weight(1f)) {
                        onUpload(arrayOf("*/*"))
                    }
                    UploadChoiceTile("新建文件夹", ReferenceAsset.NewFolderBlue, Modifier.weight(1f), onCreateFolder)
                }
            }
        }
    }
}

@Composable
private fun UploadChoiceTile(
    label: String,
    asset: ReferenceAsset,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .noRippleClickable(onClick = onClick)
            .border(1.dp, SoftLine, RoundedCornerShape(18.dp)),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ReferenceIcon(
                asset = asset,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .scale(1.5f),
            )
            Spacer(Modifier.height(5.dp))
            Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    busy: Boolean,
    onEditQuota: () -> Unit,
    onResetPassword: () -> Unit,
) {
    Card(
        modifier = Modifier.addCardChrome(RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(url = null, fallback = user.nickname, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.nickname, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${user.phoneNumber} · ${formatRole(user.role)}", color = Muted, fontSize = 12.sp)
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Text(userUsageLabel(user), color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEditQuota,
                    enabled = !busy && !user.isAdmin,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("额度") }
                OutlinedButton(
                    onClick = onResetPassword,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("重置密码") }
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("文件夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = !creating) {
                Text(if (creating) "创建中" else "创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeActionSheet(
    target: NodeActionContext,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val node = target.node
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NodeIcon(node)
                Column(modifier = Modifier.weight(1f)) {
                    Text(node.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatNodeMeta(node), color = Muted, fontSize = 12.sp)
                }
            }
            if (busy) {
                LoadingCard("正在处理")
            } else if (target.isTrashMode) {
                SheetActionButton("恢复", "还原到原目录", onRestore)
                SheetActionButton("彻底删除", "删除后无法恢复", onDeleteForever, danger = true)
            } else {
                if (node.type == StorageNodeType.FILE) {
                    SheetActionButton("预览", "查看支持的文件类型", onPreview)
                    SheetActionButton("下载", "保存到设备目录", onDownload)
                } else {
                    SheetActionButton("下载文件夹", "打包为 ZIP 保存", onDownload)
                }
                SheetActionButton("分享", "生成访问链接", onShare)
                SheetActionButton("移动", "移动到其他目录", onMove)
                SheetActionButton("移入回收站", "稍后可以恢复", onTrash, danger = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveTargetSheet(
    explorer: ExplorerUiState,
    selectedTargetId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("选择移动位置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            AliciaChip(
                label = "根目录",
                selected = selectedTargetId == null,
                onClick = { onSelect(null) },
            )
            if (explorer.moveTargetLoading) {
                LoadingCard("正在加载文件夹")
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(explorer.moveTargetFolders, key = { it.id }) { folder ->
                        Card(
                            onClick = { onSelect(folder.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedTargetId == folder.id) Color(0xFFEFF6FF) else Color(0xFFF8FAFE),
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SoftLine.copy(alpha = 0.76f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ReferenceIcon(ReferenceAsset.FolderSolidBlue, null, Modifier.size(24.dp))
                                Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (selectedTargetId == folder.id) SelectionIndicator(selected = true)
                            }
                        }
                    }
                }
            }
            Button(onClick = onMove, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Text("移动到这里")
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, UserRole, String) -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.USER) }
    var quota by rememberSaveable { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("手机号") }, singleLine = true)
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("昵称") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("初始密码") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AliciaChip(
                        label = "普通用户",
                        selected = role == UserRole.USER,
                        onClick = { role = UserRole.USER },
                        modifier = Modifier.weight(1f),
                    )
                    AliciaChip(
                        label = "管理员",
                        selected = role == UserRole.ADMIN,
                        onClick = { role = UserRole.ADMIN },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (role == UserRole.USER) {
                    OutlinedTextField(
                        value = quota,
                        onValueChange = { quota = it },
                        label = { Text("空间额度 GB") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(phone, nickname, password, role, quota) }, enabled = !creating) {
                Text(if (creating) "创建中" else "创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UpdateQuotaDialog(
    user: User,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var quota by rememberSaveable(user.id) { mutableStateOf(formatGigabytesInput(user.storageQuotaBytes ?: 0L)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整额度") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(user.nickname, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = quota,
                    onValueChange = { quota = it },
                    label = { Text("空间额度 GB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { Button(onClick = { onSubmit(quota) }, enabled = !busy) { Text(if (busy) "保存中" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ResetPasswordDialog(
    user: User,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by rememberSaveable(user.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重置密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(user.nickname, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("新密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { Button(onClick = { onSubmit(password) }, enabled = !busy) { Text(if (busy) "重置中" else "重置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TransferTaskCard(
    task: TransferTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val statusColor = transferStatusColor(task.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransferTaskThumbnail(task)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    task.title,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        transferBytesLabel(task),
                        modifier = Modifier.weight(1f),
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        transferStatusLabel(task.status),
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransferProgressBar(
                        progress = transferProgressFraction(task),
                        modifier = Modifier.weight(1f),
                    )
                    if (task.kind == TransferKind.DOWNLOAD && task.status == TransferStatus.FAILED) {
                        TransferRetryButton(onClick = onRetry)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .noRippleClickable(enabled = task.isTransferActive(), onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                ReferenceIcon(
                    ReferenceAsset.MoreBlack,
                    contentDescription = if (task.isTransferActive()) "取消任务" else "任务操作",
                    modifier = Modifier
                        .size(23.dp)
                        .scale(1.35f),
                    opacity = if (task.isTransferActive()) 1f else 0.62f,
                )
            }
        }
        task.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 59.dp, end = 34.dp),
                color = Danger,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TransferTaskThumbnail(task: TransferTask) {
    val extension = task.title.substringAfterLast('.', "").lowercase()
    val previewUri = (task.sourceUri ?: task.destinationUri).takeIf {
        extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (previewUri != null) {
            SubcomposeAsyncImage(
                model = previewUri,
                contentDescription = task.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Crop,
                loading = { TransferTaskFallback(task) },
                error = { TransferTaskFallback(task) },
            )
        } else {
            TransferTaskFallback(task)
        }
    }
}

@Composable
private fun TransferTaskFallback(task: TransferTask) {
    ReferenceIcon(
        asset = transferTaskReferenceAsset(task),
        contentDescription = null,
        modifier = Modifier
            .size(42.dp)
            .scale(1.42f),
    )
}

@Composable
private fun TransferProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFE1E5EC)),
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(PrimaryBlueDeep),
            )
        }
    }
}

@Composable
private fun TransferRetryButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(SoftBlue)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("重新下载", color = PrimaryBlueDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            when {
                state.loading -> LoadingCard("正在加载预览")
                state.error != null -> Text(state.error, color = Danger)
                state.kind == PreviewKind.TEXT -> SelectionContainer {
                    Text(
                        text = state.textContent.ifBlank { "文件内容为空。" },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        color = Ink,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
                state.kind == PreviewKind.IMAGE && !state.previewUrl.isNullOrBlank() -> SubcomposeAsyncImage(
                    model = state.previewUrl,
                    contentDescription = state.fileName,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentScale = ContentScale.Fit,
                    loading = { LoadingCard("正在加载图片") },
                    error = { ErrorCard("图片预览加载失败，请下载查看。", onDismiss) },
                )
                state.kind == PreviewKind.PDF && !state.localFilePath.isNullOrBlank() -> PdfPreviewContent(state.localFilePath)
                state.kind == PreviewKind.VIDEO || state.kind == PreviewKind.AUDIO -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("此类型建议使用系统播放器打开。", color = Muted)
                    if (!state.previewUrl.isNullOrBlank()) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.previewUrl)))
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("打开预览链接")
                        }
                    }
                }
                else -> Text("当前文件暂不支持内置预览，请先下载查看。", color = Muted)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun PdfPreviewContent(filePath: String) {
    val document = remember(filePath) { runCatching { PdfPreviewDocument(filePath) }.getOrNull() }
    var pageIndex by rememberSaveable(filePath) { mutableStateOf(0) }
    val pageCount = document?.pageCount ?: 0
    val pageBitmap = remember(filePath, pageIndex) { document?.renderPage(pageIndex) }

    DisposableEffect(document) {
        onDispose { document?.close() }
    }

    DisposableEffect(pageBitmap) {
        onDispose { pageBitmap?.recycle() }
    }

    if (document == null || pageBitmap == null) {
        Text("PDF 预览加载失败，请下载查看。", color = Danger)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pageCount > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = pageIndex > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("上一页") }
                Text("${pageIndex + 1} / $pageCount", color = Muted, fontSize = 12.sp)
                OutlinedButton(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                    enabled = pageIndex < pageCount - 1,
                    modifier = Modifier.weight(1f),
                ) { Text("下一页") }
            }
        }
        Image(
            bitmap = pageBitmap.asImageBitmap(),
            contentDescription = "PDF 页面",
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun IncomingSharePage(
    state: IncomingShareUiState,
    loggedIn: Boolean,
    baseUrl: String,
    authToken: String,
    onDismiss: () -> Unit,
    onContinueLogin: () -> Unit,
    onVerifyPassword: (String) -> Unit,
    onToggleFolder: (Long) -> Unit,
    onToggleNode: (Long) -> Unit,
    onOpenSaveTarget: () -> Unit,
    onCloseSaveTarget: () -> Unit,
    onSelectSaveTarget: (Long?) -> Unit,
    onSave: () -> Unit,
) {
    var password by rememberSaveable(state.activeShareCode) { mutableStateOf("") }
    val status = state.status
    val detail = state.detail
    val selectedBytes = remember(detail, state.selectedNodeIds) {
        detail?.let { selectedShareBytes(it.items, state.selectedNodeIds) } ?: 0L
    }
    val totalBytes = remember(detail) {
        detail?.items?.filter { it.type == StorageNodeType.FILE }?.sumOf { it.size } ?: 0L
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        val showSaveDock = detail != null && detail.allowSave
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 10.dp,
                end = 20.dp,
                bottom = if (showSaveDock) 132.dp else 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                IncomingShareHeader(
                    title = status?.title ?: detail?.title ?: "分享内容",
                    onBack = onDismiss,
                )
            }
            when {
                state.statusLoading || state.detailLoading -> item { LoadingCard("正在加载分享") }
                state.error != null -> item { ErrorCard(state.error, onDismiss) }
                status != null && !status.available -> item { ErrorCard(status.reason ?: "分享链接不可用", onDismiss) }
                !loggedIn && !state.loginPromptDismissed -> item {
                    Surface(
                        modifier = Modifier.addCardChrome(RoundedCornerShape(20.dp)),
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("请先登录后查看和保存分享内容。", color = Ink, fontWeight = FontWeight.SemiBold)
                            IncomingSharePrimaryButton(
                                label = "我知道了",
                                enabled = true,
                                onClick = onContinueLogin,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                status?.requiresPassword == true && state.shareAccessToken.isNullOrBlank() -> item {
                    IncomingSharePasswordPanel(
                        password = password,
                        onPasswordChange = { password = it },
                        checking = state.passwordChecking,
                        error = state.passwordError,
                        onVerify = { onVerifyPassword(password) },
                    )
                }
                detail != null -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (detail.allowSave) "已选 ${state.selectedNodeIds.size} 项" else "共 ${detail.items.size} 项",
                                color = Ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "共 ${formatBytes(if (state.selectedNodeIds.isNotEmpty()) selectedBytes else totalBytes)}",
                                color = Muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    item {
                        ShareNodeTree(
                            items = detail.items,
                            rootNodeIds = detail.rootNodeIds,
                            expandedFolderIds = state.expandedFolderIds,
                            selectedNodeIds = state.selectedNodeIds,
                            allowSave = detail.allowSave,
                            baseUrl = baseUrl,
                            authToken = authToken,
                            onToggleFolder = onToggleFolder,
                            onToggleNode = onToggleNode,
                        )
                    }
                    if (detail.allowSave) {
                        item {
                            IncomingShareSaveTargetPanel(
                                state = state,
                                onOpen = onOpenSaveTarget,
                            )
                        }
                    }
                }
            }
        }

        if (showSaveDock) {
            IncomingShareSaveDock(
                state = state,
                loggedIn = loggedIn,
                onSave = onSave,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (state.saveTargetPickerOpen) {
        IncomingShareSaveTargetSheet(
            state = state,
            onDismiss = onCloseSaveTarget,
            onSelect = onSelectSaveTarget,
        )
    }
}

@Composable
private fun IncomingSharePromptDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black.copy(alpha = 0.045f),
                        spotColor = Color.Black.copy(alpha = 0.075f),
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "发现分享链接",
                    color = Ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "检测到 Alicia 云盘分享链接，是否查看？",
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IncomingShareSecondaryButton(
                        label = "暂不查看",
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                    )
                    IncomingSharePrimaryButton(
                        label = "查看",
                        enabled = true,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingShareHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .noRippleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            ReferenceIcon(
                ReferenceAsset.BackBlack,
                "返回",
                Modifier
                    .size(38.dp)
                    .scale(1.22f),
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun IncomingSharePasswordPanel(
    password: String,
    onPasswordChange: (String) -> Unit,
    checking: Boolean,
    error: String?,
    onVerify: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .addCardChrome(RoundedCornerShape(20.dp)),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("请输入提取码", fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp)
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("提取码") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlueDeep,
                    unfocusedBorderColor = SoftLine,
                    focusedLabelColor = PrimaryBlueDeep,
                    cursorColor = PrimaryBlueDeep,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Danger, fontSize = 12.sp) }
            IncomingSharePrimaryButton(
                label = if (checking) "校验中" else "查看分享",
                enabled = !checking,
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IncomingShareSaveTargetPanel(
    state: IncomingShareUiState,
    onOpen: () -> Unit,
) {
    val selectedFolder = state.saveTargetFolders.firstOrNull { it.id == state.saveTargetParentId }
    val targetTitle = selectedFolder?.name ?: "我的云盘"
    val targetSubtitle = if (selectedFolder == null) "根目录" else "选定文件夹"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("保存到", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .addCardChrome(RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .noRippleClickable(onClick = onOpen),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ReferenceIcon(
                        asset = if (selectedFolder == null) ReferenceAsset.CloudUploadBlue else ReferenceAsset.FolderSolidBlue,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .scale(1.28f),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(targetTitle, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(targetSubtitle, color = Muted, fontSize = 12.sp)
                }
                SelectionIndicator(selected = true)
            }
        }
    }
}

@Composable
private fun IncomingShareSaveDock(
    state: IncomingShareUiState,
    loggedIn: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = loggedIn && state.selectedNodeIds.isNotEmpty() && !state.saving
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), ambientColor = Color.Black.copy(alpha = 0.025f), spotColor = Color.Black.copy(alpha = 0.04f)),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
        ) {
            IncomingSharePrimaryButton(
                label = when {
                    state.saving -> "保存中"
                    !loggedIn -> "登录后保存"
                    else -> "保存选中"
                },
                enabled = enabled,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomingShareSaveTargetSheet(
    state: IncomingShareUiState,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ScreenBackground,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFD1D5DE)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("保存到", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IncomingShareTargetRow(
                    title = "我的云盘",
                    subtitle = "根目录",
                    asset = ReferenceAsset.CloudUploadBlue,
                    selected = state.saveTargetParentId == null,
                    onClick = { onSelect(null) },
                )
                if (state.saveTargetLoading) {
                    LoadingCard("正在加载文件夹")
                } else {
                    state.saveTargetFolders.forEach { folder ->
                        IncomingShareTargetRow(
                            title = folder.name,
                            subtitle = "文件夹",
                            asset = ReferenceAsset.FolderSolidBlue,
                            selected = state.saveTargetParentId == folder.id,
                            onClick = { onSelect(folder.id) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IncomingShareSecondaryButton(
                    label = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                IncomingSharePrimaryButton(
                    label = "确定",
                    enabled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun IncomingShareTargetRow(
    title: String,
    subtitle: String,
    asset: ReferenceAsset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .noRippleClickable(onClick = onClick),
        color = if (selected) Color(0xFFEFF6FF) else Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) PrimaryBlueDeep.copy(alpha = 0.42f) else SoftLine),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.Center,
            ) {
                ReferenceIcon(
                    asset,
                    null,
                    Modifier
                        .size(42.dp)
                        .scale(1.28f),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            if (selected) {
                SelectionIndicator(selected = true)
            }
        }
    }
}

@Composable
private fun IncomingSharePrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) PrimaryBlueDeep else Color(0xFFDCE5F5))
            .noRippleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun IncomingShareSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF1F3F8))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun selectedShareBytes(
    items: List<StorageNode>,
    selectedNodeIds: Set<Long>,
): Long {
    val children = items.groupBy { it.parentId }
    val selectedRootIds = ShareTreeSelection.minimalSelectedRootIds(items, selectedNodeIds).toSet()

    fun nodeBytes(node: StorageNode): Long =
        if (node.type == StorageNodeType.FILE) {
            node.size
        } else {
            children[node.id].orEmpty().sumOf(::nodeBytes)
        }

    return items
        .filter { it.id in selectedRootIds }
        .sumOf(::nodeBytes)
}

@Composable
private fun ShareNodeTree(
    items: List<StorageNode>,
    rootNodeIds: List<Long>,
    expandedFolderIds: Set<Long>,
    selectedNodeIds: Set<Long>,
    allowSave: Boolean,
    baseUrl: String,
    authToken: String,
    onToggleFolder: (Long) -> Unit,
    onToggleNode: (Long) -> Unit,
) {
    val byId = remember(items) { items.associateBy { it.id } }
    val children = remember(items) { items.groupBy { it.parentId } }
    val roots = remember(items, rootNodeIds) {
        rootNodeIds.mapNotNull { byId[it] }.ifEmpty { items.filter { it.parentId == null } }
    }

    if (roots.isEmpty()) {
        EmptyCard("分享内容为空")
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .addCardChrome(RoundedCornerShape(18.dp)),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
            roots.forEach { node ->
                ShareNodeRow(
                    node = node,
                    level = 0,
                    children = children,
                    expandedFolderIds = expandedFolderIds,
                    selectedNodeIds = selectedNodeIds,
                    allowSave = allowSave,
                    baseUrl = baseUrl,
                    authToken = authToken,
                    onToggleFolder = onToggleFolder,
                    onToggleNode = onToggleNode,
                )
            }
            }
        }
    }
}

@Composable
private fun ShareNodeRow(
    node: StorageNode,
    level: Int,
    children: Map<Long?, List<StorageNode>>,
    expandedFolderIds: Set<Long>,
    selectedNodeIds: Set<Long>,
    allowSave: Boolean,
    baseUrl: String,
    authToken: String,
    onToggleFolder: (Long) -> Unit,
    onToggleNode: (Long) -> Unit,
) {
    val isFolder = node.type == StorageNodeType.FOLDER
    val expanded = node.id in expandedFolderIds
    val selected = node.id in selectedNodeIds
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) Color(0xFFF7FBFF) else Color.Transparent)
                .noRippleClickable {
                    if (isFolder) {
                        onToggleFolder(node.id)
                    } else if (allowSave) {
                        onToggleNode(node.id)
                    }
                }
                .padding(start = (10 + level * 18).dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NodeThumbnailBox(
                node = node,
                baseUrl = baseUrl,
                authToken = authToken,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(13.dp),
                backgroundColor = Color.Transparent,
                fallbackIconScale = 1.45f,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(node.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (isFolder) "文件夹" else formatBytes(node.size), color = Muted, fontSize = 12.sp)
            }
            if (isFolder) {
                Text(if (expanded) "收起" else "展开", color = PrimaryBlueDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (allowSave) {
                SelectionCircle(
                    selected = selected,
                    onClick = { onToggleNode(node.id) },
                    size = 22.dp,
                )
            }
        }
        if (isFolder && expanded) {
            children[node.id].orEmpty().forEach { child ->
                ShareNodeRow(
                    node = child,
                    level = level + 1,
                    children = children,
                    expandedFolderIds = expandedFolderIds,
                    selectedNodeIds = selectedNodeIds,
                    allowSave = allowSave,
                    baseUrl = baseUrl,
                    authToken = authToken,
                    onToggleFolder = onToggleFolder,
                    onToggleNode = onToggleNode,
                )
            }
        }
    }
}

@Composable
private fun SheetActionButton(
    label: String,
    hint: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val asset = sheetActionReferenceAsset(label, danger)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (danger) Color(0xFFFFF7F2) else Color(0xFFF8FAFE)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (danger) Color(0xFFFFB38F) else SoftLine.copy(alpha = 0.76f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background((if (danger) Danger else PrimaryBlue).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                ReferenceIcon(
                    asset = asset,
                    contentDescription = null,
                    modifier = if (asset == ReferenceAsset.RestoreGreen) {
                        Modifier.size(30.dp)
                    } else {
                        Modifier
                            .size(38.dp)
                            .scale(1.35f)
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = if (danger) Danger else Ink, fontWeight = FontWeight.Bold)
                Text(hint, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

private fun sheetActionReferenceAsset(label: String, danger: Boolean): ReferenceAsset =
    when {
        danger -> ReferenceAsset.DeleteRed
        label.contains("预览") -> ReferenceAsset.EyeGray
        label.contains("下载") -> ReferenceAsset.DownloadBlack
        label.contains("分享") -> ReferenceAsset.ShareBlack
        label.contains("移动") -> ReferenceAsset.MoveBlack
        label.contains("恢复") -> ReferenceAsset.RestoreGreen
        else -> ReferenceAsset.FolderSolidBlue
    }

@Composable
private fun Avatar(
    url: String?,
    fallback: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier),
        shape = CircleShape,
        color = Color(0xFFEAF1FF),
    ) {
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = { AvatarFallback(fallback) },
            )
        } else {
            AvatarFallback(fallback)
        }
    }
}

@Composable
private fun AvatarFallback(fallback: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = fallback.trim().take(1).ifBlank { "A" },
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NodeIcon(node: StorageNode) {
    Surface(
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = nodeThumbnailBackground(node),
    ) {
        ReferenceIcon(
            asset = nodeReferenceAsset(node),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.25f),
        )
    }
}

private fun nodeGlyph(node: StorageNode): AliciaGlyph =
    when {
        node.type == StorageNodeType.FOLDER -> AliciaGlyph.Folder
        node.mimeType.orEmpty().startsWith("image/") -> AliciaGlyph.ImageFile
        node.mimeType.orEmpty().startsWith("video/") -> AliciaGlyph.VideoFile
        node.mimeType.orEmpty().startsWith("audio/") -> AliciaGlyph.AudioFile
        node.extension.equals("zip", true) || node.extension.equals("rar", true) || node.extension.equals("7z", true) -> AliciaGlyph.ArchiveFile
        else -> AliciaGlyph.DocumentFile
    }

private fun nodeIconTint(node: StorageNode): Color =
    when {
        node.type == StorageNodeType.FOLDER -> PrimaryBlueDeep
        node.mimeType.orEmpty().startsWith("image/") -> Color(0xFF6B7A90)
        node.mimeType.orEmpty().startsWith("video/") -> Color(0xFF5B5CF6)
        node.mimeType.orEmpty().startsWith("audio/") -> Color(0xFF7250F0)
        node.extension.equals("zip", true) || node.extension.equals("rar", true) || node.extension.equals("7z", true) -> WarmOrange
        else -> Color(0xFF6B7A90)
    }

private fun nodeThumbnailBackground(node: StorageNode): Color =
    when {
        node.type == StorageNodeType.FOLDER -> Color(0xFFEAF2FF)
        node.mimeType.orEmpty().startsWith("image/") -> Color(0xFFF0F4FA)
        node.mimeType.orEmpty().startsWith("video/") -> Color(0xFFEDEBFF)
        node.mimeType.orEmpty().startsWith("audio/") -> Color(0xFFF1EBFF)
        node.extension.equals("zip", true) || node.extension.equals("rar", true) || node.extension.equals("7z", true) -> Color(0xFFFFEFE4)
        else -> Color(0xFFF1F5FA)
    }

@Composable
private fun CompactNodeCard(
    node: StorageNode,
    baseUrl: String,
    authToken: String,
    onClick: () -> Unit,
    elevated: Boolean = true,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (elevated) Modifier.addCardChrome(RoundedCornerShape(18.dp)) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .noRippleClickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NodeThumbnailBox(
                node = node,
                baseUrl = baseUrl,
                authToken = authToken,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = Color.Transparent,
                fallbackIconScale = 1.35f,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatNodeMeta(node), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(
        modifier = Modifier.addCardChrome(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryBlue)
            Text(message, color = Muted)
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F2)),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB38F)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = Danger, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        modifier = Modifier.addCardChrome(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReferenceIcon(ReferenceAsset.FolderSolidBlue, contentDescription = null, modifier = Modifier.size(38.dp))
            Text(message, color = Muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun categoryLabel(category: StorageFileCategory?): String =
    when (category) {
        null -> "全部文件"
        StorageFileCategory.IMAGE -> "相册"
        StorageFileCategory.VIDEO -> "视频"
        StorageFileCategory.AUDIO -> "音频"
        StorageFileCategory.DOCUMENT -> "文档"
        StorageFileCategory.ARCHIVE -> "压缩包"
    }

private fun filterCategoryLabel(category: StorageFileCategory?): String =
    when (category) {
        null -> "全部"
        StorageFileCategory.IMAGE -> "图片"
        StorageFileCategory.VIDEO -> "视频"
        StorageFileCategory.AUDIO -> "音频"
        StorageFileCategory.DOCUMENT -> "文档"
        StorageFileCategory.ARCHIVE -> "压缩包"
    }

private fun gridNodeMeta(node: StorageNode): String =
    when (node.type) {
        StorageNodeType.FOLDER -> "文件夹 · ${formatMonthDay(node.updatedAt)}"
        StorageNodeType.FILE -> "${formatBytes(node.size)} · ${formatMonthDay(node.updatedAt)}"
    }

private fun storageFileDownloadUrl(baseUrl: String, fileId: Long): String =
    "${baseUrl.trim().removeSuffix("/")}/api/storage/files/$fileId/download"

private fun TransferTask.isTransferActive(): Boolean =
    status == TransferStatus.QUEUED ||
        status == TransferStatus.PREPARING ||
        status == TransferStatus.RUNNING

private fun transferStatusLabel(status: TransferStatus): String =
    when (status) {
        TransferStatus.QUEUED -> "等待中"
        TransferStatus.PREPARING -> "准备中"
        TransferStatus.RUNNING -> "传输中"
        TransferStatus.COMPLETED -> "已完成"
        TransferStatus.FAILED -> "失败"
        TransferStatus.CANCELED -> "已取消"
    }

private fun transferStatusColor(status: TransferStatus): Color =
    when (status) {
        TransferStatus.FAILED -> Danger
        else -> Muted
    }

private fun transferTaskReferenceAsset(task: TransferTask): ReferenceAsset {
    val extension = task.title.substringAfterLast('.', "").lowercase()
    return when {
        task.itemKind == TransferItemKind.ARCHIVE -> ReferenceAsset.ArchiveColor
        extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif") -> ReferenceAsset.PhotoColor
        extension in setOf("mp4", "mov", "mkv", "avi", "webm", "m4v") -> ReferenceAsset.VideoColor
        extension in setOf("mp3", "wav", "flac", "aac", "m4a", "ogg") -> ReferenceAsset.AudioColor
        extension in setOf("zip", "rar", "7z", "tar", "gz") -> ReferenceAsset.ArchiveColor
        extension == "pdf" -> ReferenceAsset.PdfRed
        extension in setOf("doc", "docx") -> ReferenceAsset.DocBlue
        else -> ReferenceAsset.DocumentColor
    }
}

private fun transferProgressFraction(task: TransferTask): Float {
    if (task.status == TransferStatus.COMPLETED) return 1f
    task.progressPercent?.let { return (it / 100f).coerceIn(0f, 1f) }
    val total = task.totalBytes ?: return 0f
    if (total <= 0L) return 0f
    return (task.transferredBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun transferDayLabel(task: TransferTask): String {
    val ageMillis = (System.currentTimeMillis() - task.createdAtMillis).coerceAtLeast(0L)
    return when {
        ageMillis < 24L * 60L * 60L * 1000L -> "今天"
        ageMillis < 48L * 60L * 60L * 1000L -> "昨天"
        else -> "更早"
    }
}

private fun transferBytesLabel(task: TransferTask): String {
    val totalBytes = task.totalBytes?.takeIf { it > 0L }
    val transferredBytes = when {
        task.status == TransferStatus.COMPLETED && totalBytes != null -> totalBytes
        else -> task.transferredBytes.coerceAtLeast(0L)
    }
    return when {
        totalBytes != null -> "${formatBytes(transferredBytes)} / ${formatBytes(totalBytes)}"
        task.progressPercent != null -> "${task.progressPercent}%"
        transferredBytes > 0L -> formatBytes(transferredBytes)
        else -> "等待传输"
    }
}

private fun suggestedArchiveName(
    nodes: List<StorageNode>,
    selectedIds: List<Long>,
): String {
    if (selectedIds.size == 1) {
        val name = nodes.firstOrNull { it.id == selectedIds.first() }?.name?.trim().orEmpty()
        return "${name.removeSuffix(".zip").ifBlank { "AliciaCloud" }}.zip"
    }
    return "选中 ${selectedIds.size} 项.zip"
}

private class PdfPreviewDocument(filePath: String) {
    private val descriptor = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int
        get() = renderer.pageCount

    fun renderPage(index: Int): Bitmap? {
        if (index !in 0 until renderer.pageCount) {
            return null
        }
        return renderer.openPage(index).use { page ->
            val scale = 2
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).coerceAtLeast(1),
                (page.height * scale).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    fun close() {
        renderer.close()
        descriptor.close()
    }
}
