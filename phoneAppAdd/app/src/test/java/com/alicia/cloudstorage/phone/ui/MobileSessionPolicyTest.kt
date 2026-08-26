package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileSessionPolicyTest {
    @Test
    fun `401 responses are treated as expired sessions`() {
        val error = ApiException("token invalid", 401)

        assertTrue(error.isMobileAuthExpired())
        assertEquals(MOBILE_SESSION_EXPIRED_MESSAGE, error.mobileReadableMessage())
    }

    @Test
    fun `non auth errors keep backend message`() {
        val error = ApiException("文件不存在。", 404)

        assertFalse(error.isMobileAuthExpired())
        assertEquals("文件不存在。", error.mobileReadableMessage())
    }

    @Test
    fun `blank errors use generic fallback`() {
        val error = RuntimeException("")

        assertFalse(error.isMobileAuthExpired())
        assertEquals("请求失败，请稍后再试。", error.mobileReadableMessage())
    }
}
