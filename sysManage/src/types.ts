export type HealthResponse = {
  status: string;
  service: string;
  timestamp: string;
};

export type UserRole = 'ADMIN' | 'USER';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type ApplicationRoles = Record<string, string>;
export type StorageNodeType = 'FOLDER' | 'FILE';
export type SortDirection = 'asc' | 'desc';

export type User = {
  id: number;
  phoneNumber: string;
  email: string | null;
  nickname: string;
  avatarUrl: string | null;
  homeBackgroundUrl: string | null;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  storageQuotaBytes: number | null;
  usedBytes: number;
  remainingBytes: number | null;
  appRoles: ApplicationRoles;
};

export type IdentityUser = {
  id: number;
  phoneNumber: string | null;
  email: string | null;
  emailVerifiedAt: string | null;
  nickname: string;
  avatarUrl: string | null;
  tokenVersion: number;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  appRoles: ApplicationRoles;
};

export function isCloudAdmin(
  user: Pick<User, 'role' | 'appRoles'> | Pick<IdentityUser, 'role' | 'appRoles'> | null | undefined,
) {
  return user?.role === 'ADMIN' || user?.appRoles?.cloud === 'CLOUD_ADMIN';
}

export function cloudRoleLabel(
  user: Pick<User, 'role' | 'appRoles'> | Pick<IdentityUser, 'role' | 'appRoles'> | null | undefined,
) {
  if (!user) {
    return '无后台权限';
  }

  if (user.role === 'ADMIN') {
    return '全局管理员';
  }

  if (user.appRoles?.cloud === 'CLOUD_ADMIN') {
    return '云盘管理员';
  }

  return '云盘用户';
}

export type IdentityLoginResponse = {
  token: string;
  refreshToken: string;
  user: IdentityUser;
};

export type IdentitySession = {
  id: number;
  issuedAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  revokedAt: string | null;
  revokeReason: string | null;
  clientIp: string | null;
  userAgent: string | null;
  current: boolean;
};

export type PageResponse<TItem, TSortBy extends string = string> = {
  items: TItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  sortBy: TSortBy;
  sortDirection: SortDirection;
};

export type AdminCloudOperationsOverview = {
  generatedAt: string;
  capacity: {
    systemTotalSpaceBytes: number;
    allocatedQuotaBytes: number;
    actualUsedBytes: number;
    remainingUnallocatedBytes: number;
    allocatedUsageRatio: number;
    actualUsageRatio: number;
  };
  activeNodes: {
    totalItems: number;
    folderCount: number;
    fileCount: number;
  };
  trash: {
    totalItems: number;
    rootItems: number;
    folderCount: number;
    fileCount: number;
    bytes: number;
    latestDeletedAt: string | null;
  };
  shares: {
    totalLinks: number;
    activeLinks: number;
    availableLinks: number;
    expiredActiveLinks: number;
    revokedLinks: number;
    passwordProtectedLinks: number;
    downloadEnabledLinks: number;
    saveEnabledLinks: number;
    totalViews: number;
    latestCreatedAt: string | null;
    latestAccessedAt: string | null;
  };
  multipartUploads: {
    totalSessions: number;
    inProgressSessions: number;
    staleInProgressSessions: number;
    completedSessions: number;
    abortedSessions: number;
    latestInProgressUpdatedAt: string | null;
    staleHours: number;
  };
};

export type AdminCloudShareStatusFilter = 'ACTIVE' | 'AVAILABLE' | 'EXPIRED' | 'REVOKED';
export type AdminCloudShareSortField =
  | 'title'
  | 'ownerId'
  | 'expiresAt'
  | 'lastAccessedAt'
  | 'updatedAt'
  | 'viewCount'
  | 'createdAt';
export type AdminCloudTrashSortField = 'name' | 'ownerId' | 'size' | 'updatedAt' | 'deletedAt';
export type AdminCloudStorageUserSortField =
  | 'usedBytes'
  | 'storageQuotaBytes'
  | 'remainingBytes'
  | 'usageRatio'
  | 'activeItems'
  | 'trashItems'
  | 'shareLinks'
  | 'createdAt'
  | 'nickname'
  | 'id';

export type AdminCloudOperationPageQuery<TSortBy extends string> = {
  page?: number;
  size?: number;
  sortBy?: TSortBy;
  sortDirection?: SortDirection;
};

export type AdminCloudShareLinksQuery = AdminCloudOperationPageQuery<AdminCloudShareSortField> & {
  ownerId?: number | null;
  status?: AdminCloudShareStatusFilter | null;
  passwordProtected?: boolean | null;
};

export type AdminCloudTrashNodesQuery = AdminCloudOperationPageQuery<AdminCloudTrashSortField> & {
  ownerId?: number | null;
  keyword?: string | null;
  type?: StorageNodeType | null;
  rootOnly?: boolean | null;
};

export type AdminCloudStorageUsersQuery = AdminCloudOperationPageQuery<AdminCloudStorageUserSortField>;

export type AdminCloudShareLink = {
  id: number;
  ownerId: number;
  title: string;
  status: 'ACTIVE' | 'REVOKED';
  effectiveStatus: 'AVAILABLE' | 'EXPIRED' | 'REVOKED';
  passwordProtected: boolean;
  allowDownload: boolean;
  allowSave: boolean;
  viewCount: number;
  itemCount: number;
  expiresAt: string | null;
  lastAccessedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AdminCloudTrashNode = {
  id: number;
  ownerId: number;
  parentId: number | null;
  originalParentId: number | null;
  name: string;
  type: StorageNodeType;
  size: number;
  deletedBy: number | null;
  rootItem: boolean;
  deletedAt: string | null;
  updatedAt: string;
};

export type AdminCloudStorageUserUsage = {
  userId: number;
  phoneNumber: string | null;
  email: string | null;
  nickname: string;
  role: UserRole;
  status: UserStatus;
  storageQuotaBytes: number | null;
  usedBytes: number;
  remainingBytes: number | null;
  usageRatio: number | null;
  activeItems: number;
  activeFolders: number;
  activeFiles: number;
  trashItems: number;
  shareLinks: number;
  createdAt: string | null;
};

export type AdminCloudShareLinksPage = PageResponse<AdminCloudShareLink, AdminCloudShareSortField>;
export type AdminCloudTrashNodesPage = PageResponse<AdminCloudTrashNode, AdminCloudTrashSortField>;
export type AdminCloudStorageUsersPage = PageResponse<AdminCloudStorageUserUsage, AdminCloudStorageUserSortField>;

export type AppPackageInfo = {
  available: boolean;
  fileName: string | null;
  fileSizeBytes: number | null;
  uploadedAt: string | null;
  downloadUrl: string;
  versionName: string | null;
  releaseNotes: string | null;
};

export type UpdateUserStorageQuotaPayload = {
  storageQuotaBytes: number;
};

export type UpdateProfilePayload = {
  nickname: string;
  phoneNumber?: string | null;
  avatarUrl: string | null;
};

export type ApiMessageResponse = {
  message: string;
};
