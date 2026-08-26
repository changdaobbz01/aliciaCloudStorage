package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNode

internal const val MAX_BATCH_NODE_OPERATION_ITEMS = 500
internal const val MAX_ARCHIVE_ROOTS = 100
internal const val MAX_SHARE_TARGETS = 20
internal const val MAX_SHARE_SAVE_SELECTED_ITEMS = 500

internal data class NodeIdSelectionValidation(
    val nodeIds: List<Long>,
    val errorMessage: String? = null,
) {
    val isValid: Boolean
        get() = errorMessage == null
}

internal data class StorageNodeSelectionValidation(
    val nodes: List<StorageNode>,
    val errorMessage: String? = null,
) {
    val isValid: Boolean
        get() = errorMessage == null
}

internal fun validateBatchNodeIds(
    rawNodeIds: Iterable<Long>,
    emptyMessage: String,
): NodeIdSelectionValidation {
    val nodeIds = normalizeNodeIds(rawNodeIds)
    val errorMessage = when {
        nodeIds.isEmpty() -> emptyMessage
        nodeIds.size > MAX_BATCH_NODE_OPERATION_ITEMS -> "单次最多处理 $MAX_BATCH_NODE_OPERATION_ITEMS 个项目。"
        else -> null
    }
    return NodeIdSelectionValidation(nodeIds, errorMessage)
}

internal fun validateArchiveNodeIds(
    rawNodeIds: Iterable<Long>,
    emptyMessage: String = "请先选择要下载的项目。",
): NodeIdSelectionValidation {
    val nodeIds = normalizeNodeIds(rawNodeIds)
    val errorMessage = when {
        nodeIds.isEmpty() -> emptyMessage
        nodeIds.size > MAX_ARCHIVE_ROOTS -> "单次最多打包下载 $MAX_ARCHIVE_ROOTS 个项目，请减少选择后重试。"
        else -> null
    }
    return NodeIdSelectionValidation(nodeIds, errorMessage)
}

internal fun validateShareSaveNodeIds(rawNodeIds: Iterable<Long>): NodeIdSelectionValidation {
    val nodeIds = normalizeNodeIds(rawNodeIds)
    val errorMessage = when {
        nodeIds.isEmpty() -> "请先选择要保存的分享内容。"
        nodeIds.size > MAX_SHARE_SAVE_SELECTED_ITEMS ->
            "单次最多保存 $MAX_SHARE_SAVE_SELECTED_ITEMS 个分享项目，请减少选择后重试。"
        else -> null
    }
    return NodeIdSelectionValidation(nodeIds, errorMessage)
}

internal fun validateShareNodes(rawNodes: List<StorageNode>): StorageNodeSelectionValidation {
    val nodes = rawNodes
        .asSequence()
        .filter { it.id > 0L }
        .distinctBy(StorageNode::id)
        .toList()
    val errorMessage = when {
        nodes.isEmpty() -> "请先选择要分享的文件或文件夹。"
        nodes.size > MAX_SHARE_TARGETS -> "单个分享最多包含 $MAX_SHARE_TARGETS 个项目。"
        else -> null
    }
    return StorageNodeSelectionValidation(nodes, errorMessage)
}

private fun normalizeNodeIds(rawNodeIds: Iterable<Long>): List<Long> =
    rawNodeIds
        .asSequence()
        .filter { it > 0L }
        .distinct()
        .toList()
