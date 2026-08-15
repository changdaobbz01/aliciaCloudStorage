package com.alicia.cloudstorage.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliciaShareLinksTest {
    @Test
    fun `builds production and local share links`() {
        assertEquals(
            "https://windwindwind-alicia.cn/share/SHARE_123",
            AliciaShareLinks.build("https://windwindwind-alicia.cn/", "SHARE_123"),
        )
        assertEquals(
            "http://127.0.0.1:8090/share/local-code",
            AliciaShareLinks.build("http://127.0.0.1:8090", "local-code"),
        )
    }

    @Test
    fun `rejects unsafe bases and malformed share codes`() {
        assertNull(AliciaShareLinks.build("javascript:alert(1)", "SHARE123"))
        assertNull(AliciaShareLinks.build("https://user@example.com", "SHARE123"))
        assertNull(AliciaShareLinks.build("https://example.com?next=evil", "SHARE123"))
        assertNull(AliciaShareLinks.build("https://example.com", "bad/code"))
    }
}
