package com.alicia.cloudstorage.phone.ui

import android.media.AudioAttributes
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.alicia.cloudstorage.phone.FileDetailArgs
import com.alicia.cloudstorage.phone.R
import com.alicia.cloudstorage.phone.ShareCreateActivity
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import kotlinx.coroutines.delay
import java.util.Locale

private val DetailBackground = Color(0xFFF7F8FD)
private val DetailInk = Color(0xFF111827)
private val DetailMuted = Color(0xFF8993A6)
private val DetailSoftLine = Color(0xFFEDEFF6)
private val DetailSoftBlue = Color(0xFFEAF2FF)
private val DetailBlue = Color(0xFF0B6BFF)
private val DetailDanger = Color(0xFFE84D3D)

private enum class DetailOverlay {
    INFO,
    MOVE,
    DELETE,
}

@Composable
fun FileDetailScreen(
    args: FileDetailArgs,
    viewModel: FileDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var overlay by rememberSaveable { mutableStateOf<DetailOverlay?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(args.node.mimeType ?: "*/*"),
    ) { uri ->
        uri?.let(viewModel::downloadToUri)
    }
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    BackHandler(enabled = overlay != null) {
        overlay = null
    }

    LaunchedEffect(state.message, state.activeOperation) {
        if (state.activeOperation == null && state.message == "移动成功。" && overlay == DetailOverlay.MOVE) {
            overlay = DetailOverlay.INFO
        }
        if (state.activeOperation == null && state.deleted && overlay == DetailOverlay.DELETE) {
            overlay = DetailOverlay.INFO
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            FileDetailHeader(
                fileName = args.node.name,
                onBack = onBack,
                onInfo = { overlay = DetailOverlay.INFO },
            )
            FileDetailPreview(
                node = args.node,
                state = state,
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        when (overlay) {
            DetailOverlay.INFO -> FileInformationOverlay(
                node = args.node,
                state = state,
                onDismiss = { overlay = null },
                onDownload = {
                    downloadLauncher.launch(args.node.name.ifBlank { "download.bin" })
                },
                onShare = {
                    overlay = null
                    shareLauncher.launch(
                        ShareCreateActivity.createIntent(
                            context = context,
                            nodes = listOf(args.node),
                            baseUrl = args.baseUrl,
                            authToken = args.authToken,
                        ),
                    )
                },
                onMove = {
                    overlay = DetailOverlay.MOVE
                    viewModel.loadMoveFolders()
                },
                onDelete = { overlay = DetailOverlay.DELETE },
            )

            DetailOverlay.MOVE -> FileMoveOverlay(
                state = state,
                currentParentId = args.node.parentId,
                onDismiss = { overlay = DetailOverlay.INFO },
                onMove = viewModel::moveTo,
            )

            DetailOverlay.DELETE -> FileDeleteOverlay(
                fileName = args.node.name,
                busy = state.activeOperation == FileDetailOperation.DELETE,
                onDismiss = { overlay = DetailOverlay.INFO },
                onConfirm = viewModel::deleteToTrash,
            )

            null -> Unit
        }

        state.message?.let { message ->
            DetailMessageBanner(
                message = message,
                onExpired = viewModel::clearMessage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

@Composable
private fun FileDetailHeader(
    fileName: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(DetailBackground)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailIconButton(
            drawableRes = R.drawable.ic_add_back_black,
            contentDescription = "返回",
            onClick = onBack,
        )
        Text(
            text = fileName,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = DetailInk,
            fontSize = 18.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DetailIconButton(
            drawableRes = R.drawable.ic_add_alert_circle_black,
            contentDescription = "文件信息",
            iconScale = 1.42f,
            onClick = onInfo,
        )
    }
}

@Composable
private fun DetailIconButton(
    drawableRes: Int,
    contentDescription: String,
    iconScale: Float = 1.55f,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .noRippleClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(32.dp)
                .scale(iconScale),
            alpha = if (enabled) 1f else 0.28f,
        )
    }
}

@Composable
private fun FileDetailPreview(
    node: StorageNode,
    state: FileDetailUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.loading -> DetailPreviewMessage(
                node = node,
                title = "正在加载预览",
                supporting = "请稍候",
            )

            state.error != null -> DetailPreviewMessage(
                node = node,
                title = "预览加载失败",
                supporting = state.error,
                actionLabel = "重新加载",
                onAction = onRetry,
            )

            state.kind == PreviewKind.IMAGE && !state.previewUrl.isNullOrBlank() -> ZoomableDetailImage(
                url = state.previewUrl,
                fileName = node.name,
                onRetry = onRetry,
            )

            state.kind == PreviewKind.TEXT -> SelectionContainer {
                Text(
                    text = state.textContent.ifBlank { "文件内容为空。" },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    color = DetailInk,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }

            state.kind == PreviewKind.VIDEO && !state.previewUrl.isNullOrBlank() -> DetailMediaPlayer(
                url = state.previewUrl,
                node = node,
                video = true,
            )

            state.kind == PreviewKind.AUDIO && !state.previewUrl.isNullOrBlank() -> DetailMediaPlayer(
                url = state.previewUrl,
                node = node,
                video = false,
            )

            else -> DetailPreviewMessage(
                node = node,
                title = detailDocumentTitle(node),
                supporting = "不直接显示内容，可从右上角下载、分享或移动文件",
            )
        }
    }
}

@Composable
private fun ZoomableDetailImage(
    url: String,
    fileName: String,
    onRetry: () -> Unit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var containerWidth by remember(url) { mutableIntStateOf(0) }
    var containerHeight by remember(url) { mutableIntStateOf(0) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        val maxX = containerWidth * (nextScale - 1f) / 2f
        val maxY = containerHeight * (nextScale - 1f) / 2f
        offset = if (nextScale <= 1.01f) {
            Offset.Zero
        } else {
            Offset(
                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        }
        scale = nextScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                containerWidth = it.width
                containerHeight = it.height
            }
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .transformable(transformState),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = fileName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            contentScale = ContentScale.Fit,
            loading = {
                DetailPreviewMessage(
                    node = placeholderImageNode(fileName),
                    title = "正在加载图片",
                    supporting = "请稍候",
                )
            },
            error = {
                DetailPreviewMessage(
                    node = placeholderImageNode(fileName),
                    title = "图片预览失败",
                    supporting = "可从右上角下载文件",
                    actionLabel = "重新加载",
                    onAction = onRetry,
                )
            },
        )
    }
}

@Composable
private fun DetailMediaPlayer(
    url: String,
    node: StorageNode,
    video: Boolean,
) {
    var videoView by remember(url) { mutableStateOf<VideoView?>(null) }
    var prepared by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var duration by remember(url) { mutableIntStateOf(0) }
    var position by remember(url) { mutableIntStateOf(0) }
    var playbackError by remember(url) { mutableStateOf<String?>(null) }

    DisposableEffect(url) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }
    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            position = videoView?.currentPosition ?: position
            delay(300)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (video) Color.Black else Color.White),
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(
                                if (video) {
                                    AudioAttributes.CONTENT_TYPE_MOVIE
                                } else {
                                    AudioAttributes.CONTENT_TYPE_MUSIC
                                },
                            )
                            .build(),
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setVideoURI(Uri.parse(url))
                    setOnPreparedListener { player ->
                        playbackError = null
                        prepared = true
                        duration = player.duration.coerceAtLeast(0)
                        player.isLooping = false
                    }
                    setOnCompletionListener {
                        playing = false
                        position = duration
                    }
                    setOnErrorListener { _, what, extra ->
                        prepared = false
                        playing = false
                        playbackError = "播放失败（$what/$extra）"
                        true
                    }
                    videoView = this
                }
            },
            update = { view -> videoView = view },
            modifier = if (video) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .size(1.dp)
                    .alpha(0f)
            },
        )

        if (!video) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_add_audio_color),
                    contentDescription = null,
                    modifier = Modifier
                        .size(92.dp)
                        .scale(1.5f),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = node.name,
                    color = DetailInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        playbackError?.let { message ->
            Text(
                text = message,
                color = if (video) Color.White else DetailInk,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            )
        }

        FilePlayerControls(
            prepared = prepared,
            playing = playing,
            position = position,
            duration = duration,
            onToggle = {
                val view = videoView ?: return@FilePlayerControls
                if (!prepared) return@FilePlayerControls
                if (playing) {
                    view.pause()
                    position = view.currentPosition
                } else {
                    if (position >= duration && duration > 0) view.seekTo(0)
                    view.start()
                }
                playing = !playing
            },
            onSeek = { target ->
                videoView?.seekTo(target)
                position = target
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun FilePlayerControls(
    prepared: Boolean,
    playing: Boolean,
    position: Int,
    duration: Int,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailIconButton(
            drawableRes = if (playing) R.drawable.ic_add_pause_black else R.drawable.ic_add_play_black,
            contentDescription = if (playing) "暂停" else "播放",
            iconScale = 1.5f,
            enabled = prepared,
            onClick = onToggle,
        )
        Text(
            text = formatPlaybackTime(position),
            color = DetailInk,
            fontSize = 12.sp,
            modifier = Modifier.width(44.dp),
        )
        DetailSeekBar(
            progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
            enabled = prepared && duration > 0,
            onSeekFraction = { fraction -> onSeek((duration * fraction).toInt()) },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatPlaybackTime(duration),
            color = DetailMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun DetailSeekBar(
    progress: Float,
    enabled: Boolean,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { point ->
                        onSeekFraction((point.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFE2E6EF)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(CircleShape)
                .background(DetailBlue),
        )
    }
}

@Composable
private fun DetailPreviewMessage(
    node: StorageNode,
    title: String,
    supporting: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(detailNodeIcon(node)),
            contentDescription = null,
            modifier = Modifier
                .size(82.dp)
                .scale(1.55f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            color = DetailInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = supporting,
            color = DetailMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(22.dp))
            DetailPrimaryButton(label = actionLabel, busy = false, onClick = onAction)
        }
    }
}

@Composable
private fun FileInformationOverlay(
    node: StorageNode,
    state: FileDetailUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    DetailBottomOverlay(onDismiss = onDismiss) {
        DetailOverlayHeader(title = "文件信息", onDismiss = onDismiss)
        FileInformationValue(label = "完整名称", value = node.name)
        DetailDivider()
        FileInformationValue(label = "大小", value = formatBytes(node.size))
        DetailDivider()
        FileInformationValue(label = "修改日期", value = formatDateTime(node.updatedAt))
        DetailDivider()
        FileInformationValue(label = "文件类型", value = detailFileTypeLabel(node))
        if (state.deleted) {
            Spacer(modifier = Modifier.height(16.dp))
            DetailStatusStrip("此文件已移入回收站", DetailDanger)
        }
        if (state.activeOperation == FileDetailOperation.DOWNLOAD) {
            Spacer(modifier = Modifier.height(16.dp))
            DetailStatusStrip(
                state.downloadPercent?.let { "正在下载 $it%" } ?: "正在下载文件",
                DetailBlue,
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text("文件操作", color = DetailInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        val actionsEnabled = state.activeOperation == null && !state.deleted
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top,
        ) {
            FileDetailOperationButton(
                drawableRes = R.drawable.ic_add_download_black,
                label = "下载",
                enabled = actionsEnabled,
                onClick = onDownload,
            )
            FileDetailOperationButton(
                drawableRes = R.drawable.ic_add_share_black,
                label = "分享",
                enabled = actionsEnabled,
                onClick = onShare,
            )
            FileDetailOperationButton(
                drawableRes = R.drawable.ic_add_move_black,
                label = "移动",
                enabled = actionsEnabled,
                onClick = onMove,
            )
            FileDetailOperationButton(
                drawableRes = R.drawable.ic_add_delete_red,
                label = "删除",
                enabled = actionsEnabled,
                tint = DetailDanger,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun FileMoveOverlay(
    state: FileDetailUiState,
    currentParentId: Long?,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
) {
    var selectedTarget by rememberSaveable { mutableStateOf(currentParentId?.toString() ?: "root") }
    DetailBottomOverlay(onDismiss = onDismiss, maxHeight = 720.dp, scrollable = false) {
        DetailOverlayHeader(title = "移动到", onDismiss = onDismiss)
        when {
            state.moveFoldersLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("正在加载文件夹", color = DetailMuted, fontSize = 15.sp)
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    MoveTargetRow(
                        label = "根目录",
                        selected = selectedTarget == "root",
                        onClick = { selectedTarget = "root" },
                    )
                }
                items(state.moveFolders, key = { it.id }) { folder ->
                    MoveTargetRow(
                        label = folder.name,
                        selected = selectedTarget == folder.id.toString(),
                        onClick = { selectedTarget = folder.id.toString() },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        DetailPrimaryButton(
            label = "移动到这里",
            busy = state.activeOperation == FileDetailOperation.MOVE,
            onClick = { onMove(selectedTarget.takeUnless { it == "root" }?.toLongOrNull()) },
        )
    }
}

@Composable
private fun FileDeleteOverlay(
    fileName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DetailBottomOverlay(onDismiss = onDismiss) {
        DetailOverlayHeader(title = "移入回收站", onDismiss = onDismiss)
        Text(
            text = "确定将“$fileName”移入回收站吗？",
            color = DetailInk,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailSecondaryButton("取消", Modifier.weight(1f), onDismiss)
            DetailDangerButton("移入回收站", busy, Modifier.weight(1f), onConfirm)
        }
    }
}

@Composable
private fun DetailBottomOverlay(
    onDismiss: () -> Unit,
    maxHeight: androidx.compose.ui.unit.Dp = 620.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.24f))
                .noRippleClickable(onClick = onDismiss),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 12.dp,
        ) {
            val baseModifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, top = 10.dp, end = 22.dp, bottom = 24.dp)
            Column(
                modifier = if (scrollable) baseModifier.verticalScroll(rememberScrollState()) else baseModifier,
                content = content,
            )
        }
    }
}

@Composable
private fun DetailOverlayHeader(title: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFD8DCE6)),
        )
    }
    Spacer(modifier = Modifier.height(18.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = DetailInk,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        DetailIconButton(
            drawableRes = R.drawable.ic_add_close_black,
            contentDescription = "关闭",
            iconScale = 1.45f,
            onClick = onDismiss,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun FileInformationValue(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, color = DetailMuted, fontSize = 13.sp)
        SelectionContainer {
            Text(
                text = value,
                color = DetailInk,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FileDetailOperationButton(
    drawableRes: Int,
    label: String,
    enabled: Boolean,
    tint: Color = DetailInk,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(68.dp)
            .noRippleClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.3f)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = label,
            modifier = Modifier
                .size(40.dp)
                .scale(1.5f),
        )
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoveTargetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) DetailSoftBlue else Color(0xFFF5F6FA))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_add_folder_solid_blue),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .scale(1.45f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (selected) DetailBlue else DetailInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Image(
                painter = painterResource(R.drawable.ic_add_check_blue_circle),
                contentDescription = "已选择",
                modifier = Modifier
                    .size(28.dp)
                    .scale(1.45f),
            )
        }
    }
}

@Composable
private fun DetailPrimaryButton(
    label: String,
    busy: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (busy) DetailBlue.copy(alpha = 0.45f) else DetailBlue)
            .noRippleClickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (busy) "处理中" else label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailSecondaryButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF1F2F6))
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = DetailInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailDangerButton(label: String, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (busy) DetailDanger.copy(alpha = 0.45f) else DetailDanger)
            .noRippleClickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (busy) "处理中" else label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailStatusStrip(message: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.09f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(message, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DetailSoftLine),
    )
}

@Composable
private fun DetailMessageBanner(message: String, onExpired: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        delay(2_500)
        onExpired()
    }
    Surface(
        modifier = modifier,
        color = DetailInk.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun detailNodeIcon(node: StorageNode): Int = when {
    node.type == StorageNodeType.FOLDER -> R.drawable.ic_add_folder_solid_blue
    node.mimeType.orEmpty().startsWith("image/") -> R.drawable.ic_add_photo_color
    node.mimeType.orEmpty().startsWith("video/") -> R.drawable.ic_add_video_color
    node.mimeType.orEmpty().startsWith("audio/") -> R.drawable.ic_add_audio_color
    node.extension.equals("zip", true) || node.extension.equals("rar", true) || node.extension.equals("7z", true) -> R.drawable.ic_add_archive_color
    node.extension.equals("pdf", true) -> R.drawable.ic_add_pdf_red
    node.extension.equals("doc", true) || node.extension.equals("docx", true) -> R.drawable.ic_add_doc_blue
    else -> R.drawable.ic_add_document_color
}

private fun detailDocumentTitle(node: StorageNode): String = when {
    node.extension.equals("pdf", true) -> "PDF 文档"
    node.extension.equals("doc", true) || node.extension.equals("docx", true) -> "Word 文档"
    node.extension.equals("zip", true) || node.extension.equals("rar", true) || node.extension.equals("7z", true) -> "压缩文件"
    else -> "${node.extension?.uppercase()?.takeIf(String::isNotBlank) ?: "文件"} 文件"
}

private fun detailFileTypeLabel(node: StorageNode): String {
    val extension = node.extension?.trim()?.trimStart('.')?.uppercase().orEmpty()
    val mimeType = node.mimeType?.trim().orEmpty()
    return when {
        extension.isNotBlank() && mimeType.isNotBlank() -> "$extension · $mimeType"
        extension.isNotBlank() -> "$extension 文件"
        mimeType.isNotBlank() -> mimeType
        else -> "普通文件"
    }
}

private fun placeholderImageNode(fileName: String) = StorageNode(
    id = 0,
    parentId = null,
    name = fileName,
    type = StorageNodeType.FILE,
    size = 0,
    extension = fileName.substringAfterLast('.', "png"),
    mimeType = "image/*",
    updatedAt = "",
    deletedAt = null,
)

private fun formatPlaybackTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
