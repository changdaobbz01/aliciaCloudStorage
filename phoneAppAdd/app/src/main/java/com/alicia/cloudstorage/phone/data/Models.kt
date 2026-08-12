package com.alicia.cloudstorage.phone.data

enum class UserRole {
    ADMIN,
    USER,
}

enum class UserStatus {
    ACTIVE,
    DISABLED,
}

enum class StorageNodeType {
    FOLDER,
    FILE,
}

enum class StorageNodeFilter {
    ALL,
    FOLDER,
    FILE,
}

enum class StorageFileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
}

enum class AppTab {
    HOME,
    FILES,
    TRASH,
    TRANSFERS,
    TEAM,
    ME,
}

data class User(
    val id: Long,
    val phoneNumber: String,
    val nickname: String,
    val avatarUrl: String?,
    val homeBackgroundUrl: String?,
    val role: UserRole,
    val status: UserStatus,
    val createdAt: String,
    val storageQuotaBytes: Long?,
    val usedBytes: Long,
    val remainingBytes: Long?,
)

data class LoginPayload(
    val phoneNumber: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val user: User,
)

data class ApiMessageResponse(
    val message: String,
)

data class AppPackageVersionInfo(
    val available: Boolean,
    val versionName: String?,
    val releaseNotes: String?,
    val downloadUrl: String,
    val uploadedAt: String?,
)

data class ChangePasswordPayload(
    val oldPassword: String,
    val newPassword: String,
)

data class UpdateProfilePayload(
    val phoneNumber: String,
    val nickname: String,
    val avatarUrl: String?,
)

data class CreateUserPayload(
    val phoneNumber: String,
    val nickname: String,
    val avatarUrl: String?,
    val password: String,
    val role: UserRole,
    val storageQuotaBytes: Long?,
)

data class ResetUserPasswordPayload(
    val newPassword: String,
)

data class UpdateUserStorageQuotaPayload(
    val storageQuotaBytes: Long,
)

data class CreateFolderPayload(
    val parentId: Long?,
    val folderName: String,
)

data class RenameNodePayload(
    val name: String,
)

data class MoveNodePayload(
    val parentId: Long?,
)

data class BatchRenameNodeItemPayload(
    val nodeId: Long,
    val name: String,
)

data class BatchRenameNodePayload(
    val items: List<BatchRenameNodeItemPayload>,
)

data class BatchNodePayload(
    val nodeIds: List<Long>,
)

data class BatchMoveNodePayload(
    val nodeIds: List<Long>,
    val parentId: Long?,
)

data class CreateShareLinkPayload(
    val nodeIds: List<Long>,
    val title: String?,
    val password: String?,
    val expiresInDays: Int?,
    val allowDownload: Boolean,
    val allowSave: Boolean,
)

data class DriveOverview(
    val totalItems: Int,
    val totalFolders: Int,
    val totalFiles: Int,
    val usedBytes: Long,
    val totalSpaceBytes: Long?,
    val actualUsedBytes: Long,
    val scope: String,
)

data class UsageHistoryPoint(
    val date: String,
    val usedBytes: Long,
)

data class StorageNode(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val type: StorageNodeType,
    val size: Long,
    val extension: String?,
    val mimeType: String?,
    val updatedAt: String,
    val deletedAt: String?,
)

data class StorageNodePage(
    val items: List<StorageNode>,
    val page: Int,
    val size: Int,
    val totalItems: Int,
    val totalPages: Int,
    val sortBy: String,
    val sortDirection: String,
)

data class FolderCrumb(
    val id: Long?,
    val label: String,
)

data class SavedSession(
    val token: String?,
    val baseUrl: String,
)

data class DownloadedFile(
    val fileName: String,
    val contentType: String?,
    val bytes: ByteArray,
)

data class CachedPreviewFile(
    val fileName: String,
    val contentType: String?,
    val localPath: String,
)

data class SignedUrlResponse(
    val url: String,
    val fileName: String?,
    val contentType: String?,
    val expiresAtEpochMillis: Long,
)

data class ShareLinkSummaryResponse(
    val id: Long,
    val shareCode: String,
    val title: String,
    val hasPassword: Boolean,
    val expiresAt: String?,
    val allowDownload: Boolean,
    val allowSave: Boolean,
    val status: String,
    val viewCount: Long,
    val lastAccessedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val itemCount: Long,
)

data class ShareLinkStatusResponse(
    val shareCode: String,
    val title: String?,
    val available: Boolean,
    val requiresPassword: Boolean,
    val expiresAt: String?,
    val reason: String?,
)

data class VerifySharePasswordPayload(
    val password: String,
)

data class VerifySharePasswordResponse(
    val accessToken: String?,
    val expiresAt: String?,
)

data class ShareLinkDetailResponse(
    val shareCode: String,
    val title: String,
    val ownerNickname: String,
    val expiresAt: String?,
    val allowDownload: Boolean,
    val allowSave: Boolean,
    val rootNodeIds: List<Long>,
    val items: List<StorageNode>,
)

data class SaveShareLinkPayload(
    val parentId: Long?,
    val selectedNodeIds: List<Long>? = null,
)

val User.isAdmin: Boolean
    get() = role == UserRole.ADMIN
