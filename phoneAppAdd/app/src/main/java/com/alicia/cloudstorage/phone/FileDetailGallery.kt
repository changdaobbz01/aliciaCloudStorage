package com.alicia.cloudstorage.phone

import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType

internal const val MAX_FILE_DETAIL_GALLERY_NODES = 80

internal fun normalizeFileDetailGalleryNodes(
    currentNode: StorageNode,
    nodes: List<StorageNode>,
): List<StorageNode> {
    val byId = linkedMapOf<Long, StorageNode>()
    nodes
        .asSequence()
        .filter { it.type == StorageNodeType.FILE }
        .forEach { node ->
            if (byId.size < MAX_FILE_DETAIL_GALLERY_NODES || byId.containsKey(node.id)) {
                byId[node.id] = if (node.id == currentNode.id) currentNode else node
            }
        }

    if (currentNode.type == StorageNodeType.FILE && !byId.containsKey(currentNode.id)) {
        val retained = byId.values.take(MAX_FILE_DETAIL_GALLERY_NODES - 1)
        return listOf(currentNode) + retained
    }

    return byId.values
        .take(MAX_FILE_DETAIL_GALLERY_NODES)
        .ifEmpty { listOf(currentNode) }
}
