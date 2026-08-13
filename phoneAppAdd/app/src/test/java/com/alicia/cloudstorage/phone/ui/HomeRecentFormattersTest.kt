package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRecentFormattersTest {
    private val now = Instant.parse("2026-08-11T00:00:00Z")

    @Test
    fun `uses relative minutes and hours within one day`() {
        assertEquals("1分钟前", formatRelativeOrDateTime("2026-08-10T23:59:00Z", now, ZoneOffset.UTC))
        assertEquals("3小时前", formatRelativeOrDateTime("2026-08-10T21:00:00Z", now, ZoneOffset.UTC))
    }

    @Test
    fun `uses absolute time at one day`() {
        assertEquals(
            "2026-08-10 00:00",
            formatRelativeOrDateTime("2026-08-10T00:00:00Z", now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `treats legacy offsetless timestamps as utc`() {
        assertEquals(
            "1分钟前",
            formatRelativeOrDateTime("2026-08-10T23:59:00", now, ZoneOffset.ofHours(8)),
        )
    }

    @Test
    fun `converts explicit api timestamps to the device zone`() {
        assertEquals(
            "2026-08-11 07:00",
            formatDateTime("2026-08-10T23:00:00Z", ZoneOffset.ofHours(8)),
        )
        assertEquals(
            "08-11",
            formatMonthDay("2026-08-10T23:00:00Z", ZoneOffset.ofHours(8)),
        )
    }

    @Test
    fun `shows user facing file types`() {
        assertEquals("图片", formatNodeTypeLabel(node("jpg", "image/jpeg")))
        assertEquals("PDF", formatNodeTypeLabel(node("pdf", "application/pdf")))
        assertEquals("压缩包", formatNodeTypeLabel(node("zip", "application/zip")))
    }

    private fun node(extension: String, mimeType: String) = StorageNode(
        id = 1L,
        parentId = null,
        name = "sample.$extension",
        type = StorageNodeType.FILE,
        size = 1L,
        extension = extension,
        mimeType = mimeType,
        updatedAt = "2026-08-10T23:59:00Z",
        deletedAt = null,
    )
}
