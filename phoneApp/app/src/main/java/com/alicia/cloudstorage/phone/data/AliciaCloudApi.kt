package com.alicia.cloudstorage.phone.data

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface AliciaCloudService {
    @POST("api/auth/login")
    suspend fun login(
        @Body payload: LoginPayload,
    ): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun fetchCurrentUser(
        @Header("Authorization") authorization: String,
    ): Response<User>

    @Multipart
    @POST("api/auth/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
    ): Response<User>

    @PUT("api/auth/password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body payload: ChangePasswordPayload,
    ): Response<ApiMessageResponse>

    @GET("api/storage/overview")
    suspend fun fetchDriveOverview(
        @Header("Authorization") authorization: String,
    ): Response<DriveOverview>

    @GET("api/storage/usage-history")
    suspend fun fetchUsageHistory(
        @Header("Authorization") authorization: String,
        @Query("days") days: Int,
    ): Response<List<UsageHistoryPoint>>

    @GET("api/storage/nodes")
    suspend fun fetchStorageNodes(
        @Header("Authorization") authorization: String,
        @Query("parentId") parentId: Long?,
        @Query("keyword") keyword: String?,
        @Query("type") type: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sortBy") sortBy: String,
        @Query("sortDirection") sortDirection: String,
    ): Response<StorageNodePage>

    @GET("api/storage/trash")
    suspend fun fetchTrashNodes(
        @Header("Authorization") authorization: String,
        @Query("keyword") keyword: String?,
        @Query("type") type: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sortBy") sortBy: String,
        @Query("sortDirection") sortDirection: String,
    ): Response<StorageNodePage>

    @GET("api/admin/users")
    suspend fun fetchUsers(
        @Header("Authorization") authorization: String,
    ): Response<List<User>>

    @POST("api/admin/users")
    suspend fun createUser(
        @Header("Authorization") authorization: String,
        @Body payload: CreateUserPayload,
    ): Response<User>

    @PUT("api/admin/users/{userId}/quota")
    suspend fun updateUserQuota(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
        @Body payload: UpdateUserStorageQuotaPayload,
    ): Response<User>

    @PUT("api/admin/users/{userId}/password")
    suspend fun resetUserPassword(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
        @Body payload: ResetUserPasswordPayload,
    ): Response<ApiMessageResponse>

    @POST("api/storage/folders")
    suspend fun createFolder(
        @Header("Authorization") authorization: String,
        @Body payload: CreateFolderPayload,
    ): Response<StorageNode>

    @Multipart
    @POST("api/storage/files")
    suspend fun uploadFile(
        @Header("Authorization") authorization: String,
        @Part("parentId") parentId: RequestBody?,
        @Part file: MultipartBody.Part,
    ): Response<StorageNode>

    @DELETE("api/storage/nodes/{nodeId}")
    suspend fun moveNodeToTrash(
        @Header("Authorization") authorization: String,
        @Path("nodeId") nodeId: Long,
    ): Response<ApiMessageResponse>

    @POST("api/storage/trash/{nodeId}/restore")
    suspend fun restoreNode(
        @Header("Authorization") authorization: String,
        @Path("nodeId") nodeId: Long,
    ): Response<StorageNode>

    @DELETE("api/storage/trash/{nodeId}")
    suspend fun permanentlyDeleteNode(
        @Header("Authorization") authorization: String,
        @Path("nodeId") nodeId: Long,
    ): Response<ApiMessageResponse>

    @Streaming
    @GET("api/storage/files/{fileId}/download")
    suspend fun downloadFile(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: Long,
    ): Response<ResponseBody>
}

class AliciaCloudServiceFactory {
    private val serviceCache = ConcurrentHashMap<String, AliciaCloudService>()

    fun serviceFor(baseUrl: String): AliciaCloudService =
        serviceCache.getOrPut(normalizedBaseUrl(baseUrl)) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(baseUrl))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AliciaCloudService::class.java)
        }

    private fun normalizedBaseUrl(baseUrl: String) = baseUrl.trim().removeSuffix("/")

    private fun ensureTrailingSlash(baseUrl: String) = "${normalizedBaseUrl(baseUrl)}/"
}
