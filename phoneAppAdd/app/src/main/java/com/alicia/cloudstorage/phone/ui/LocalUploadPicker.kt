package com.alicia.cloudstorage.phone.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val PickerInk = Color(0xFF111827)
private val PickerMuted = Color(0xFF8993A6)
private val PickerLine = Color(0xFFE8ECF3)
private val PickerBlue = Color(0xFF087CFF)
private val PickerBackground = Color(0xFFF8FAFD)

private data class LocalPickerEntry(
    val document: DocumentFile,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

@Composable
internal fun LocalUploadPicker(
    rootUri: Uri?,
    onRequestDirectoryAccess: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<LocalUploadSelection>) -> Unit,
) {
    val context = LocalContext.current
    val rootDocument = remember(rootUri) {
        rootUri?.let { uri -> DocumentFile.fromTreeUri(context, uri) }
    }
    var directoryStack by remember(rootUri) {
        mutableStateOf(rootDocument?.let(::listOf).orEmpty())
    }
    var selected by remember(rootUri) { mutableStateOf<List<LocalUploadSelection>>(emptyList()) }
    var entries by remember(rootUri) { mutableStateOf<List<LocalPickerEntry>>(emptyList()) }
    var loading by remember(rootUri) { mutableStateOf(false) }
    var loadError by remember(rootUri) { mutableStateOf<String?>(null) }
    val currentDirectory = directoryStack.lastOrNull()

    LaunchedEffect(currentDirectory?.uri) {
        if (currentDirectory == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        runCatching {
            withContext(Dispatchers.IO) {
                currentDirectory.listFiles()
                    .asSequence()
                    .filter { child -> child.exists() && child.canRead() && (child.isDirectory || child.isFile) }
                    .filter { child -> !child.name.orEmpty().startsWith(".") }
                    .map { child ->
                        LocalPickerEntry(
                            document = child,
                            name = child.name?.trim().orEmpty().ifBlank { "未命名项目" },
                            isDirectory = child.isDirectory,
                            sizeBytes = child.length().takeIf { size -> child.isFile && size >= 0L },
                        )
                    }
                    .sortedWith(
                        compareByDescending<LocalPickerEntry> { entry -> entry.isDirectory }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.name },
                    )
                    .toList()
            }
        }.onSuccess { loaded ->
            entries = loaded
        }.onFailure { error ->
            entries = emptyList()
            loadError = error.message ?: "无法读取这个目录。"
        }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = PickerBackground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                LocalPickerHeader(
                    onBack = {
                        if (directoryStack.size > 1) {
                            directoryStack = directoryStack.dropLast(1)
                        } else {
                            onDismiss()
                        }
                    },
                    onChangeAccess = onRequestDirectoryAccess,
                )

                if (rootDocument == null) {
                    LocalPickerAccessPrompt(onRequestDirectoryAccess)
                } else {
                    Text(
                        text = directoryStack.joinToString(" / ") { directory ->
                            directory.name?.trim().orEmpty().ifBlank { "已授权目录" }
                        },
                        color = PickerMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    HorizontalDivider(color = PickerLine)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            loading -> CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(28.dp),
                                color = PickerBlue,
                                strokeWidth = 2.5.dp,
                            )

                            loadError != null -> Text(
                                text = loadError.orEmpty(),
                                color = PickerMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )

                            entries.isEmpty() -> Text(
                                text = "这个目录是空的",
                                color = PickerMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center),
                            )

                            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(entries, key = { entry -> entry.document.uri.toString() }) { entry ->
                                    val selection = entry.toUploadSelection()
                                    val checked = selected.any { item -> item.uri == selection.uri && item.kind == selection.kind }
                                    LocalPickerEntryRow(
                                        entry = entry,
                                        checked = checked,
                                        onOpen = {
                                            if (entry.isDirectory) {
                                                directoryStack = directoryStack + entry.document
                                            } else {
                                                selected = toggleSelection(context, selected, selection)
                                            }
                                        },
                                        onToggle = {
                                            selected = toggleSelection(context, selected, selection)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    LocalPickerFooter(
                        selectionCount = selected.size,
                        onCancel = onDismiss,
                        onConfirm = { onConfirm(selected) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalPickerHeader(
    onBack: () -> Unit,
    onChangeAccess: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddTopBackButton(onClick = onBack)
        Text(
            text = "选择文件或文件夹",
            color = PickerInk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .height(36.dp)
                .background(Color(0xFFEAF2FF), RoundedCornerShape(12.dp))
                .noRippleClickable(onClick = onChangeAccess)
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = PickerBlue,
                modifier = Modifier.size(17.dp),
            )
            Text("切换目录", color = PickerBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LocalPickerAccessPrompt(onRequestDirectoryAccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = PickerBlue,
            modifier = Modifier.size(54.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text("授权一个本地目录", color = PickerInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "首次需要由系统确认访问范围，之后会直接进入 Alicia 的文件选择页。",
            color = PickerMuted,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(22.dp))
        AddActionButton(
            label = "选择授权目录",
            onClick = onRequestDirectoryAccess,
            modifier = Modifier.width(190.dp),
            primary = true,
        )
    }
}

@Composable
private fun LocalPickerEntryRow(
    entry: LocalPickerEntry,
    checked: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .noRippleClickable(onClick = onOpen)
            .padding(start = 18.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(Color(0xFFF0F5FF), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) PickerBlue else Color(0xFF4F5D75),
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = PickerInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.isDirectory) "文件夹，点击进入" else entry.sizeBytes.toReadableSize(),
                color = PickerMuted,
                fontSize = 11.5.sp,
            )
        }
        SelectionCircle(
            selected = checked,
            onClick = onToggle,
            size = 24.dp,
        )
    }
    HorizontalDivider(color = PickerLine, modifier = Modifier.padding(start = 72.dp))
}

@Composable
private fun LocalPickerFooter(
    selectionCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("已选择 $selectionCount 项", color = PickerMuted, fontSize = 13.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AddActionButton(
                label = "取消",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                primary = false,
            )
            AddActionButton(
                label = "确认选择",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = selectionCount > 0,
            )
        }
    }
}

private fun LocalPickerEntry.toUploadSelection(): LocalUploadSelection =
    LocalUploadSelection(
        uri = if (isDirectory) document.uri.asTreeUriForDirectory() else document.uri,
        kind = if (isDirectory) LocalUploadSelectionKind.FOLDER else LocalUploadSelectionKind.FILE,
        displayName = name,
    )

private fun Uri.asTreeUriForDirectory(): Uri = runCatching {
    val authority = requireNotNull(authority)
    val documentId = DocumentsContract.getDocumentId(this)
    DocumentsContract.buildTreeDocumentUri(authority, documentId)
}.getOrDefault(this)

private fun toggleSelection(
    context: Context,
    current: List<LocalUploadSelection>,
    target: LocalUploadSelection,
): List<LocalUploadSelection> {
    if (current.any { item -> item.uri == target.uri && item.kind == target.kind }) {
        return current.filterNot { item -> item.uri == target.uri && item.kind == target.kind }
    }
    if (current.any { item ->
            item.kind == LocalUploadSelectionKind.FOLDER && context.isDocumentDescendant(target.uri, item.uri)
        }
    ) {
        return current
    }
    val retained = if (target.kind == LocalUploadSelectionKind.FOLDER) {
        current.filterNot { item -> context.isDocumentDescendant(item.uri, target.uri) }
    } else {
        current
    }
    return retained + target
}

private fun Context.isDocumentDescendant(candidateUri: Uri, ancestorUri: Uri): Boolean {
    if (candidateUri.authority != ancestorUri.authority) {
        return false
    }
    val candidateId = candidateUri.documentIdOrNull() ?: return false
    val ancestorId = ancestorUri.documentIdOrNull() ?: return false
    return candidateId != ancestorId && candidateId.startsWith("$ancestorId/")
}

private fun Uri.documentIdOrNull(): String? = runCatching {
    if (DocumentsContract.isTreeUri(this)) {
        DocumentsContract.getTreeDocumentId(this)
    } else {
        DocumentsContract.getDocumentId(this)
    }
}.getOrNull()

private fun Long?.toReadableSize(): String {
    val size = this ?: return "大小未知"
    return when {
        size >= 1024L * 1024L * 1024L -> String.format(
            Locale.getDefault(),
            "%.1f GB",
            size / (1024.0 * 1024.0 * 1024.0),
        )
        size >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0))
        size >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", size / 1024.0)
        else -> "$size B"
    }
}
