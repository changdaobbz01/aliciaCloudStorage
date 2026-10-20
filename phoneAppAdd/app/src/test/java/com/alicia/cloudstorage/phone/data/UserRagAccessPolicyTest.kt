package com.alicia.cloudstorage.phone.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRagAccessPolicyTest {
    @Test
    fun `explicit rag admin role enables cloud execution canary`() {
        assertTrue(user(appRoles = mapOf("rag" to " RAG_ADMIN ")).isRagAdmin)
    }

    @Test
    fun `global or cloud admin does not substitute for rag admin role`() {
        assertFalse(user(role = UserRole.ADMIN).isRagAdmin)
        assertFalse(user(appRoles = mapOf("cloud" to "CLOUD_ADMIN")).isRagAdmin)
    }

    @Test
    fun `rag user and missing role remain outside the canary`() {
        assertFalse(user(appRoles = mapOf("rag" to "RAG_USER")).isRagAdmin)
        assertFalse(user().isRagAdmin)
    }

    private fun user(
        role: UserRole = UserRole.USER,
        appRoles: Map<String, String> = emptyMap(),
    ) = User(
        id = 1L,
        phoneNumber = "13800000000",
        email = null,
        nickname = "tester",
        avatarUrl = null,
        homeBackgroundUrl = null,
        role = role,
        status = UserStatus.ACTIVE,
        createdAt = "2026-10-20T00:00:00Z",
        storageQuotaBytes = 1024L,
        usedBytes = 0L,
        remainingBytes = 1024L,
        appRoles = appRoles,
    )
}
