package com.alicia.cloudstorage.phone.data

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

class AliciaCloudServiceRoutesTest {
    @Test
    fun `routes identity writes to identity api`() {
        assertEquals("api/identity/auth/login", postPath("login"))
        assertEquals("api/identity/auth/register/email-code", postPath("requestEmailRegistrationCode"))
        assertEquals("api/identity/auth/register/verify", postPath("verifyEmailRegistration"))
        assertEquals("api/identity/auth/profile", putPath("updateProfile"))
        assertEquals("api/identity/auth/password", putPath("changePassword"))
        assertEquals("api/identity/admin/users/{userId}/password", putPath("resetUserPassword"))
    }

    @Test
    fun `keeps cloud aggregate routes on cloud storage api`() {
        assertEquals("api/auth/me", getPath("fetchCurrentUser"))
        assertEquals("api/auth/avatar", postPath("uploadAvatar"))
        assertEquals("api/admin/users", getPath("fetchUsers"))
        assertEquals("api/admin/users", postPath("createUser"))
        assertEquals("api/admin/cloud-users/{userId}/quota", putPath("updateUserQuota"))
    }

    private fun postPath(methodName: String): String =
        method(methodName).getAnnotation(POST::class.java)?.value
            ?: error("Missing @POST on $methodName")

    private fun getPath(methodName: String): String =
        method(methodName).getAnnotation(GET::class.java)?.value
            ?: error("Missing @GET on $methodName")

    private fun putPath(methodName: String): String =
        method(methodName).getAnnotation(PUT::class.java)?.value
            ?: error("Missing @PUT on $methodName")

    private fun method(methodName: String) =
        AliciaCloudService::class.java.declaredMethods.single { it.name == methodName }
}
