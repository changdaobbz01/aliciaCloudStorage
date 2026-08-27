export type HealthResponse = {
  status: string;
  service: string;
  timestamp: string;
};

export type UserRole = 'ADMIN' | 'USER';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type ApplicationRoles = Record<string, string>;
export type StorageNodeType = 'FOLDER' | 'FILE';
export type StorageNodeFilter = 'ALL' | StorageNodeType;
export type StorageFileCategory = 'IMAGE' | 'VIDEO' | 'AUDIO' | 'DOCUMENT' | 'ARCHIVE';
export type StorageViewMode = 'home' | 'drive' | 'downloads' | 'shares' | 'trash';
export type SortDirection = 'asc' | 'desc';
export type DriveSortField = 'name' | 'size' | 'updatedAt';
export type TrashSortField = 'name' | 'size' | 'updatedAt' | 'deletedAt';
export type StorageNodeSortField = DriveSortField | TrashSortField;

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

export type AppPackageInfo = {
  available: boolean;
  fileName: string | null;
  fileSizeBytes: number | null;
  uploadedAt: string | null;
  downloadUrl: string;
  versionName: string | null;
  releaseNotes: string | null;
};

export type SignedUrlResponse = {
  url: string;
  fileName: string | null;
  contentType: string | null;
  expiresAtEpochMillis: number;
};

export type UpdateProfilePayload = {
  phoneNumber?: string | null;
  nickname: string;
  avatarUrl: string | null;
};

export type ChangePasswordPayload = {
  oldPassword: string;
  newPassword: string;
};

export type CreateFolderPayload = {
  parentId?: number | null;
  folderName: string;
};

export type RenameNodePayload = {
  name: string;
};

export type MoveNodePayload = {
  parentId?: number | null;
};

export type BatchNodePayload = {
  nodeIds: number[];
};

export type BatchMoveNodePayload = BatchNodePayload & {
  parentId?: number | null;
};

export type CreateMultipartUploadPayload = {
  parentId?: number | null;
  fileName: string;
  fileSize: number;
  contentType?: string | null;
  chunkSize: number;
  totalChunks: number;
  fingerprint: string;
};

export type MultipartUploadPart = {
  partNumber: number;
  eTag: string;
  size: number;
};

export type MultipartUploadStatus = {
  uploadToken: string;
  fileName: string;
  fileSize: number;
  contentType: string;
  chunkSize: number;
  totalChunks: number;
  uploadedParts: MultipartUploadPart[];
  status: 'IN_PROGRESS' | 'COMPLETED' | 'ABORTED';
};

export type ApiMessageResponse = {
  message: string;
};

export type DriveOverview = {
  totalItems: number;
  totalFolders: number;
  totalFiles: number;
  usedBytes: number;
  totalSpaceBytes: number | null;
  actualUsedBytes: number;
  scope: 'USER' | 'ADMIN';
};

export type UsageHistoryPoint = {
  date: string;
  usedBytes: number;
};

export type StorageNode = {
  id: number;
  parentId: number | null;
  name: string;
  type: StorageNodeType;
  size: number;
  extension: string | null;
  mimeType: string | null;
  updatedAt: string;
  deletedAt: string | null;
};

export type StorageNodePage = {
  items: StorageNode[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  sortBy: StorageNodeSortField;
  sortDirection: SortDirection;
};

export type StorageNodeQuery = {
  page?: number;
  size?: number;
  sortBy?: StorageNodeSortField;
  sortDirection?: SortDirection;
  recursive?: boolean;
  category?: StorageFileCategory | null;
};

export type CreateShareLinkPayload = {
  nodeIds: number[];
  title?: string | null;
  password?: string | null;
  expiresInDays?: number | null;
  allowDownload: boolean;
  allowSave: boolean;
};

export type ShareLinkSummary = {
  id: number;
  shareCode: string;
  title: string;
  hasPassword: boolean;
  expiresAt: string | null;
  allowDownload: boolean;
  allowSave: boolean;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  viewCount: number;
  lastAccessedAt: string | null;
  createdAt: string;
  updatedAt: string;
  itemCount: number;
};

export type ShareLinkStatus = {
  shareCode: string;
  title: string | null;
  available: boolean;
  requiresPassword: boolean;
  expiresAt: string | null;
  reason: 'REVOKED' | 'EXPIRED' | null;
};

export type VerifySharePasswordPayload = {
  password: string;
};

export type VerifySharePasswordResponse = {
  accessToken: string | null;
  expiresAt: string | null;
};

export type ShareLinkDetail = {
  shareCode: string;
  title: string;
  ownerNickname: string;
  expiresAt: string | null;
  allowDownload: boolean;
  allowSave: boolean;
  rootNodeIds: number[];
  items: StorageNode[];
};

export type SaveShareLinkPayload = {
  parentId?: number | null;
  selectedNodeIds?: number[] | null;
};
