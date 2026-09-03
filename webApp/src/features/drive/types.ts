import type { SortDirection, StorageNode, StorageNodeSortField } from '../../types';

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

export type DriveStorageMutationKind = 'create-folder' | 'rename' | 'move' | 'delete' | 'restore' | 'permanent-delete';

export type DriveStorageMutationState = {
  kind: DriveStorageMutationKind;
  nodeIds: number[];
} | null;

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

export type DriveDownloadTaskStatus =
  | 'queued'
  | 'preparing'
  | 'downloading'
  | 'saving'
  | 'success'
  | 'error'
  | 'canceled';

export type DriveDownloadSourceType = 'file' | 'archive';

export type DriveDownloadTask = {
  id: string;
  sourceType: DriveDownloadSourceType;
  nodeIds: number[];
  displayName: string;
  fileName: string | null;
  version: string | null;
  status: DriveDownloadTaskStatus;
  loadedBytes: number;
  totalBytes: number | null;
  percent: number | null;
  createdAt: number;
  finishedAt: number | null;
  error: string | null;
};

export type DriveDownloadButtonState = {
  label: string;
  busy: boolean;
  task: DriveDownloadTask | null;
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

export type CreateShareFormValues = {
  title: string;
  passwordEnabled: boolean;
  password?: string;
  expiresInDays: number;
  allowDownload: boolean;
  allowSave: boolean;
};
