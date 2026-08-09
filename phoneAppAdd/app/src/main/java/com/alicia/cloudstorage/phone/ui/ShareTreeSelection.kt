package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNode

internal object ShareTreeSelection {
    fun allNodeIds(items: List<StorageNode>): Set<Long> =
        items.mapTo(linkedSetOf()) { it.id }

    fun toggle(
        items: List<StorageNode>,
        selectedNodeIds: Set<Long>,
        nodeId: Long,
    ): Set<Long> {
        if (items.none { it.id == nodeId }) {
            return selectedNodeIds
        }

        val childrenByParent = items.groupBy { it.parentId }
        val parentById = items.associate { it.id to it.parentId }
        val subtreeIds = collectSubtreeIds(nodeId, childrenByParent)
        val nextSelection = selectedNodeIds.toMutableSet()

        if (subtreeIds.all(nextSelection::contains)) {
            nextSelection.removeAll(subtreeIds)
        } else {
            nextSelection.addAll(subtreeIds)
        }

        var parentId = parentById[nodeId]
        val visitedAncestors = mutableSetOf<Long>()
        while (parentId != null && visitedAncestors.add(parentId)) {
            val descendantIds = collectSubtreeIds(parentId, childrenByParent) - parentId
            if (descendantIds.isNotEmpty() && descendantIds.all(nextSelection::contains)) {
                nextSelection.add(parentId)
            } else {
                nextSelection.remove(parentId)
            }
            parentId = parentById[parentId]
        }

        return nextSelection
    }

    fun minimalSelectedRootIds(
        items: List<StorageNode>,
        selectedNodeIds: Set<Long>,
    ): List<Long> {
        if (selectedNodeIds.isEmpty()) {
            return emptyList()
        }

        val nodeIds = items.mapTo(hashSetOf()) { it.id }
        val parentById = items.associate { it.id to it.parentId }
        val childCountById = items.associate { it.id to 0 }.toMutableMap()
        items.forEach { node ->
            node.parentId?.takeIf(nodeIds::contains)?.let { parentId ->
                childCountById[parentId] = childCountById.getValue(parentId) + 1
            }
        }

        val remainingChildCount = childCountById.toMutableMap()
        val fullySelectedChildCount = mutableMapOf<Long, Int>()
        val fullySelectedNodeIds = mutableSetOf<Long>()
        val pending = ArrayDeque<Long>()
        childCountById.filterValues { it == 0 }.keys.forEach(pending::add)

        while (pending.isNotEmpty()) {
            val nodeId = pending.removeFirst()
            val fullySelected = nodeId in selectedNodeIds &&
                fullySelectedChildCount.getOrDefault(nodeId, 0) == childCountById.getValue(nodeId)
            if (fullySelected) {
                fullySelectedNodeIds.add(nodeId)
            }

            parentById[nodeId]?.takeIf(nodeIds::contains)?.let { parentId ->
                if (fullySelected) {
                    fullySelectedChildCount[parentId] = fullySelectedChildCount.getOrDefault(parentId, 0) + 1
                }
                val remaining = remainingChildCount.getValue(parentId) - 1
                remainingChildCount[parentId] = remaining
                if (remaining == 0) {
                    pending.add(parentId)
                }
            }
        }

        return items
            .filter { node ->
                node.id in fullySelectedNodeIds &&
                    (node.parentId == null || node.parentId !in fullySelectedNodeIds)
            }
            .map(StorageNode::id)
    }

    private fun collectSubtreeIds(
        rootNodeId: Long,
        childrenByParent: Map<Long?, List<StorageNode>>,
    ): Set<Long> {
        val collected = linkedSetOf<Long>()
        val pending = ArrayDeque<Long>()
        pending.add(rootNodeId)

        while (pending.isNotEmpty()) {
            val currentId = pending.removeLast()
            if (!collected.add(currentId)) {
                continue
            }
            childrenByParent[currentId].orEmpty().forEach { child -> pending.add(child.id) }
        }

        return collected
    }
}
