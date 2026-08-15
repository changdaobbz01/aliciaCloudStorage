package com.alicia.cloudstorage.phone

import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDetailGalleryTest {
    @Test
    fun `normalizes file gallery around current node`() {
        val current = node(id = 2, name = "b.jpg")
        val nodes = listOf(
            node(id = 1, name = "a.jpg"),
            node(id = 2, name = "old-b.jpg"),
            node(id = 3, name = "folder", type = StorageNodeType.FOLDER),
            node(id = 1, name = "duplicate-a.jpg"),
        )

        val gallery = normalizeFileDetailGalleryNodes(current, nodes)

        assertEquals(listOf(1L, 2L), gallery.map { it.id })
        assertEquals("duplicate-a.jpg", gallery[0].name)
        assertEquals("b.jpg", gallery[1].name)
    }

    @Test
    fun `keeps current file when siblings response omits it`() {
        val current = node(id = 42, name = "current.mp4")
        val gallery = normalizeFileDetailGalleryNodes(
            currentNode = current,
            nodes = listOf(node(id = 7, name = "other.mp4")),
        )

        assertEquals(listOf(42L, 7L), gallery.map { it.id })
    }

    @Test
    fun `caps detail gallery size`() {
        val current = node(id = 200, name = "current.mp4")
        val many = (1L..100L).map { node(id = it, name = "$it.mp4") }

        val gallery = normalizeFileDetailGalleryNodes(current, many)

        assertEquals(MAX_FILE_DETAIL_GALLERY_NODES, gallery.size)
        assertEquals(current.id, gallery.first().id)
        assertTrue(gallery.none { it.id == 100L })
    }

    private fun node(
        id: Long,
        name: String,
        type: StorageNodeType = StorageNodeType.FILE,
    ): StorageNode = StorageNode(
        id = id,
        parentId = 10,
        name = name,
        type = type,
        size = 1,
        extension = name.substringAfterLast('.', missingDelimiterValue = ""),
        mimeType = null,
        updatedAt = "2026-08-15T00:00:00Z",
        deletedAt = null,
    )
}
