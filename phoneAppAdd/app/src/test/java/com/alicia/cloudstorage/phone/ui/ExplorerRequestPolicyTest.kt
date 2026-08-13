package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageFileCategory
import com.alicia.cloudstorage.phone.data.StorageNodeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExplorerRequestPolicyTest {
    @Test
    fun `file identity changes for every server query dimension`() {
        val initial = ExplorerUiState(currentFolderId = 7L).fileQueryIdentity()

        assertNotEquals(initial, ExplorerUiState(currentFolderId = 8L).fileQueryIdentity())
        assertNotEquals(initial, ExplorerUiState(currentFolderId = 7L, submittedKeyword = "报告").fileQueryIdentity())
        assertNotEquals(initial, ExplorerUiState(currentFolderId = 7L, filter = StorageNodeFilter.FILE).fileQueryIdentity())
        assertNotEquals(
            initial,
            ExplorerUiState(
                currentFolderId = 7L,
                searchScope = FileSearchScope.GLOBAL,
                category = StorageFileCategory.DOCUMENT,
            ).fileQueryIdentity(),
        )
    }

    @Test
    fun `presentation-only state does not invalidate a query`() {
        val initial = ExplorerUiState(currentFolderId = 7L).fileQueryIdentity()
        val withSelection = ExplorerUiState(
            currentFolderId = 7L,
            keyword = "尚未提交",
            selectedNodeIds = setOf(11L),
            loading = true,
        ).fileQueryIdentity()

        assertEquals(initial, withSelection)
    }

    @Test
    fun `trash identity excludes file-only dimensions`() {
        val initial = ExplorerUiState(submittedKeyword = "旧文件", filter = StorageNodeFilter.FOLDER).trashQueryIdentity()
        val changedFolder = ExplorerUiState(
            submittedKeyword = "旧文件",
            filter = StorageNodeFilter.FOLDER,
            currentFolderId = 9L,
            category = StorageFileCategory.IMAGE,
        ).trashQueryIdentity()

        assertEquals(initial, changedFolder)
    }
}
