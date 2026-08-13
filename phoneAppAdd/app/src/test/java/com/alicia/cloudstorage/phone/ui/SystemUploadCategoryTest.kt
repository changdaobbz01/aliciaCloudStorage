package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemUploadCategoryTest {
    @Test
    fun `system categories stay type scoped`() {
        assertTrue("image/*" in SystemUploadCategory.MEDIA.mimeTypes)
        assertTrue("application/pdf" in SystemUploadCategory.DOCUMENTS.mimeTypes)
        assertTrue("application/vnd.rar" in SystemUploadCategory.ARCHIVES.mimeTypes)
        assertTrue("audio/*" in SystemUploadCategory.AUDIO.mimeTypes)
        assertTrue("*/*" in SystemUploadCategory.OTHER.mimeTypes)
    }

    @Test
    fun `archive category does not expose every binary file`() {
        assertFalse("application/octet-stream" in SystemUploadCategory.ARCHIVES.mimeTypes)
    }
}
