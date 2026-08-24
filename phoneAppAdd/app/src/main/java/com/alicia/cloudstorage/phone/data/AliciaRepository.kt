package com.alicia.cloudstorage.phone.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import kotlin.math.max
import kotlin.math.roundToInt

class ApiException(
    override val message: String,
    val status: Int,
    cause: Throwable? = null,
) : IOException(message, cause)

private const val MAX_DIRECT_UPLOAD_BYTES = 20L * 1024 * 1024
private const val MAX_AVATAR_UPLOAD_BYTES = 2L * 1024 * 1024
private const val MAX_AVATAR_DIMENSION = 1440
private const val TRANSFER_BUFFER_SIZE = 64 * 1024

data class TransferProgress(
    val transferredBytes: Long,
    val totalBytes: Long?,
)

data class UploadAssetDescriptor(
    val fileName: String,
    val contentType: String?,
    val sizeBytes: Long?,
)

class AliciaRepository(
    private val serviceFactory: AliciaCloudServiceFactory = AliciaCloudServiceFactory(),
) {
    private val directDownloadClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun login(baseUrl: String, identifier: String, password: String): LoginResponse {
        val identitySession = serviceFactory.serviceFor(baseUrl)
            .login(LoginPayload(identifier = identifier, password = password))
            .requireBody(fallback = "登录失败，请检查账号和密码。")

        return identitySession.toCloudLoginResponse(baseUrl)
    }

    suspend fun requestEmailRegistrationCode(baseUrl: String, email: String): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .requestEmailRegistrationCode(RequestEmailRegistrationCodePayload(email = email))
            .requireBody(fallback = "验证码发送失败，请稍后再试。")

    suspend fun verifyEmailRegistration(
        baseUrl: String,
        email: String,
        code: String,
        nickname: String,
        password: String,
    ): LoginResponse {
        val identitySession = serviceFactory.serviceFor(baseUrl)
            .verifyEmailRegistration(
                VerifyEmailRegistrationPayload(
                    email = email,
                    code = code,
                    nickname = nickname,
                    password = password,
                ),
            )
            .requireBody(fallback = "注册失败，请检查验证码后再试。")

        return identitySession.toCloudLoginResponse(baseUrl)
    }

    suspend fun refreshToken(baseUrl: String, token: String, refreshToken: String): LoginResponse {
        if (refreshToken.isBlank()) {
            throw ApiException("刷新令牌不能为空，请重新登录。", 401)
        }

        val identitySession = serviceFactory.serviceFor(baseUrl)
            .refreshToken(authorization(token), RefreshTokenPayload(refreshToken))
            .requireBody(fallback = "刷新登录状态失败，请重新登录。")

        return identitySession.toCloudLoginResponse(baseUrl)
    }

    suspend fun logout(baseUrl: String, token: String, refreshToken: String?): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .logout(authorization(token), LogoutPayload(refreshToken))
            .requireBody(fallback = "退出登录失败。")

    suspend fun fetchCurrentUser(baseUrl: String, token: String): User =
        serviceFactory.serviceFor(baseUrl)
            .fetchCurrentUser(authorization(token))
            .requireBody(fallback = "获取当前账号信息失败。")

    suspend fun updateProfile(
        baseUrl: String,
        token: String,
        phoneNumber: String,
        nickname: String,
        avatarUrl: String?,
    ): User {
        serviceFactory.serviceFor(baseUrl)
            .updateProfile(
                authorization = authorization(token),
                payload = UpdateProfilePayload(
                    phoneNumber = phoneNumber,
                    nickname = nickname,
                    avatarUrl = avatarUrl,
                ),
            )
            .requireBody(fallback = "更新个人资料失败。")

        return fetchCurrentUser(baseUrl, token)
    }

    suspend fun uploadCurrentUserAvatar(
        context: Context,
        baseUrl: String,
        token: String,
        uri: Uri,
    ): User {
        val preparedFile = context.contentResolver.prepareAvatarUploadFile(uri, context.cacheDir)
        try {
            preparedFile.file.inputStream().use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw ApiException("无法读取你选择的头像文件。", 400)

            val requestBody = preparedFile.file.asRequestBody(
                preparedFile.contentType.toMediaTypeOrNull(),
            )
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = preparedFile.fileName,
                body = requestBody,
            )

            return serviceFactory.serviceFor(baseUrl)
                .uploadAvatar(
                    authorization = authorization(token),
                    file = filePart,
                )
                .requireBody(fallback = "更新头像失败。")
        } finally {
            preparedFile.file.delete()
        }
    }

    suspend fun changePassword(
        baseUrl: String,
        token: String,
        oldPassword: String,
        newPassword: String,
    ): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .changePassword(
                authorization = authorization(token),
                payload = ChangePasswordPayload(
                    oldPassword = oldPassword,
                    newPassword = newPassword,
                ),
            )
            .requireBody(fallback = "修改密码失败。")

    suspend fun fetchLatestAppVersion(baseUrl: String): AppPackageVersionInfo =
        serviceFactory.serviceFor(baseUrl)
            .fetchLatestAppVersion()
            .requireBody(fallback = "获取 APP 更新信息失败。")

    suspend fun fetchDriveOverview(baseUrl: String, token: String): DriveOverview =
        serviceFactory.serviceFor(baseUrl)
            .fetchDriveOverview(authorization(token))
            .requireBody(fallback = "加载首页概览失败。")

    suspend fun fetchUsageHistory(baseUrl: String, token: String, days: Int = 30): List<UsageHistoryPoint> =
        serviceFactory.serviceFor(baseUrl)
            .fetchUsageHistory(authorization(token), days)
            .requireBody(fallback = "加载空间趋势失败。")

    suspend fun fetchStorageNodes(
        baseUrl: String,
        token: String,
        parentId: Long?,
        keyword: String,
        filter: StorageNodeFilter,
        page: Int = 1,
        size: Int = 100,
        sortBy: String = "name",
        sortDirection: String = "asc",
        recursive: Boolean = false,
        category: StorageFileCategory? = null,
    ): StorageNodePage =
        serviceFactory.serviceFor(baseUrl)
            .fetchStorageNodes(
                authorization = authorization(token),
                parentId = parentId,
                recursive = recursive,
                keyword = keyword.trim().takeIf { it.isNotEmpty() },
                type = filter.takeUnless { it == StorageNodeFilter.ALL }?.name,
                category = category?.name,
                page = page,
                size = size,
                sortBy = sortBy,
                sortDirection = sortDirection,
            )
            .requireBody(fallback = "加载文件列表失败。")

    suspend fun fetchTrashNodes(
        baseUrl: String,
        token: String,
        keyword: String,
        filter: StorageNodeFilter,
    ): StorageNodePage =
        serviceFactory.serviceFor(baseUrl)
            .fetchTrashNodes(
                authorization = authorization(token),
                keyword = keyword.trim().takeIf { it.isNotEmpty() },
                type = filter.takeUnless { it == StorageNodeFilter.ALL }?.name,
                page = 1,
                size = 100,
                sortBy = "deletedAt",
                sortDirection = "desc",
            )
            .requireBody(fallback = "加载回收站失败。")

    suspend fun fetchFolders(baseUrl: String, token: String): List<StorageNode> =
        serviceFactory.serviceFor(baseUrl)
            .fetchFolders(authorization(token))
            .requireBody(fallback = "加载文件夹目录失败。")

    suspend fun fetchUsers(baseUrl: String, token: String): List<User> =
        serviceFactory.serviceFor(baseUrl)
            .fetchUsers(authorization(token))
            .requireBody(fallback = "加载账号列表失败。")

    suspend fun createUser(
        baseUrl: String,
        token: String,
        phoneNumber: String,
        nickname: String,
        password: String,
        role: UserRole,
        storageQuotaBytes: Long?,
    ): User =
        serviceFactory.serviceFor(baseUrl)
            .createUser(
                authorization = authorization(token),
                payload = CreateUserPayload(
                    phoneNumber = phoneNumber,
                    nickname = nickname,
                    avatarUrl = null,
                    password = password,
                    role = role,
                    storageQuotaBytes = storageQuotaBytes,
                ),
            )
            .requireBody(fallback = "新增账号失败。")

    suspend fun updateUserQuota(
        baseUrl: String,
        token: String,
        userId: Long,
        storageQuotaBytes: Long,
    ): User =
        serviceFactory.serviceFor(baseUrl)
            .updateUserQuota(
                authorization = authorization(token),
                userId = userId,
                payload = UpdateUserStorageQuotaPayload(
                    storageQuotaBytes = storageQuotaBytes,
                ),
            )
            .requireBody(fallback = "修改用户额度失败。")

    suspend fun resetUserPassword(
        baseUrl: String,
        token: String,
        userId: Long,
        newPassword: String,
    ): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .resetUserPassword(
                authorization = authorization(token),
                userId = userId,
                payload = ResetUserPasswordPayload(
                    newPassword = newPassword,
                ),
            )
            .requireBody(fallback = "重置用户密码失败。")

    private suspend fun IdentityLoginResponse.toCloudLoginResponse(baseUrl: String): LoginResponse =
        LoginResponse(
            token = token,
            refreshToken = refreshToken,
            user = fetchCurrentUser(baseUrl, token),
        )

    suspend fun createFolder(
        baseUrl: String,
        token: String,
        parentId: Long?,
        folderName: String,
    ): StorageNode =
        serviceFactory.serviceFor(baseUrl)
            .createFolder(
                authorization = authorization(token),
                payload = CreateFolderPayload(
                    parentId = parentId,
                    folderName = folderName,
                ),
            )
            .requireBody(fallback = "新建文件夹失败。")

    suspend fun uploadFile(
        context: Context,
        baseUrl: String,
        token: String,
        parentId: Long?,
        uri: Uri,
        onProgress: (TransferProgress) -> Unit = {},
    ): StorageNode {
        val asset = context.contentResolver.resolveOpenableAsset(uri)

        if ((asset.sizeBytes ?: 0L) > MAX_DIRECT_UPLOAD_BYTES) {
            throw ApiException(
                message = "Android 首版先支持 20 MB 以内直传，大文件分片上传下一轮继续补。",
                status = 400,
            )
        }

        val suffix = asset.fileName.substringAfterLast('.', "").ifBlank { "bin" }
        val tempFile = File.createTempFile("alicia-upload-", ".$suffix", context.cacheDir)

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw ApiException("无法读取你选择的文件。", 400)

            val resolvedSizeBytes = tempFile.length()
            if (resolvedSizeBytes > MAX_DIRECT_UPLOAD_BYTES) {
                throw ApiException(
                    message = "Android 首版先支持 20 MB 以内直传，大文件分片上传下一轮继续补。",
                    status = 400,
                )
            }

            val requestBody = ProgressRequestBody(
                file = tempFile,
                contentType = (asset.contentType ?: "application/octet-stream").toMediaTypeOrNull(),
                totalBytes = resolvedSizeBytes,
                onProgress = onProgress,
            )
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = asset.fileName,
                body = requestBody,
            )
            val parentIdPart = parentId
                ?.toString()
                ?.toRequestBody("text/plain".toMediaTypeOrNull())

            return serviceFactory.serviceFor(baseUrl)
                .uploadFile(
                    authorization = authorization(token),
                    parentId = parentIdPart,
                    file = filePart,
                )
                .requireBody(fallback = "上传文件失败。")
        } finally {
            tempFile.delete()
        }
    }

    fun describeUploadAsset(context: Context, uri: Uri): UploadAssetDescriptor {
        val asset = context.contentResolver.resolveOpenableAsset(uri)
        return UploadAssetDescriptor(
            fileName = asset.fileName,
            contentType = asset.contentType,
            sizeBytes = asset.sizeBytes,
        )
    }

    suspend fun renameNode(
        baseUrl: String,
        token: String,
        nodeId: Long,
        name: String,
    ): StorageNode =
        serviceFactory.serviceFor(baseUrl)
            .renameNode(
                authorization = authorization(token),
                nodeId = nodeId,
                payload = RenameNodePayload(name = name),
            )
            .requireBody(fallback = "重命名失败。")

    suspend fun renameNodes(
        baseUrl: String,
        token: String,
        items: List<BatchRenameNodeItemPayload>,
    ): List<StorageNode> =
        serviceFactory.serviceFor(baseUrl)
            .renameNodes(
                authorization = authorization(token),
                payload = BatchRenameNodePayload(items = items),
            )
            .requireBody(fallback = "批量重命名失败。")

    suspend fun moveNode(
        baseUrl: String,
        token: String,
        nodeId: Long,
        parentId: Long?,
    ): StorageNode =
        serviceFactory.serviceFor(baseUrl)
            .moveNode(
                authorization = authorization(token),
                nodeId = nodeId,
                payload = MoveNodePayload(parentId = parentId),
            )
            .requireBody(fallback = "移动失败。")

    suspend fun moveNodes(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        parentId: Long?,
    ): List<StorageNode> =
        serviceFactory.serviceFor(baseUrl)
            .moveNodes(
                authorization = authorization(token),
                payload = BatchMoveNodePayload(
                    nodeIds = nodeIds,
                    parentId = parentId,
                ),
            )
            .requireBody(fallback = "批量移动失败。")

    suspend fun moveNodeToTrash(
        baseUrl: String,
        token: String,
        nodeId: Long,
    ): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .moveNodeToTrash(
                authorization = authorization(token),
                nodeId = nodeId,
            )
            .requireBody(fallback = "删除到回收站失败。")

    suspend fun moveNodesToTrash(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
    ): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .moveNodesToTrash(
                authorization = authorization(token),
                payload = BatchNodePayload(nodeIds = nodeIds),
            )
            .requireBody(fallback = "批量移入回收站失败。")

    suspend fun moveScopedNodesToTrash(
        baseUrl: String,
        token: String,
        payload: ScopedTrashPayload,
    ): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .moveScopedNodesToTrash(
                authorization = authorization(token),
                payload = payload,
            )
            .requireBody(fallback = "目录内容已发生变化，请重新预览后再确认删除。")

    suspend fun restoreNode(
        baseUrl: String,
        token: String,
        nodeId: Long,
    ): StorageNode =
        serviceFactory.serviceFor(baseUrl)
            .restoreNode(
                authorization = authorization(token),
                nodeId = nodeId,
            )
            .requireBody(fallback = "恢复文件失败。")

    suspend fun restoreNodes(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
    ): List<StorageNode> =
        serviceFactory.serviceFor(baseUrl)
            .restoreNodes(
                authorization = authorization(token),
                payload = BatchNodePayload(nodeIds = nodeIds),
            )
            .requireBody(fallback = "批量恢复失败。")

    suspend fun permanentlyDeleteNode(
        baseUrl: String,
        token: String,
        nodeId: Long,
    ): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .permanentlyDeleteNode(
                authorization = authorization(token),
                nodeId = nodeId,
            )
            .requireBody(fallback = "彻底删除失败。")

    suspend fun permanentlyDeleteNodes(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
    ): ApiMessageResponse =
        serviceFactory.serviceFor(baseUrl)
            .permanentlyDeleteNodes(
                authorization = authorization(token),
                payload = BatchNodePayload(nodeIds = nodeIds),
            )
            .requireBody(fallback = "批量彻底删除失败。")

    suspend fun createShareLink(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        title: String?,
        password: String?,
        expiresInDays: Int?,
        allowDownload: Boolean,
        allowSave: Boolean,
    ): ShareLinkSummaryResponse =
        serviceFactory.serviceFor(baseUrl)
            .createShareLink(
                authorization = authorization(token),
                payload = CreateShareLinkPayload(
                    nodeIds = nodeIds,
                    title = title,
                    password = password,
                    expiresInDays = expiresInDays,
                    allowDownload = allowDownload,
                    allowSave = allowSave,
                ),
            )
            .requireBody(fallback = "创建分享失败。")

    suspend fun fetchPublicShareStatus(
        baseUrl: String,
        shareCode: String,
    ): ShareLinkStatusResponse =
        serviceFactory.serviceFor(baseUrl)
            .fetchPublicShareStatus(shareCode)
            .requireBody(fallback = "加载分享状态失败。")

    suspend fun verifySharePassword(
        baseUrl: String,
        shareCode: String,
        password: String,
    ): VerifySharePasswordResponse =
        serviceFactory.serviceFor(baseUrl)
            .verifySharePassword(
                shareCode = shareCode,
                payload = VerifySharePasswordPayload(password = password),
            )
            .requireBody(fallback = "提取码校验失败。")

    suspend fun fetchShareDetail(
        baseUrl: String,
        token: String,
        shareCode: String,
        shareAccessToken: String?,
    ): ShareLinkDetailResponse =
        serviceFactory.serviceFor(baseUrl)
            .fetchShareDetail(
                authorization = authorization(token),
                shareAccessToken = shareAccessToken,
                shareCode = shareCode,
            )
            .requireBody(fallback = "加载分享详情失败。")

    suspend fun saveShareToDrive(
        baseUrl: String,
        token: String,
        shareCode: String,
        shareAccessToken: String?,
        parentId: Long?,
        selectedNodeIds: List<Long>? = null,
    ): List<StorageNode> =
        serviceFactory.serviceFor(baseUrl)
            .saveShareToDrive(
                authorization = authorization(token),
                shareAccessToken = shareAccessToken,
                shareCode = shareCode,
                payload = SaveShareLinkPayload(
                    parentId = parentId,
                    selectedNodeIds = selectedNodeIds,
                ),
            )
            .requireBody(fallback = "保存分享失败。")

    suspend fun saveArchiveToUri(
        context: Context,
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        destinationUri: Uri,
        onProgress: (TransferProgress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val response = serviceFactory.serviceFor(baseUrl)
            .downloadArchive(
                authorization = authorization(token),
                payload = BatchNodePayload(nodeIds = nodeIds),
            )

        if (!response.isSuccessful) {
            val payload = runCatching { response.errorBody()?.string() }.getOrNull()
            throw ApiException(
                message = payload.toReadableError(response.code(), "下载选中项目失败。"),
                status = response.code(),
            )
        }

        val fileName = parseFileName(response.headers()["content-disposition"]) ?: "AliciaCloud.zip"
        val body = response.body() ?: throw ApiException("下载选中项目失败。", response.code())

        body.use { responseBody ->
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                responseBody.byteStream().use { input ->
                    copyToWithProgress(
                        input = input,
                        output = output,
                        totalBytes = responseBody.contentLength().takeIf { it >= 0L },
                        onProgress = onProgress,
                    )
                }
            } ?: throw ApiException("无法写入你选择的保存位置。", 400)
        }

        fileName
    }

    suspend fun downloadFile(
        baseUrl: String,
        token: String,
        fileId: Long,
    ): DownloadedFile = withContext(Dispatchers.IO) {
        val response = serviceFactory.serviceFor(baseUrl)
            .downloadFile(
                authorization = authorization(token),
                fileId = fileId,
            )

        if (!response.isSuccessful) {
            val payload = runCatching { response.errorBody()?.string() }.getOrNull()
            throw ApiException(
                message = payload.toReadableError(response.code(), "下载文件失败。"),
                status = response.code(),
            )
        }

        val body = response.body() ?: throw ApiException("下载文件失败。", response.code())
        body.use { responseBody ->
            DownloadedFile(
                fileName = parseFileName(response.headers()["content-disposition"]) ?: "download.bin",
                contentType = response.headers()["content-type"],
                bytes = responseBody.bytes(),
            )
        }
    }

    suspend fun saveDownloadedFileToUri(
        context: Context,
        baseUrl: String,
        token: String,
        fileId: Long,
        destinationUri: Uri,
        onProgress: (TransferProgress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val response = serviceFactory.serviceFor(baseUrl)
            .downloadFile(
                authorization = authorization(token),
                fileId = fileId,
            )

        if (!response.isSuccessful) {
            val payload = runCatching { response.errorBody()?.string() }.getOrNull()
            throw ApiException(
                message = payload.toReadableError(response.code(), "下载文件失败。"),
                status = response.code(),
            )
        }

        val fileName = parseFileName(response.headers()["content-disposition"]) ?: "download.bin"
        val body = response.body() ?: throw ApiException("下载文件失败。", response.code())

        body.use { responseBody ->
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                responseBody.byteStream().use { input ->
                    copyToWithProgress(
                        input = input,
                        output = output,
                        totalBytes = responseBody.contentLength().takeIf { it >= 0L },
                        onProgress = onProgress,
                    )
                }
            } ?: throw ApiException("无法写入你选择的保存位置。", 400)
        }

        fileName
    }

    suspend fun downloadFileViaSignedUrl(
        baseUrl: String,
        token: String,
        fileId: Long,
    ): DownloadedFile {
        val access = fetchFileAccessUrl(baseUrl, token, fileId, "inline")
        return fetchSignedFile(access, "download.bin")
    }

    suspend fun fetchInlineFileAccessUrl(
        baseUrl: String,
        token: String,
        fileId: Long,
    ): SignedUrlResponse = fetchFileAccessUrl(baseUrl, token, fileId, "inline")

    suspend fun cachePreviewFileViaSignedUrl(
        context: Context,
        baseUrl: String,
        token: String,
        fileId: Long,
    ): CachedPreviewFile {
        val access = fetchFileAccessUrl(baseUrl, token, fileId, "inline")
        return fetchSignedFileToCache(context.cacheDir, access, "preview.bin")
    }

    suspend fun saveDownloadedFileToUriViaSignedUrl(
        context: Context,
        baseUrl: String,
        token: String,
        fileId: Long,
        destinationUri: Uri,
        onProgress: (TransferProgress) -> Unit = {},
    ): String {
        val access = fetchFileAccessUrl(baseUrl, token, fileId, "attachment")
        return copySignedFileToUri(
            context = context,
            access = access,
            destinationUri = destinationUri,
            fallbackFileName = "download.bin",
            onProgress = onProgress,
        )
    }

    private suspend fun fetchFileAccessUrl(
        baseUrl: String,
        token: String,
        fileId: Long,
        disposition: String,
    ): SignedUrlResponse =
        serviceFactory.serviceFor(baseUrl)
            .fetchFileAccessUrl(
                authorization = authorization(token),
                fileId = fileId,
                disposition = disposition,
            )
            .requireBody(fallback = "获取文件访问地址失败。")

    private suspend fun copySignedFileToUri(
        context: Context,
        access: SignedUrlResponse,
        destinationUri: Uri,
        fallbackFileName: String,
        onProgress: (TransferProgress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(access.url)
            .get()
            .build()

        directDownloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException("下载文件失败。", response.code)
            }

            val body = response.body ?: throw ApiException("下载文件失败。", response.code)
            val resolvedFileName = parseFileName(response.header("content-disposition"))
                ?: access.fileName
                ?: fallbackFileName

            body.byteStream().use { input ->
                context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                    copyToWithProgress(
                        input = input,
                        output = output,
                        totalBytes = body.contentLength().takeIf { it >= 0L },
                        onProgress = onProgress,
                    )
                } ?: throw ApiException("无法写入你选择的保存位置。", 400)
            }

            resolvedFileName
        }
    }

    private suspend fun fetchSignedFile(
        access: SignedUrlResponse,
        fallbackFileName: String,
    ): DownloadedFile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(access.url)
            .get()
            .build()

        directDownloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException("下载文件失败。", response.code)
            }

            val body = response.body ?: throw ApiException("下载文件失败。", response.code)
            val resolvedFileName = parseFileName(response.header("content-disposition"))
                ?: access.fileName
                ?: fallbackFileName

            body.use { responseBody ->
                DownloadedFile(
                    fileName = resolvedFileName,
                    contentType = response.header("content-type") ?: access.contentType,
                    bytes = responseBody.bytes(),
                )
            }
        }
    }

    private suspend fun fetchSignedFileToCache(
        cacheDir: File,
        access: SignedUrlResponse,
        fallbackFileName: String,
    ): CachedPreviewFile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(access.url)
            .get()
            .build()

        directDownloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException("下载文件失败。", response.code)
            }

            val body = response.body ?: throw ApiException("下载文件失败。", response.code)
            val resolvedFileName = parseFileName(response.header("content-disposition"))
                ?: access.fileName
                ?: fallbackFileName
            val previewDirectory = File(cacheDir, "preview-cache").apply { mkdirs() }
            val suffix = resolvedFileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                ?.let { ".${it.lowercase()}" }
                ?: ".bin"
            val tempFile = File.createTempFile("alicia-preview-", suffix, previewDirectory)

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            CachedPreviewFile(
                fileName = resolvedFileName,
                contentType = response.header("content-type") ?: access.contentType,
                localPath = tempFile.absolutePath,
            )
        }
    }

    private fun authorization(token: String) = "Bearer $token"
}

private class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val totalBytes: Long?,
    private val onProgress: (TransferProgress) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = totalBytes ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
        var transferredBytes = 0L
        onProgress(TransferProgress(transferredBytes = transferredBytes, totalBytes = totalBytes))

        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                sink.write(buffer, 0, read)
                transferredBytes += read.toLong()
                onProgress(
                    TransferProgress(
                        transferredBytes = transferredBytes,
                        totalBytes = totalBytes,
                    ),
                )
            }
        }
    }
}

private suspend fun copyToWithProgress(
    input: java.io.InputStream,
    output: java.io.OutputStream,
    totalBytes: Long?,
    onProgress: (TransferProgress) -> Unit,
) {
    val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
    var transferredBytes = 0L
    onProgress(TransferProgress(transferredBytes = transferredBytes, totalBytes = totalBytes))

    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read == -1) {
            break
        }
        output.write(buffer, 0, read)
        transferredBytes += read.toLong()
        onProgress(
            TransferProgress(
                transferredBytes = transferredBytes,
                totalBytes = totalBytes,
            ),
        )
    }
    output.flush()
}

private data class OpenableAsset(
    val fileName: String,
    val contentType: String?,
    val sizeBytes: Long?,
)

private data class PreparedUploadFile(
    val file: File,
    val fileName: String,
    val contentType: String,
)

private fun ContentResolver.resolveOpenableAsset(uri: Uri): OpenableAsset {
    var fileName: String? = null
    var sizeBytes: Long? = null

    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

            if (nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }

            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                sizeBytes = cursor.getLong(sizeIndex)
            }
        }
    }

    return OpenableAsset(
        fileName = fileName ?: uri.lastPathSegment ?: "upload.bin",
        contentType = getType(uri),
        sizeBytes = sizeBytes,
    )
}

private fun ContentResolver.prepareAvatarUploadFile(uri: Uri, cacheDir: File): PreparedUploadFile {
    val asset = resolveOpenableAsset(uri)
    val inputBytes = openInputStream(uri)?.use { it.readBytes() }
        ?: throw ApiException("无法读取你选择的头像文件。", 400)

    val bitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
        ?: throw ApiException("当前图片格式暂不支持，请换成 JPG、PNG、GIF 或 WebP 后重试。", 400)

    val scaledBitmap = bitmap.scaleDown(maxDimension = MAX_AVATAR_DIMENSION)
    val outputBytes = scaledBitmap.compressAvatarJpeg(maxBytes = MAX_AVATAR_UPLOAD_BYTES)
        ?: throw ApiException("请选择更小的头像图片，建议使用清晰的人像图。", 400)

    if (scaledBitmap !== bitmap) {
        scaledBitmap.recycle()
    }
    bitmap.recycle()

    val tempFile = File.createTempFile("alicia-avatar-", ".jpg", cacheDir)
    tempFile.writeBytes(outputBytes)

    val normalizedName = asset.fileName.substringBeforeLast('.', asset.fileName) + ".jpg"
    return PreparedUploadFile(
        file = tempFile,
        fileName = normalizedName,
        contentType = "image/jpeg",
    )
}

private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
    val longestSide = max(width, height)
    if (longestSide <= maxDimension) {
        return this
    }

    val scale = maxDimension.toFloat() / longestSide.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun Bitmap.compressAvatarJpeg(maxBytes: Long): ByteArray? {
    var quality = 92

    while (quality >= 48) {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, output)
        val bytes = output.toByteArray()
        if (bytes.size.toLong() <= maxBytes) {
            return bytes
        }
        quality -= 8
    }

    return null
}

private fun <T> Response<T>.requireBody(fallback: String): T {
    if (isSuccessful) {
        return body() ?: throw ApiException(fallback, status = code())
    }

    val rawBody = runCatching { errorBody()?.string() }.getOrNull()
    throw ApiException(
        message = rawBody.toReadableError(code(), fallback),
        status = code(),
    )
}

private fun String?.toReadableError(status: Int, fallback: String): String {
    val readableStatusError = statusToReadableError(status)
    val body = this?.trim().orEmpty()

    if (body.isNotEmpty() && !body.isHtmlDocument()) {
        runCatching {
            JsonParser.parseString(body)
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.let { jsonObject ->
                    listOf("error", "message")
                        .firstNotNullOfOrNull { key ->
                            jsonObject.get(key)
                                ?.takeIf { it.isJsonPrimitive }
                                ?.asString
                                ?.takeIf { value -> value.isNotBlank() }
                        }
                }
        }.getOrNull()?.let { return it }

        return body
    }

    return readableStatusError ?: fallback
}

private fun String.isHtmlDocument(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("<!doctype html") ||
        normalized.startsWith("<html") ||
        normalized.contains("<body")
}

private fun parseFileName(contentDisposition: String?): String? {
    if (contentDisposition.isNullOrBlank()) {
        return null
    }

    val utf8Match = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
        .find(contentDisposition)
        ?.groupValues
        ?.getOrNull(1)

    if (!utf8Match.isNullOrBlank()) {
        return runCatching { URLDecoder.decode(utf8Match, "UTF-8") }
            .getOrElse { utf8Match }
    }

    return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
        .find(contentDisposition)
        ?.groupValues
        ?.getOrNull(1)
}

private fun statusToReadableError(status: Int): String? =
    when (status) {
        400 -> "请求内容不正确，请检查填写的信息。"
        401 -> "登录状态已过期，请重新登录。"
        403 -> "当前账号没有权限执行这个操作。"
        404 -> "请求的资源不存在。"
        413 -> "文件太大，当前后端拒绝了这次上传。"
        415 -> "当前文件类型不受支持。"
        429 -> "请求过于频繁，请稍后再试。"
        502, 503, 504 -> "服务暂时不可用，请稍后再试。"
        in 500..599 -> "服务器处理失败，请稍后再试。"
        else -> null
    }
