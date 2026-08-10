package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import java.text.Collator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

internal enum class FileSortCriterion(val label: String) {
    ORIGINAL("综合"),
    NAME("名称"),
    UPDATED_AT("日期"),
    SIZE("大小"),
}

internal enum class FileLocalSortMode(
    val toolbarLabel: String,
    val criterion: FileSortCriterion,
    val descending: Boolean,
) {
    ORIGINAL("综合排序", FileSortCriterion.ORIGINAL, false),
    NAME_ASC("名称升序", FileSortCriterion.NAME, false),
    NAME_DESC("名称降序", FileSortCriterion.NAME, true),
    UPDATED_DESC("日期降序", FileSortCriterion.UPDATED_AT, true),
    UPDATED_ASC("日期升序", FileSortCriterion.UPDATED_AT, false),
    SIZE_DESC("大小降序", FileSortCriterion.SIZE, true),
    SIZE_ASC("大小升序", FileSortCriterion.SIZE, false),
}

internal fun defaultFileSortMode(criterion: FileSortCriterion): FileLocalSortMode =
    when (criterion) {
        FileSortCriterion.ORIGINAL -> FileLocalSortMode.ORIGINAL
        FileSortCriterion.NAME -> FileLocalSortMode.NAME_ASC
        FileSortCriterion.UPDATED_AT -> FileLocalSortMode.UPDATED_DESC
        FileSortCriterion.SIZE -> FileLocalSortMode.SIZE_DESC
    }

internal fun fileSortDirectionOptions(criterion: FileSortCriterion): List<Pair<FileLocalSortMode, String>> =
    when (criterion) {
        FileSortCriterion.ORIGINAL -> emptyList()
        FileSortCriterion.NAME -> listOf(
            FileLocalSortMode.NAME_ASC to "A-Z / 中文升序",
            FileLocalSortMode.NAME_DESC to "Z-A / 中文降序",
        )
        FileSortCriterion.UPDATED_AT -> listOf(
            FileLocalSortMode.UPDATED_DESC to "最新在前",
            FileLocalSortMode.UPDATED_ASC to "最早在前",
        )
        FileSortCriterion.SIZE -> listOf(
            FileLocalSortMode.SIZE_DESC to "大文件在前",
            FileLocalSortMode.SIZE_ASC to "小文件在前",
        )
    }

internal fun sortStorageNodesLocally(
    nodes: List<StorageNode>,
    mode: FileLocalSortMode,
): List<StorageNode> {
    if (mode == FileLocalSortMode.ORIGINAL || nodes.size < 2) return nodes

    val nameCollator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }
    val folderFirst = Comparator<StorageNode> { left, right ->
        compareValues(left.type != StorageNodeType.FOLDER, right.type != StorageNodeType.FOLDER)
    }
    val nameAscending = Comparator<StorageNode> { left, right ->
        nameCollator.compare(left.name, right.name)
    }
    val valueComparator = when (mode.criterion) {
        FileSortCriterion.NAME -> if (mode.descending) nameAscending.reversed() else nameAscending
        FileSortCriterion.UPDATED_AT -> Comparator { left, right ->
            compareNullable(
                left = parseNodeUpdatedAt(left.updatedAt),
                right = parseNodeUpdatedAt(right.updatedAt),
                descending = mode.descending,
            )
        }
        FileSortCriterion.SIZE -> Comparator { left, right ->
            if (mode.descending) {
                compareValues(right.size, left.size)
            } else {
                compareValues(left.size, right.size)
            }
        }
        FileSortCriterion.ORIGINAL -> Comparator { _, _ -> 0 }
    }

    return nodes.sortedWith(
        folderFirst
            .then(valueComparator)
            .then(nameAscending)
            .thenBy { it.id },
    )
}

private fun parseNodeUpdatedAt(value: String): Long? =
    runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(value)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.recoverCatching {
        LocalDate.parse(value.take(10))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

private fun <T : Comparable<T>> compareNullable(
    left: T?,
    right: T?,
    descending: Boolean,
): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    descending -> right.compareTo(left)
    else -> left.compareTo(right)
}
