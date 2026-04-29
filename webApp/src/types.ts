export type HealthResponse = {
  status: string;
  service: string;
  timestamp: string;
};

export type UserRole = 'ADMIN' | 'USER';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type StorageNodeType = 'FOLDER' | 'FILE';
export type StorageNodeFilter = 'ALL' | StorageNodeType;
export type StorageViewMode = 'home' | 'drive' | 'accounts' | 'trash';
export type SortDirection = 'asc' | 'desc';
export type DriveSortField = 'name' | 'size' | 'updatedAt';
export type TrashSortField = 'name' | 'size' | 'updatedAt' | 'deletedAt';
export type StorageNodeSortField = DriveSortField | TrashSortField;

export type User = {
  id: number;
  phoneNumber: string;
  nickname: string;
  avatarUrl: string | null;
  homeBackgroundUrl: string | null;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  storageQuotaBytes: number | null;
  usedBytes: number;
  remainingBytes: number | null;
};

export type LoginPayload = {
  phoneNumber: string;
  password: string;
};

export type LoginResponse = {
  token: string;
  user: User;
};

export type UpdateProfilePayload = {
  phoneNumber: string;
  nickname: string;
  avatarUrl: string | null;
};

export type ChangePasswordPayload = {
  oldPassword: string;
  newPassword: string;
};

export type CreateUserPayload = {
  phoneNumber: string;
  nickname: string;
  avatarUrl: string | null;
  password: string;
  role: UserRole;
  storageQuotaBytes: number | null;
};

export type UpdateUserStorageQuotaPayload = {
  storageQuotaBytes: number;
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
};
