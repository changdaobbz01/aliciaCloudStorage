package com.alicia.cloudstorage.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigTest {
    @Test
    fun `legacy service addresses migrate to official base url`() {
        assertEquals(MAINLAND_BASE_URL, migrateSavedBaseUrl("http://43.132.237.15", MAINLAND_BASE_URL))
        assertEquals(MAINLAND_BASE_URL, migrateSavedBaseUrl("https://43.132.237.15", MAINLAND_BASE_URL))
        assertEquals(MAINLAND_BASE_URL, migrateSavedBaseUrl("http://windwindwind-alicia.cn", MAINLAND_BASE_URL))
    }

    @Test
    fun `official base url is described as formal service`() {
        assertEquals("正式服务", describeAccessEnvironment(MAINLAND_BASE_URL))
    }
}
