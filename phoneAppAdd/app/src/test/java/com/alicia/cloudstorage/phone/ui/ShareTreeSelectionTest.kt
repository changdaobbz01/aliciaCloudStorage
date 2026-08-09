package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTreeSelectionTest {
    private val folder = node(1, null, StorageNodeType.FOLDER)
    private val childFile = node(2, 1, StorageNodeType.FILE)
    private val childFolder = node(3, 1, StorageNodeType.FOLDER)
    private val grandchildFile = node(4, 3, StorageNodeType.FILE)
    private val separateFile = node(5, null, StorageNodeType.FILE)
    private val items = listOf(folder, childFile, childFolder, grandchildFile, separateFile)

    @Test
    fun selectingFolderSelectsEntireSubtree() {
        val selected = ShareTreeSelection.toggle(items, emptySet(), folder.id)

        assertEquals(setOf(1L, 2L, 3L, 4L), selected)
    }

    @Test
    fun deselectingChildClearsAncestorsButPreservesSiblings() {
        val allSelected = ShareTreeSelection.allNodeIds(items)
        val selected = ShareTreeSelection.toggle(items, allSelected, grandchildFile.id)

        assertEquals(setOf(2L, 5L), selected)
    }

    @Test
    fun selectingLastMissingChildRestoresAncestorSelection() {
        val selected = ShareTreeSelection.toggle(items, setOf(2L, 5L), grandchildFile.id)

        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), selected)
    }

    @Test
    fun submissionCollapsesFullySelectedSubtreesToMinimalRoots() {
        val roots = ShareTreeSelection.minimalSelectedRootIds(
            items,
            setOf(1L, 2L, 3L, 4L, 5L),
        )

        assertEquals(listOf(1L, 5L), roots)
    }

    @Test
    fun submissionNeverUsesPartiallySelectedFolder() {
        val roots = ShareTreeSelection.minimalSelectedRootIds(
            items,
            setOf(1L, 2L, 4L),
        )

        assertEquals(listOf(2L, 4L), roots)
    }

    private fun node(id: Long, parentId: Long?, type: StorageNodeType) = StorageNode(
        id = id,
        parentId = parentId,
        name = "node-$id",
        type = type,
        size = if (type == StorageNodeType.FILE) 1L else 0L,
        extension = null,
        mimeType = null,
        updatedAt = "2026-08-09T00:00:00",
        deletedAt = null,
    )
}
