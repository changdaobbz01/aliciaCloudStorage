package com.alicia.cloudstorage.phone.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.URLDecoder

class ApiException(
    override val message: String,
    val status: Int,
    cause: Throwable? = null,
) : IOException(message, cause)

private const val MAX_DIRECT_UPLOAD_BYTES = 20L * 1024 * 1024

class AliciaRepository(
    private val serviceFactory: AliciaCloudServiceFactory = AliciaCloudServiceFactory(),
) {
    suspend fun login(baseUrl: String, phoneNumber: String, password: String): LoginResponse =
        serviceFactory.serviceFor(baseUrl)
            .login(LoginPayload(phoneNumber = phoneNumber, password = password))
            .requireBody(fallback = "登录失败，请检查手机号和密码。")

    suspend fun fetchCurrentUser(baseUrl: String, token: String): User =
        serviceFactory.serviceFor(baseUrl)
            .fetchCurrentUser(authorization(token))
            .requireBody(fallback = "获取当前账号信息失败。")

    suspend fun uploadCurrentUserAvatar(
        context: Context,
        baseUrl: String,
        token: String,
        uri: Uri,
    ): User {
        val asset = context.contentResolver.resolveOpenableAsset(uri)
        val suffix = asset.fileName.substringAfterLast('.', "").ifBlank { "jpg" }
        val tempFile = File.createTempFile("alicia-avatar-", ".$suffix", context.cacheDir)

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw ApiException("无法读取你选择的头像文件。", 400)

            val requestBody = tempFile.asRequestBody(
                (asset.contentType ?: "application/octet-stream").toMediaTypeOrNull(),
            )
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = asset.fileName,
                body = requestBody,
            )

            return serviceFactory.serviceFor(baseUrl)
                .uploadAvatar(
                    authorization = authorization(token),
                    file = filePart,
                )
                .requireBody(fallback = "更新头像失败。")
        } finally {
            tempFile.delete()
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
    ): StorageNodePage =
        serviceFactory.serviceFor(baseUrl)
            .fetchStorageNodes(
                authorization = authorization(token),
                parentId = parentId,
                keyword = keyword.trim().takeIf { it.isNotEmpty() },
                type = filter.takeUnless { it == StorageNodeFilter.ALL }?.name,
                page = 1,
                size = 100,
                sortBy = "name",
                sortDirection = "asc",
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

            val requestBody = tempFile.asRequestBody(
                (asset.contentType ?: "application/octet-stream").toMediaTypeOrNull(),
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

    suspend fun downloadFile(
        baseUrl: String,
        token: String,
        fileId: Long,
    ): DownloadedFile {
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
            return DownloadedFile(
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
    ): String {
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
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                responseBody.byteStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw ApiException("无法写入你选择的保存位置。", 400)
        }

        return fileName
    }

    private fun authorization(token: String) = "Bearer $token"
}

private data class OpenableAsset(
    val fileName: String,
    val contentType: String?,
    val sizeBytes: Long?,
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
