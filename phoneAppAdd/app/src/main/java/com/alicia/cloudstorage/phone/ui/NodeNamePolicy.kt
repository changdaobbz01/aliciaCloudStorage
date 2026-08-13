package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNodeType

internal const val MAX_NODE_NAME_LENGTH = 255

internal data class NodeNameValidation(
    val normalizedName: String,
    val errorMessage: String? = null,
) {
    val isValid: Boolean
        get() = errorMessage == null
}

internal data class NodeNameSelection(
    val start: Int,
    val endExclusive: Int,
)

internal fun validateNodeName(
    rawName: String,
    currentName: String? = null,
): NodeNameValidation {
    val normalizedName = rawName.trim()
    val errorMessage = when {
        normalizedName.isEmpty() -> "名称不能为空。"
        normalizedName.length > MAX_NODE_NAME_LENGTH -> "名称长度不能超过 $MAX_NODE_NAME_LENGTH 个字符。"
        '/' in normalizedName || '\\' in normalizedName -> "名称不能包含斜杠。"
        currentName != null && normalizedName == currentName -> "请输入新的名称。"
        else -> null
    }
    return NodeNameValidation(normalizedName, errorMessage)
}

internal fun initialNodeNameSelection(
    name: String,
    nodeType: StorageNodeType,
): NodeNameSelection {
    if (nodeType != StorageNodeType.FILE) {
        return NodeNameSelection(0, name.length)
    }

    val extensionSeparator = name.lastIndexOf('.')
        .takeIf { index -> index > 0 && index < name.lastIndex }
        ?: name.length
    return NodeNameSelection(0, extensionSeparator)
}
