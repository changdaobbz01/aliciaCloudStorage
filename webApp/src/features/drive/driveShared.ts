import type { AppPackageInfo, StorageFileCategory, StorageViewMode, User } from '../../types';
import type { DriveListState } from './types';

const BYTES_PER_GIB = 1024 * 1024 * 1024;
export const ROOT_PARENT_KEY = 'ROOT';
export const DEFAULT_NEW_USER_QUOTA_GB = 50;
export const APP_DOWNLOAD_PUBLIC_PATH = '/api/app-package/download/current';
export const storageFileCategoryLabels: Record<StorageFileCategory, string> = {
  IMAGE: '相册',
  VIDEO: '视频',
  AUDIO: '音频',
  DOCUMENT: '文档',
  ARCHIVE: '压缩包',
};
export const storageFileCategoryDescriptions: Record<StorageFileCategory, string> = {
  IMAGE: '全盘归集图片文件，便于预览、分享和批量下载。',
  VIDEO: '全盘归集视频文件，便于预览和下载。',
  AUDIO: '全盘归集音频文件，便于播放和下载。',
  DOCUMENT: '全盘归集文档、表格、演示和文本文件。',
  ARCHIVE: '全盘归集压缩包和归档文件。',
};

export function getStorageFileCategoryLabel(category: StorageFileCategory | null) {
  return category ? storageFileCategoryLabels[category] : null;
}

export function createDefaultListState(view: StorageViewMode): DriveListState {
  return {
    page: 1,
    size: 10,
    totalItems: 0,
    totalPages: 0,
    sortBy: view === 'trash' ? 'deletedAt' : 'name',
    sortDirection: view === 'trash' ? 'desc' : 'asc',
  };
}

export function formatFileSize(value: number) {
  if (value === 0) {
    return '0 B';
  }

  if (value < 1024) {
    return `${value} B`;
  }

  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unitIndex = -1;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(size >= 100 ? 0 : 1)} ${units[unitIndex]}`;
}

export function bytesToGigabytes(bytes: number) {
  return Number((bytes / BYTES_PER_GIB).toFixed(2));
}

export function formatNullableBytes(value: number | null) {
  return value === null ? '无限制' : formatFileSize(value);
}

export function gigabytesToBytes(gigabytes: number) {
  return Math.round(gigabytes * BYTES_PER_GIB);
}

export function resolveAvatarSrc(user: User | null | undefined) {
  if (!user?.avatarUrl) {
    return undefined;
  }

  if (user.avatarUrl.startsWith('cos:')) {
    return `/api/auth/avatar/${user.id}?v=${encodeURIComponent(user.avatarUrl)}`;
  }

  return user.avatarUrl;
}

export function resolveHomeBackgroundSrc(user: User | null | undefined) {
  if (!user?.homeBackgroundUrl) {
    return null;
  }

  if (user.homeBackgroundUrl.startsWith('cosbg:')) {
    return `/api/auth/background/${user.id}?v=${encodeURIComponent(user.homeBackgroundUrl)}`;
  }

  return user.homeBackgroundUrl;
}

export function resolveAppDownloadUrl(downloadPath = APP_DOWNLOAD_PUBLIC_PATH) {
  if (typeof window === 'undefined') {
    return downloadPath;
  }

  return new URL(downloadPath, window.location.origin).toString();
}

export function resolveShareUrl(shareCode: string) {
  const path = `/share/${encodeURIComponent(shareCode)}`;

  if (typeof window === 'undefined') {
    return path;
  }

  return new URL(path, window.location.origin).toString();
}

export function createEmptyAppPackageInfo(): AppPackageInfo {
  return {
    available: false,
    fileName: null,
    fileSizeBytes: null,
    uploadedAt: null,
    downloadUrl: APP_DOWNLOAD_PUBLIC_PATH,
    versionName: null,
    releaseNotes: null,
  };
}
