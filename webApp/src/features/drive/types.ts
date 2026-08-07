import type { ResetUserPasswordPayload, SortDirection, StorageNode, StorageNodeSortField, User } from '../../types';

export type FolderCrumb = {
  id: number | null;
  label: string;
};

export type FolderTreeNode = {
  title: string;
  value: string;
  children?: FolderTreeNode[];
};

export type DriveListState = {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  sortBy: StorageNodeSortField;
  sortDirection: SortDirection;
};

export type DriveUploadTaskStatus = 'queued' | 'uploading' | 'retrying' | 'completing' | 'success' | 'error' | 'canceled';

export type DriveUploadTask = {
  id: string;
  file: File;
  parentId: number | null;
  uploadToken: string | null;
  progress: number;
  loadedBytes: number;
  totalBytes: number;
  status: DriveUploadTaskStatus;
  attempt: number;
  error: string | null;
};

export type DrivePreviewKind = 'image' | 'pdf' | 'text' | 'audio' | 'video' | 'unsupported';

export type DrivePreviewState = {
  target: StorageNode | null;
  kind: DrivePreviewKind | null;
  loading: boolean;
  objectUrl: string | null;
  textContent: string;
  note: string | null;
  error: string | null;
};

export type CreateUserFormValues = {
  phoneNumber: string;
  nickname: string;
  avatarUrl: string | null;
  inheritAdminBackground: boolean;
  password: string;
  role: User['role'];
  storageQuotaGb: number;
};

export type UpdateUserQuotaFormValues = {
  storageQuotaGb: number;
};

export type AppPackageUploadFormValues = {
  versionName: string;
  releaseNotes: string;
};

export type ResetUserPasswordFormValues = ResetUserPasswordPayload & {
  confirmPassword: string;
};

export type CreateShareFormValues = {
  title: string;
  passwordEnabled: boolean;
  password?: string;
  expiresInDays: number;
  allowDownload: boolean;
  allowSave: boolean;
};
