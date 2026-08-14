package com.alicia.cloudstorage.phone.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.alicia.cloudstorage.phone.BuildConfig
import com.alicia.cloudstorage.phone.R
import kotlin.math.roundToInt

private val AiInk = Color(0xFF111827)
private val AiMuted = Color(0xFF8993A6)
private val AiLine = Color(0xFFE9EDF5)
private val AiBlue = Color(0xFF087CFF)
private val AiFieldFocusBlue = Color(0xFF005CFF)
private val AiOnline = Color(0xFF13C77A)

@Composable
internal fun AiChatRoute(
    onBack: () -> Unit,
    onFileMutation: (AiChatFileMutationSignal) -> Unit,
    onOpenFileResult: (AiChatFileResult) -> Unit,
    onClientUpload: (AiChatClientUploadRequest, AiChatClientUploadCallbacks) -> Unit,
    onAttachFiles: ((List<AiChatPendingAttachment>) -> Unit) -> Unit,
    onClearAttachedFiles: () -> Unit,
    modifier: Modifier = Modifier,
    ragBaseUrl: String = BuildConfig.DEFAULT_RAG_BASE_URL,
    apiBaseUrl: String = BuildConfig.DEFAULT_API_BASE_URL,
    actionExecutionEnabled: Boolean = BuildConfig.RAG_ACTION_EXECUTION_ENABLED,
    confirmationMessage: String = BuildConfig.RAG_CONFIRMATION_MESSAGE,
    authToken: String = "",
    userAvatarUrl: String? = null,
    userDisplayName: String = "用户",
    currentFolderId: Long? = null,
    currentFolderPath: String = "根目录",
) {
    val context = LocalContext.current
    val viewModelKey = remember(ragBaseUrl, apiBaseUrl, actionExecutionEnabled, confirmationMessage, authToken) {
        "rag-assistant-${ragBaseUrl.hashCode()}-${apiBaseUrl.hashCode()}-$actionExecutionEnabled-${confirmationMessage.hashCode()}-${authToken.hashCode()}"
    }
    val viewModel: RagAssistantViewModel = composeViewModel(
        key = viewModelKey,
        factory = RagAssistantViewModel.provideFactory(
            context = context.applicationContext,
            ragBaseUrl = ragBaseUrl,
            apiBaseUrl = apiBaseUrl,
            actionExecutionEnabled = actionExecutionEnabled,
            confirmationMessage = confirmationMessage,
            authToken = authToken,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmNewConversation by remember { mutableStateOf(false) }
    val currentOnFileMutation by rememberUpdatedState(onFileMutation)
    val currentOnClientUpload by rememberUpdatedState(onClientUpload)
    val currentOnClearAttachedFiles by rememberUpdatedState(onClearAttachedFiles)
    val currentOnOpenFileResult by rememberUpdatedState(onOpenFileResult)

    androidx.compose.runtime.LaunchedEffect(viewModel, currentFolderId, currentFolderPath) {
        viewModel.updateFileContext(currentFolderId, currentFolderPath)
    }

    LaunchedEffect(viewModel) {
        viewModel.fileMutationSignals.collect { signal ->
            currentOnFileMutation(signal)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.clientUploadRequests.collect { launch ->
            currentOnClientUpload(
                launch.request,
                AiChatClientUploadCallbacks(
                    onSelectionResolved = { filesSelected ->
                        viewModel.completeClientUploadSelection(launch, filesSelected)
                    },
                    onExecutionCompleted = { result ->
                        viewModel.completeClientUploadExecution(launch, result)
                    },
                ),
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.composerAttachmentClearRequests.collect {
            currentOnClearAttachedFiles()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationRequests.collect { target ->
            currentOnOpenFileResult(target)
        }
    }

    AiConversationViewport(
        state = state,
        onAction = { action ->
            when (action) {
                AiChatAction.Back -> onBack()
                AiChatAction.NewConversation -> {
                    if (!state.sending && state.hasConversationContent()) {
                        confirmNewConversation = true
                    }
                }
                AiChatAction.Attach -> {
                    if (viewModel.prepareComposerAttachment()) {
                        onAttachFiles { attachments ->
                            viewModel.completeComposerAttachmentSelection(attachments)
                        }
                    }
                }
                AiChatAction.ClearAttachments -> {
                    viewModel.clearComposerAttachments()
                    onClearAttachedFiles()
                }
                AiChatAction.Send -> viewModel.sendMessage()
                is AiChatAction.DraftChanged -> viewModel.updateDraft(action.value)
                is AiChatAction.OpenFile -> onOpenFileResult(action.file)
                is AiChatAction.SelectCandidate -> viewModel.selectCandidate(action.messageId, action.file)
                is AiChatAction.RunClientUpload -> viewModel.runClientUpload(action.messageId, action.request)
                is AiChatAction.ConfirmReview -> viewModel.confirmReview(action.messageId)
                is AiChatAction.CancelReview -> viewModel.cancelReview(action.messageId)
            }
        },
        userAvatarUrl = userAvatarUrl,
        userDisplayName = userDisplayName,
        modifier = modifier,
    )

    if (confirmNewConversation) {
        AliciaConfirmDialog(
            title = "新建对话",
            message = "新建后将清空当前对话、未发送文字和已选附件。",
            confirmLabel = "新建",
            onDismiss = { confirmNewConversation = false },
            onConfirm = {
                confirmNewConversation = false
                viewModel.startNewConversation()
                onClearAttachedFiles()
            },
        )
    }
}

@Composable
internal fun AiConversationViewport(
    state: AiChatUiState,
    onAction: (AiChatAction) -> Unit,
    userAvatarUrl: String? = null,
    userDisplayName: String = "用户",
    modifier: Modifier = Modifier,
) {
    val viewportShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val listState = rememberLazyListState()
    val showSendingPlaceholder = state.sending &&
        state.messages.lastOrNull()?.author != AiChatAuthor.ASSISTANT
    val bottomAnchorIndex = state.messages.size + if (showSendingPlaceholder) 1 else 0

    LaunchedEffect(
        state.messages.size,
        state.messages.lastOrNull(),
        showSendingPlaceholder,
        state.pendingAttachments.size,
    ) {
        androidx.compose.runtime.withFrameNanos { }
        if (state.sending) {
            listState.scrollToItem(bottomAnchorIndex)
        } else {
            listState.animateScrollToItem(bottomAnchorIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .shadow(
                elevation = 3.dp,
                shape = viewportShape,
                ambientColor = Color.Black.copy(alpha = 0.02f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            )
            .clip(viewportShape)
            .background(Color.White)
            .border(1.dp, AiLine.copy(alpha = 0.8f), viewportShape),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AiConversationHeader(
                online = state.online,
                newConversationEnabled = !state.sending && state.hasConversationContent(),
                onBack = { onAction(AiChatAction.Back) },
                onNewConversation = { onAction(AiChatAction.NewConversation) },
            )
            HorizontalDivider(color = AiLine, thickness = 1.dp)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    top = 16.dp,
                    end = 18.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = AiChatMessage::id) { message ->
                    AiMessageItem(
                        message = message,
                        userAvatarUrl = userAvatarUrl,
                        userDisplayName = userDisplayName,
                        actionsEnabled = !state.sending,
                        onOpenFile = { file -> onAction(AiChatAction.OpenFile(file)) },
                        onSelectCandidate = { messageId, file ->
                            onAction(AiChatAction.SelectCandidate(messageId, file))
                        },
                        onClientUpload = { messageId, request ->
                            onAction(AiChatAction.RunClientUpload(messageId, request))
                        },
                        onConfirmReview = { messageId -> onAction(AiChatAction.ConfirmReview(messageId)) },
                        onCancelReview = { messageId -> onAction(AiChatAction.CancelReview(messageId)) },
                    )
                }
                if (showSendingPlaceholder) {
                    item(key = "ai-sending") {
                        AiMessageItem(
                            message = AiChatMessage(
                                id = Long.MIN_VALUE,
                                author = AiChatAuthor.ASSISTANT,
                                text = "安安正在思考...",
                            ),
                            userAvatarUrl = userAvatarUrl,
                            userDisplayName = userDisplayName,
                            actionsEnabled = false,
                            onOpenFile = {},
                            onSelectCandidate = { _, _ -> },
                            onClientUpload = { _, _ -> },
                            onConfirmReview = {},
                            onCancelReview = {},
                        )
                    }
                }
                item(key = "ai-bottom-anchor") {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
            HorizontalDivider(color = AiLine, thickness = 1.dp)
            AiMessageComposer(
                value = state.draft,
                sending = state.sending,
                pendingAttachments = state.pendingAttachments,
                onValueChange = { onAction(AiChatAction.DraftChanged(it)) },
                onAttach = { onAction(AiChatAction.Attach) },
                onClearAttachments = { onAction(AiChatAction.ClearAttachments) },
                onSend = { onAction(AiChatAction.Send) },
            )
        }
    }
}

@Composable
private fun AiConversationHeader(
    online: Boolean,
    newConversationEnabled: Boolean,
    onBack: () -> Unit,
    onNewConversation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ai_back),
                contentDescription = "返回",
                modifier = Modifier.size(26.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "安安",
                color = AiInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (online) AiOnline else AiMuted),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (online) "在线" else "离线",
                    color = AiMuted,
                    fontSize = 11.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AiBlue.copy(alpha = 0.08f))
                .clickable(enabled = newConversationEnabled, onClick = onNewConversation),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AddComment,
                contentDescription = "新建对话",
                tint = AiBlue.copy(alpha = if (newConversationEnabled) 1f else 0.42f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun AiMessageItem(
    message: AiChatMessage,
    userAvatarUrl: String?,
    userDisplayName: String,
    actionsEnabled: Boolean,
    onOpenFile: (AiChatFileResult) -> Unit,
    onSelectCandidate: (Long, AiChatFileResult) -> Unit,
    onClientUpload: (Long, AiChatClientUploadRequest) -> Unit,
    onConfirmReview: (Long) -> Unit,
    onCancelReview: (Long) -> Unit,
) {
    if (message.author == AiChatAuthor.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RasterPanel(
                resourceId = R.drawable.bg_ai_message_user,
                modifier = Modifier.widthIn(max = 248.dp),
            ) {
                Text(
                    text = message.text,
                    color = AiInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
            UserAvatar(
                url = userAvatarUrl,
                fallback = userDisplayName,
                size = 34.dp,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            AiAssistantAvatar()
            Column(
                modifier = Modifier.widthIn(max = 334.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                AiAssistantMessageBubble(message.text)
                message.plan?.let { plan ->
                    AiPlanPreviewCard(
                        plan = plan,
                        actionsEnabled = actionsEnabled,
                        onClientUpload = { request -> onClientUpload(message.id, request) },
                        onConfirm = { onConfirmReview(message.id) },
                        onCancel = { onCancelReview(message.id) },
                    )
                }
                message.resultSection?.let { section ->
                    AiResultSection(
                        messageId = message.id,
                        section = section,
                        files = message.files,
                        actionsEnabled = actionsEnabled,
                        onFileClick = { file ->
                            if (file.selectionAction != null) {
                                onSelectCandidate(message.id, file)
                            } else {
                                onOpenFile(file)
                            }
                        },
                    )
                } ?: message.files.forEach { file ->
                    AiFileResultRow(
                        file = file,
                        actionsEnabled = actionsEnabled,
                        onClick = {
                            if (file.selectionAction != null) {
                                onSelectCandidate(message.id, file)
                            } else {
                                onOpenFile(file)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiAssistantMessageBubble(text: String) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .widthIn(max = 294.dp)
            .shadow(elevation = 2.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, AiLine, shape),
    ) {
        AiMarkdownText(
            markdown = text,
            color = AiInk,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
        )
    }
}

@Composable
private fun AiResultSection(
    messageId: Long,
    section: AiChatResultSection,
    files: List<AiChatFileResult>,
    actionsEnabled: Boolean,
    onFileClick: (AiChatFileResult) -> Unit,
) {
    if (files.isEmpty()) {
        return
    }

    val fileIds = remember(files) { files.map(AiChatFileResult::id) }
    var expanded by rememberSaveable(messageId, fileIds) { mutableStateOf(false) }
    val canCollapse = files.none { it.selectionAction != null } &&
        AiChatResultDisplayPolicy.canCollapse(section, files.size)
    val visibleCount = AiChatResultDisplayPolicy.visibleItemCount(
        section = section,
        itemCount = files.size,
        expanded = expanded,
    )
    val groupShape = RoundedCornerShape(8.dp)
    val headerIcon = if (files.all { it.type.equals("FOLDER", ignoreCase = true) }) {
        R.drawable.ic_add_folder_solid_blue
    } else {
        R.drawable.ic_ai_document
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(groupShape)
                .background(Color.White)
                .border(1.dp, AiLine, groupShape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(headerIcon),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        color = AiInk,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = AiChatResultDisplayPolicy.countLabel(section, files.size),
                        color = AiMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(color = AiLine, thickness = 1.dp)
            files.take(visibleCount).forEachIndexed { index, file ->
                AiFileResultRow(
                    file = file,
                    actionsEnabled = actionsEnabled,
                    grouped = true,
                    onClick = { onFileClick(file) },
                )
                if (index < visibleCount - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 50.dp),
                        color = AiLine,
                        thickness = 1.dp,
                    )
                }
            }

            if (canCollapse) {
                HorizontalDivider(color = AiLine, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .semantics {
                            stateDescription = if (expanded) "已展开" else "已收起"
                        }
                        .clickable(role = Role.Button) {
                            expanded = !expanded
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = AiChatResultDisplayPolicy.toggleLabel(
                            section = section,
                            itemCount = files.size,
                            expanded = expanded,
                        ),
                        color = AiBlue,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = AiBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (expanded) {
            AiChatResultDisplayPolicy.partialResultLabel(section, files.size)?.let { label ->
                Text(
                    text = label,
                    color = AiMuted,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        if (section.mode == AiChatResultMode.SEARCH_RESULTS) {
            Text(
                text = "仅完成查找，不会修改文件",
                color = AiMuted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun AiAssistantAvatar() {
    Image(
        painter = painterResource(R.drawable.ai_assistant_chat_avatar),
        contentDescription = null,
        modifier = Modifier.size(34.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun AiPlanPreviewCard(
    plan: AiChatPlanPreview,
    actionsEnabled: Boolean,
    onClientUpload: (AiChatClientUploadRequest) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFF7FAFF))
            .border(1.dp, AiLine, RoundedCornerShape(15.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = plan.title,
            color = AiBlue,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
        plan.lines.take(6).forEach { line ->
            Text(
                text = "· $line",
                color = AiMuted,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }
        plan.clientActionControls?.let { controls ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiPlanActionButton(
                    label = controls.label,
                    enabled = actionsEnabled,
                    primary = true,
                    destructive = false,
                    onClick = { onClientUpload(controls.uploadRequest) },
                )
            }
        }
        plan.actionControls?.let { controls ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiPlanActionButton(
                    label = controls.cancelLabel,
                    enabled = actionsEnabled,
                    primary = false,
                    destructive = false,
                    onClick = onCancel,
                )
                Spacer(modifier = Modifier.width(8.dp))
                AiPlanActionButton(
                    label = controls.confirmLabel,
                    enabled = actionsEnabled,
                    primary = true,
                    destructive = controls.destructive,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun AiPlanActionButton(
    label: String,
    enabled: Boolean,
    primary: Boolean,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        !enabled -> Color(0xFFE5EAF5)
        primary && destructive -> Color(0xFFE64A59)
        primary -> AiBlue
        else -> Color(0xFFEFF4FB)
    }
    val textColor = when {
        !enabled -> AiMuted
        primary -> Color.White
        else -> AiInk
    }

    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .widthIn(min = 64.dp, max = 190.dp)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiFileResultRow(
    file: AiChatFileResult,
    actionsEnabled: Boolean,
    grouped: Boolean = false,
    onClick: () -> Unit,
) {
    val iconResource = if (file.type.equals("FOLDER", ignoreCase = true)) {
        R.drawable.ic_add_folder_solid_blue
    } else {
        R.drawable.ic_ai_document
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .height(if (grouped) 64.dp else 70.dp)
        .clickable(
            enabled = file.selectionAction == null || actionsEnabled,
            onClick = onClick,
        )

    if (grouped) {
        AiFileResultContent(
            file = file,
            actionsEnabled = actionsEnabled,
            iconResource = iconResource,
            modifier = rowModifier,
        )
    } else {
        RasterPanel(
            resourceId = R.drawable.bg_ai_file_card,
            modifier = rowModifier,
        ) {
            AiFileResultContent(
                file = file,
                actionsEnabled = actionsEnabled,
                iconResource = iconResource,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AiFileResultContent(
    file: AiChatFileResult,
    actionsEnabled: Boolean,
    @DrawableRes iconResource: Int,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconResource),
            contentDescription = null,
            modifier = Modifier.size(29.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = AiInk,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = file.detail,
                color = AiMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_ai_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            contentScale = ContentScale.Fit,
        )
        file.selectionAction?.let { action ->
            Spacer(modifier = Modifier.width(7.dp))
            AiCandidateSelectBadge(
                label = action.label,
                enabled = actionsEnabled,
            )
        }
    }
}

@Composable
private fun AiCandidateSelectBadge(
    label: String,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .height(27.dp)
            .width(49.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) AiBlue else Color(0xFFE5EAF5)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else AiMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RasterPanel(
    @DrawableRes resourceId: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        content()
    }
}

@Composable
private fun AiMessageComposer(
    value: String,
    sending: Boolean,
    pendingAttachments: List<AiChatPendingAttachment>,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onClearAttachments: () -> Unit,
    onSend: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val canSend = value.isNotBlank() && !sending
    val hasAttachments = pendingAttachments.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (pendingAttachments.isNotEmpty()) {
            AiPendingAttachmentStrip(
                attachments = pendingAttachments,
                onClear = onClearAttachments,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (hasAttachments) AiBlue.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(enabled = !sending, onClick = onAttach)
                    .alpha(if (sending) 0.35f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "添加附件",
                    tint = if (hasAttachments) AiBlue else AiInk,
                    modifier = Modifier.size(24.dp),
                )
                if (hasAttachments) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(AiBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (pendingAttachments.size <= 9) {
                                pendingAttachments.size.toString()
                            } else {
                                "9+"
                            },
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            AiDraftField(
                value = value,
                onValueChange = onValueChange,
                onSend = { submitAiMessage(canSend, focusManager, onSend) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
            )
            Image(
                painter = painterResource(
                    if (canSend) R.drawable.ic_ai_send_enabled else R.drawable.ic_ai_send_disabled,
                ),
                contentDescription = "发送",
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canSend) {
                        submitAiMessage(canSend, focusManager, onSend)
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun AiPendingAttachmentStrip(
    attachments: List<AiChatPendingAttachment>,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 47.dp, end = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        attachments.take(2).forEach { attachment ->
            AiPendingAttachmentChip(
                attachment = attachment,
                modifier = Modifier.weight(1f),
            )
        }
        if (attachments.size > 2) {
            Text(
                text = "+${attachments.size - 2}",
                color = AiMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF4FB))
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "移除附件",
                tint = AiMuted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun AiPendingAttachmentChip(
    attachment: AiChatPendingAttachment,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFFF3F7FF))
            .border(1.dp, AiLine, RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attachment.isFolder) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = AiBlue,
                modifier = Modifier.size(19.dp),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_ai_document),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = attachment.name,
            color = AiInk,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiDraftField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "ai-composer-focus",
    )

    Box(modifier = modifier) {
        HorizontalStretchRaster(
            resourceId = R.drawable.bg_ai_composer_field,
            modifier = Modifier
                .matchParentSize()
                .alpha(1f - focusAlpha),
        )
        HorizontalStretchRaster(
            resourceId = R.drawable.bg_ai_composer_field_focused,
            modifier = Modifier
                .matchParentSize()
                .alpha(focusAlpha),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            textStyle = TextStyle(color = AiInk, fontSize = 13.sp, lineHeight = 19.sp),
            cursorBrush = SolidColor(AiFieldFocusBlue),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "问问安安...",
                            color = Color(0xFFABB4C4),
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun HorizontalStretchRaster(
    @DrawableRes resourceId: Int,
    modifier: Modifier = Modifier,
) {
    val image = ImageBitmap.imageResource(resourceId)
    Canvas(modifier = modifier) {
        val destinationWidth = size.width.roundToInt().coerceAtLeast(1)
        val destinationHeight = size.height.roundToInt().coerceAtLeast(1)
        val sourceCapWidth = (image.height / 2).coerceAtMost(image.width / 2)
        val destinationCapWidth = (destinationHeight / 2).coerceAtMost(destinationWidth / 2)
        val sourceCenterWidth = image.width - (sourceCapWidth * 2)
        val destinationCenterWidth = destinationWidth - (destinationCapWidth * 2)

        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(sourceCapWidth, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(destinationCapWidth, destinationHeight),
        )
        if (sourceCenterWidth > 0 && destinationCenterWidth > 0) {
            drawImage(
                image = image,
                srcOffset = IntOffset(sourceCapWidth, 0),
                srcSize = IntSize(sourceCenterWidth, image.height),
                dstOffset = IntOffset(destinationCapWidth, 0),
                dstSize = IntSize(destinationCenterWidth, destinationHeight),
            )
        }
        drawImage(
            image = image,
            srcOffset = IntOffset(image.width - sourceCapWidth, 0),
            srcSize = IntSize(sourceCapWidth, image.height),
            dstOffset = IntOffset(destinationWidth - destinationCapWidth, 0),
            dstSize = IntSize(destinationCapWidth, destinationHeight),
        )
    }
}

private fun submitAiMessage(
    enabled: Boolean,
    focusManager: FocusManager,
    onSend: () -> Unit,
) {
    if (!enabled) return
    onSend()
    focusManager.clearFocus()
}

private fun AiChatUiState.hasConversationContent(): Boolean =
    draft.isNotBlank() ||
        pendingAttachments.isNotEmpty() ||
        messages.any { message -> message.author == AiChatAuthor.USER }
