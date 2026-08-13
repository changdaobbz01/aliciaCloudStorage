package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPickerPolicyTest {
    @Test
    fun `selection mode does not leak into upload model`() {
        assertTrue(LocalPickerMode.FOLDERS.allowsSelection(LocalUploadSelectionKind.FOLDER))
        assertFalse(LocalPickerMode.FOLDERS.allowsSelection(LocalUploadSelectionKind.FILE))
        assertTrue(LocalPickerMode.FILES_AND_FOLDERS.allowsSelection(LocalUploadSelectionKind.FILE))
        assertTrue(LocalPickerMode.FILES_AND_FOLDERS.allowsSelection(LocalUploadSelectionKind.FOLDER))
    }

    @Test
    fun `preview kind uses mime first and extension fallback`() {
        assertEquals(LocalMediaPreviewKind.IMAGE, localMediaPreviewKind("asset", "image/webp"))
        assertEquals(LocalMediaPreviewKind.VIDEO, localMediaPreviewKind("movie.mkv", null))
        assertEquals(LocalMediaPreviewKind.VIDEO, localMediaPreviewKind("wrong.jpg", "video/mp4"))
        assertEquals(null, localMediaPreviewKind("readme.txt", "text/plain"))
    }
}
