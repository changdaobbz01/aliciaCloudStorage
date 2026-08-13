package com.alicia.cloudstorage.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alicia.cloudstorage.phone.data.StorageNode

private val NodeDialogInk = Color(0xFF111827)
private val NodeDialogDanger = Color(0xFFE84D3D)

@Composable
internal fun NodeNameDialog(
    node: StorageNode,
    submitting: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val initialSelection = remember(node.id, node.name, node.type) {
        initialNodeNameSelection(node.name, node.type)
    }
    NodeNameInputDialog(
        stateKey = "rename-${node.id}",
        title = "修改名称",
        fieldLabel = "名称",
        placeholder = "请输入新的名称",
        initialName = node.name,
        initialSelection = initialSelection,
        currentName = node.name,
        submitting = submitting,
        serverError = serverError,
        submitLabel = "确定",
        submittingLabel = "修改中",
        onDismiss = onDismiss,
        onSubmit = onSubmit,
    )
}

@Composable
internal fun CreateFolderNameDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    NodeNameInputDialog(
        stateKey = "create-folder",
        title = "新建文件夹",
        fieldLabel = "文件夹名称",
        placeholder = "请输入文件夹名称",
        initialName = "",
        initialSelection = NodeNameSelection(0, 0),
        currentName = null,
        submitting = creating,
        serverError = null,
        submitLabel = "创建",
        submittingLabel = "创建中",
        onDismiss = onDismiss,
        onSubmit = onCreate,
    )
}

@Composable
private fun NodeNameInputDialog(
    stateKey: String,
    title: String,
    fieldLabel: String,
    placeholder: String,
    initialName: String,
    initialSelection: NodeNameSelection,
    currentName: String?,
    submitting: Boolean,
    serverError: String?,
    submitLabel: String,
    submittingLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by rememberSaveable(stateKey, initialName, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(initialSelection.start, initialSelection.endExclusive),
            ),
        )
    }
    var localError by remember(stateKey, initialName) { mutableStateOf<String?>(null) }
    var hideServerError by remember(stateKey, serverError) { mutableStateOf(false) }
    val validation = validateNodeName(value.text, currentName)
    val submit = {
        if (!submitting) {
            if (validation.isValid) {
                localError = null
                onSubmit(validation.normalizedName)
            } else {
                localError = validation.errorMessage
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !submitting,
            dismissOnClickOutside = !submitting,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .addCardChrome(shape)
                .clip(shape)
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = title,
                color = NodeDialogInk,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            AddTextFieldValue(
                value = value,
                onValueChange = {
                    value = it
                    localError = null
                    hideServerError = true
                },
                label = fieldLabel,
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            (serverError?.takeUnless { hideServerError } ?: localError)?.let { error ->
                Text(
                    text = error,
                    color = NodeDialogDanger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AddActionButton(
                    label = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    primary = false,
                    enabled = !submitting,
                )
                AddActionButton(
                    label = if (submitting) submittingLabel else submitLabel,
                    onClick = submit,
                    modifier = Modifier.weight(1f),
                    primary = true,
                    enabled = validation.isValid && !submitting,
                )
            }
        }
    }
}
