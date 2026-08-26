package com.alicia.cloudstorage.phone.ui

import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.alicia.cloudstorage.phone.R
import com.alicia.cloudstorage.phone.ShareCreateArgs
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType

private val ShareInk = Color(0xFF111827)
private val ShareMuted = Color(0xFF8993A6)
private val ShareBlue = Color(0xFF0B6BFF)
private val ShareSoftBlue = Color(0xFFEAF2FF)
private val ShareSoftGray = Color(0xFFF3F5FA)
private val ShareLine = Color(0xFFE2E6EF)
private val ShareBackground = Color(0xFFF7F8FD)

data class ShareSelection(val nodes: List<StorageNode>) {
    init {
        require(nodes.isNotEmpty() && nodes.size <= MAX_SHARE_TARGETS)
        require(nodes.map(StorageNode::id).distinct().size == nodes.size)
    }

    val nodeIds: List<Long> = nodes.map(StorageNode::id)
    val defaultTitle: String = if (nodes.size == 1) nodes.first().name else "批量分享"
    val containsFolder: Boolean = nodes.any { it.type == StorageNodeType.FOLDER }
    val totalFileBytes: Long = nodes.filter { it.type == StorageNodeType.FILE }.sumOf(StorageNode::size)
    val stateKey: String = nodeIds.joinToString(",")
}

@Composable
fun ShareCreateScreen(
    args: ShareCreateArgs,
    viewModel: ShareCreateViewModel,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onSystemShare: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection = args.selection
    val createdShare = state.createdShare
    var title by remember(selection.stateKey) { mutableStateOf(selection.defaultTitle) }
    var password by remember(selection.stateKey) { mutableStateOf("") }
    var expiresInDays by remember(selection.stateKey) { mutableStateOf<Int?>(7) }
    var allowDownload by remember(selection.stateKey) { mutableStateOf(true) }
    var allowSave by remember(selection.stateKey) { mutableStateOf(true) }
    var validationError by remember(selection.stateKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(title, password) {
        validationError = null
    }
    BackHandler(enabled = state.creating) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShareBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ShareActivityHeader(
                title = if (createdShare == null) "创建分享" else "分享已创建",
                enabled = !state.creating,
                onBack = onBack,
            )

            if (createdShare == null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ShareContentModule(selection, args.baseUrl, args.authToken)

                    AddShareTextField(
                        label = "分享标题",
                        value = title,
                        placeholder = "输入分享标题",
                        enabled = !state.creating,
                        onValueChange = { if (it.length <= 255) title = it },
                    )
                    AddShareTextField(
                        label = "提取码（可空）",
                        value = password,
                        placeholder = "4 到 32 个字符",
                        enabled = !state.creating,
                        password = true,
                        onValueChange = { if (it.length <= 32) password = it },
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("有效期", color = ShareInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "1 天", 7 to "7 天", 30 to "30 天", null to "永久").forEach { (days, label) ->
                                ShareExpiryOption(
                                    label = label,
                                    selected = expiresInDays == days,
                                    enabled = !state.creating,
                                    modifier = Modifier.weight(1f),
                                    onClick = { expiresInDays = days },
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AddShareToggle("允许下载", allowDownload, !state.creating) { allowDownload = it }
                        AddShareToggle("允许保存到网盘", allowSave, !state.creating) { allowSave = it }
                    }

                    (validationError ?: state.error)?.let { error ->
                        Text(error, color = Color(0xFFE84D3D), fontSize = 13.sp)
                    }
                }

                ShareCreateActionBar(
                    creating = state.creating,
                    onCancel = onBack,
                    onCreate = {
                        val normalizedTitle = title.trim()
                        val normalizedPassword = password.trim()
                        validationError = when {
                            normalizedTitle.isEmpty() -> "请输入分享标题。"
                            normalizedPassword.isNotEmpty() && normalizedPassword.length !in 4..32 -> "提取码需为 4 到 32 个字符。"
                            else -> null
                        }
                        if (validationError == null) {
                            viewModel.createShare(
                                normalizedTitle,
                                normalizedPassword.takeIf(String::isNotEmpty),
                                expiresInDays,
                                allowDownload,
                                allowSave,
                            )
                        }
                    },
                )
            } else {
                ShareCreatedPage(
                    share = createdShare,
                    onCopy = { onCopy(createdShare.toShareText()) },
                    onSystemShare = { onSystemShare(createdShare.toShareText()) },
                    onDone = onBack,
                )
            }
        }

        state.message?.let { message ->
            AddToastOverlay(
                message = message,
                onExpired = viewModel::clearMessage,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AddGlobalLoadingOverlay(visible = state.creating)
    }
}

@Composable
private fun ShareActivityHeader(
    title: String,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(70.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .noRippleClickable(enabled = enabled, onClick = onBack)
                .alpha(if (enabled) 1f else 0.45f),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_add_back_black),
                contentDescription = "返回",
                modifier = Modifier
                    .size(38.dp)
                    .scale(1.28f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = ShareInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShareCreateActionBar(
    creating: Boolean,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShareDialogButton("取消", false, !creating, Modifier.weight(1f), onClick = onCancel)
            ShareDialogButton("生成", true, !creating, Modifier.weight(1f), busy = creating, onClick = onCreate)
        }
    }
}

@Composable
private fun ShareCreatedPage(
    share: ShareCreatedState,
    onCopy: () -> Unit,
    onSystemShare: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("链接已经生成", color = ShareInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, ShareLine.copy(alpha = 0.76f), RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val itemCountLabel = "共 ${share.itemCount} 项分享内容"
                Text(
                    if (share.title == itemCountLabel) "分享内容" else share.title,
                    color = ShareInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(itemCountLabel, color = ShareBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(
                        share.shareUrl,
                        color = ShareBlue,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }
                share.password?.let { code ->
                    Text("提取码：$code", color = ShareInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "下载：${if (share.allowDownload) "允许" else "关闭"}  ·  保存：${if (share.allowSave) "允许" else "关闭"}",
                    color = ShareMuted,
                    fontSize = 13.sp,
                )
                share.expiresAt?.let { expiresAt ->
                    Text("到期时间：$expiresAt", color = ShareMuted, fontSize = 13.sp)
                }
            }
        }
        Surface(color = Color.White, shadowElevation = 3.dp) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShareDialogButton("复制信息", true, true, Modifier.weight(1f), onClick = onCopy)
                    ShareDialogButton("系统分享", false, true, Modifier.weight(1f), onClick = onSystemShare)
                }
                ShareDialogButton(
                    label = "完成",
                    primary = false,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDone,
                )
            }
        }
    }
}

@Composable
private fun ShareContentModule(selection: ShareSelection, baseUrl: String, authToken: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ShareSoftGray)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("分享内容", color = ShareInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("已选 ${selection.nodes.size} 项", color = ShareBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                if (selection.containsFolder) "含文件夹" else formatShareBytes(selection.totalFileBytes),
                color = ShareMuted,
                fontSize = 12.sp,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            selection.nodes.forEach { node ->
                ShareTargetRow(node, baseUrl, authToken)
            }
        }
        if (selection.containsFolder) {
            Text("文件夹分享会包含其当前及后续新增的有效内容。", color = ShareMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ShareTargetRow(node: StorageNode, baseUrl: String, authToken: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShareTargetThumbnail(node, baseUrl, authToken)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                node.name,
                color = ShareInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (node.type == StorageNodeType.FOLDER) "文件夹" else formatShareBytes(node.size),
                color = ShareMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShareTargetThumbnail(node: StorageNode, baseUrl: String, authToken: String) {
    val context = LocalContext.current
    val isImage = node.type == StorageNodeType.FILE && node.mimeType.orEmpty().startsWith("image/")
    val model = remember(baseUrl, authToken, node.id, isImage) {
        if (isImage && authToken.isNotBlank()) {
            ImageRequest.Builder(context)
                .data("${baseUrl.trim().removeSuffix("/")}/api/storage/files/${node.id}/download")
                .addHeader("Authorization", "Bearer $authToken")
                .crossfade(false)
                .build()
        } else {
            null
        }
    }

    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = node.name,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                loading = { ShareTargetFallback(node) },
                error = { ShareTargetFallback(node) },
            )
        } else {
            ShareTargetFallback(node)
        }
    }
}

@Composable
private fun ShareTargetFallback(node: StorageNode) {
    Image(
        painter = painterResource(shareNodeDrawable(node)),
        contentDescription = null,
        modifier = Modifier
            .size(if (node.type == StorageNodeType.FOLDER) 42.dp else 44.dp)
            .scale(1.45f),
        contentScale = ContentScale.Fit,
    )
}

@DrawableRes
private fun shareNodeDrawable(node: StorageNode): Int {
    val extension = node.extension.orEmpty().lowercase()
    return when {
        node.type == StorageNodeType.FOLDER -> R.drawable.ic_add_folder_solid_blue
        node.mimeType.orEmpty().startsWith("image/") -> R.drawable.ic_add_photo_color
        node.mimeType.orEmpty().startsWith("video/") -> R.drawable.ic_add_video_color
        node.mimeType.orEmpty().startsWith("audio/") -> R.drawable.ic_add_audio_color
        extension in setOf("zip", "rar", "7z", "tar", "gz") -> R.drawable.ic_add_archive_color
        extension == "pdf" -> R.drawable.ic_add_pdf_red
        else -> R.drawable.ic_add_document_color
    }
}

@Composable
private fun AddShareTextField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    password: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = ShareInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = ShareInk, fontSize = 15.sp),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, ShareLine, RoundedCornerShape(12.dp)),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = ShareMuted, fontSize = 15.sp)
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun ShareExpiryOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ShareSoftBlue else ShareSoftGray)
            .then(if (selected) Modifier.border(1.dp, ShareBlue, RoundedCornerShape(10.dp)) else Modifier)
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.55f),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) ShareBlue else ShareInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AddShareToggle(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .noRippleClickable(enabled = enabled) { onCheckedChange(!checked) }
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = ShareInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(27.dp)
                .clip(CircleShape)
                .background(if (checked) ShareBlue else Color(0xFFD5DAE4))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(21.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun ShareDialogButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) ShareBlue else ShareSoftGray)
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled || busy) 1f else 0.55f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (busy) "$label..." else label,
            color = if (primary) Color.White else ShareInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatShareBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var unitIndex = -1
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex += 1
    }
    return if (size >= 100) "%.0f %s".format(size, units[unitIndex]) else "%.1f %s".format(size, units[unitIndex])
}
