package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageFileCategory
import com.alicia.cloudstorage.phone.data.StorageNodeFilter

internal data class FileQueryIdentity(
    val folderId: Long?,
    val keyword: String,
    val searchScope: FileSearchScope,
    val filter: StorageNodeFilter,
    val category: StorageFileCategory?,
)

internal data class TrashQueryIdentity(
    val keyword: String,
    val filter: StorageNodeFilter,
)

internal fun ExplorerUiState.fileQueryIdentity(): FileQueryIdentity =
    FileQueryIdentity(
        folderId = currentFolderId,
        keyword = submittedKeyword,
        searchScope = searchScope,
        filter = filter,
        category = category,
    )

internal fun ExplorerUiState.trashQueryIdentity(): TrashQueryIdentity =
    TrashQueryIdentity(
        keyword = submittedKeyword,
        filter = filter,
    )
