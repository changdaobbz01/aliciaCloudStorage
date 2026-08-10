package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FileLocalSortTest {
    private val folder = node(1, "资料", StorageNodeType.FOLDER, size = 0, updatedAt = "2026-08-01T08:00:00")
    private val smallOld = node(2, "alpha.txt", StorageNodeType.FILE, size = 10, updatedAt = "2026-08-02T08:00:00")
    private val largeNew = node(3, "Beta.txt", StorageNodeType.FILE, size = 100, updatedAt = "2026-08-09T08:00:00")

    @Test
    fun originalModePreservesRepositoryOrderAndInstance() {
        val nodes = listOf(largeNew, folder, smallOld)

        val result = sortStorageNodesLocally(nodes, FileLocalSortMode.ORIGINAL)

        assertSame(nodes, result)
    }

    @Test
    fun nameSortKeepsFoldersFirstAndIgnoresCase() {
        val result = sortStorageNodesLocally(
            listOf(largeNew, smallOld, folder),
            FileLocalSortMode.NAME_ASC,
        )

        assertEquals(listOf(folder.id, smallOld.id, largeNew.id), result.map { it.id })
    }

    @Test
    fun sizeSortKeepsFoldersFirstAndSortsFilesDescending() {
        val result = sortStorageNodesLocally(
            listOf(smallOld, largeNew, folder),
            FileLocalSortMode.SIZE_DESC,
        )

        assertEquals(listOf(folder.id, largeNew.id, smallOld.id), result.map { it.id })
    }

    @Test
    fun dateSortSupportsBothDirections() {
        val descending = sortStorageNodesLocally(
            listOf(smallOld, largeNew, folder),
            FileLocalSortMode.UPDATED_DESC,
        )
        val ascending = sortStorageNodesLocally(
            listOf(smallOld, largeNew, folder),
            FileLocalSortMode.UPDATED_ASC,
        )

        assertEquals(listOf(folder.id, largeNew.id, smallOld.id), descending.map { it.id })
        assertEquals(listOf(folder.id, smallOld.id, largeNew.id), ascending.map { it.id })
    }

    private fun node(
        id: Long,
        name: String,
        type: StorageNodeType,
        size: Long,
        updatedAt: String,
    ) = StorageNode(
        id = id,
        parentId = null,
        name = name,
        type = type,
        size = size,
        extension = null,
        mimeType = null,
        updatedAt = updatedAt,
        deletedAt = null,
    )
}
