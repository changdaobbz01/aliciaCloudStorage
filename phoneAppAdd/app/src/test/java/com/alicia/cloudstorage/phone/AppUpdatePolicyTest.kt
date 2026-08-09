package com.alicia.cloudstorage.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun resolvesRelativeDownloadUrlAgainstApiBase() {
        assertEquals(
            "https://cloud.example.com/api/app/latest.apk",
            AppUpdatePolicy.resolveDownloadUrl(
                baseUrl = "https://cloud.example.com/api",
                downloadUrl = "app/latest.apk",
            ),
        )
    }

    @Test
    fun acceptsAbsoluteHttpsDownloadUrl() {
        assertEquals(
            "https://downloads.example.com/alicia.apk",
            AppUpdatePolicy.resolveDownloadUrl(
                baseUrl = "https://cloud.example.com/api",
                downloadUrl = "https://downloads.example.com/alicia.apk",
            ),
        )
    }

    @Test
    fun rejectsBlankDownloadUrl() {
        assertNull(AppUpdatePolicy.resolveDownloadUrl("https://cloud.example.com", "  "))
    }

    @Test
    fun rejectsNonNetworkScheme() {
        assertNull(
            AppUpdatePolicy.resolveDownloadUrl(
                baseUrl = "https://cloud.example.com",
                downloadUrl = "file:///sdcard/alicia.apk",
            ),
        )
    }
}
