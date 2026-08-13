package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageFileCategory
import com.alicia.cloudstorage.phone.data.StorageNodeFilter

internal data class FileFilterSelection(
    val category: StorageFileCategory?,
    val nodeFilter: StorageNodeFilter,
) {
    fun normalized(trashMode: Boolean): FileFilterSelection = when {
        trashMode -> copy(category = null)
        category != null -> copy(nodeFilter = StorageNodeFilter.FILE)
        else -> this
    }

    fun isActive(trashMode: Boolean): Boolean {
        val normalized = normalized(trashMode)
        return normalized.category != null || normalized.nodeFilter != StorageNodeFilter.ALL
    }
}

internal fun nextFileFilterSearchScope(
    currentScope: FileSearchScope,
    currentCategory: StorageFileCategory?,
    nextCategory: StorageFileCategory?,
): FileSearchScope = when {
    nextCategory != null -> FileSearchScope.GLOBAL
    currentCategory != nextCategory -> FileSearchScope.CURRENT_FOLDER
    else -> currentScope
}
