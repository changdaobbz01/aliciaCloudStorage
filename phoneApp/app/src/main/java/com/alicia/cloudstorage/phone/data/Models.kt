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

enum class AppTab {
    HOME,
    FILES,
    TRASH,
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

data class ChangePasswordPayload(
    val oldPassword: String,
    val newPassword: String,
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

val User.isAdmin: Boolean
    get() = role == UserRole.ADMIN
