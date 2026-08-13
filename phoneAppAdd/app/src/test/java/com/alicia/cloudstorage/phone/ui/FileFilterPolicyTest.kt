package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageFileCategory
import com.alicia.cloudstorage.phone.data.StorageNodeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFilterPolicyTest {
    @Test
    fun `category selection always resolves to files`() {
        val selection = FileFilterSelection(StorageFileCategory.IMAGE, StorageNodeFilter.FOLDER)

        assertEquals(
            FileFilterSelection(StorageFileCategory.IMAGE, StorageNodeFilter.FILE),
            selection.normalized(trashMode = false),
        )
    }

    @Test
    fun `active state uses committed filter values`() {
        assertFalse(FileFilterSelection(null, StorageNodeFilter.ALL).isActive(trashMode = false))
        assertTrue(FileFilterSelection(null, StorageNodeFilter.FOLDER).isActive(trashMode = false))
        assertTrue(FileFilterSelection(StorageFileCategory.VIDEO, StorageNodeFilter.FILE).isActive(trashMode = false))
    }

    @Test
    fun `trash ignores file category`() {
        val selection = FileFilterSelection(StorageFileCategory.IMAGE, StorageNodeFilter.ALL)

        assertEquals(FileFilterSelection(null, StorageNodeFilter.ALL), selection.normalized(trashMode = true))
        assertFalse(selection.isActive(trashMode = true))
    }

    @Test
    fun `node type filter preserves an existing global search`() {
        assertEquals(
            FileSearchScope.GLOBAL,
            nextFileFilterSearchScope(
                currentScope = FileSearchScope.GLOBAL,
                currentCategory = null,
                nextCategory = null,
            ),
        )
    }

    @Test
    fun `category transitions use their intended scope`() {
        assertEquals(
            FileSearchScope.GLOBAL,
            nextFileFilterSearchScope(
                currentScope = FileSearchScope.CURRENT_FOLDER,
                currentCategory = null,
                nextCategory = StorageFileCategory.IMAGE,
            ),
        )
        assertEquals(
            FileSearchScope.CURRENT_FOLDER,
            nextFileFilterSearchScope(
                currentScope = FileSearchScope.GLOBAL,
                currentCategory = StorageFileCategory.IMAGE,
                nextCategory = null,
            ),
        )
    }
}
