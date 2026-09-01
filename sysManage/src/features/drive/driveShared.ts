import type { AppPackageInfo, User } from '../../types';

const BYTES_PER_GIB = 1024 * 1024 * 1024;
export const APP_DOWNLOAD_PUBLIC_PATH = '/api/app-package/download/current';

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
  return value === null ? '未配置' : formatFileSize(value);
}

export function gigabytesToBytes(gigabytes: number) {
  return Math.round(gigabytes * BYTES_PER_GIB);
}

export function resolveAvatarSrc(user: User | null | undefined) {
  if (!user?.avatarUrl) {
    return undefined;
  }

  if (user.avatarUrl.startsWith('cos:')) {
    return `/api/cloud-profile/avatar/${user.id}?v=${encodeURIComponent(user.avatarUrl)}`;
  }

  return user.avatarUrl;
}

export function normalizeAppDownloadPath(downloadPath = APP_DOWNLOAD_PUBLIC_PATH) {
  const currentOrigin = typeof window === 'undefined' ? 'https://alicia.local' : window.location.origin;

  try {
    const url = new URL(downloadPath || APP_DOWNLOAD_PUBLIC_PATH, currentOrigin);
    if (url.origin !== currentOrigin || url.pathname !== APP_DOWNLOAD_PUBLIC_PATH) {
      return APP_DOWNLOAD_PUBLIC_PATH;
    }
  } catch {
    return APP_DOWNLOAD_PUBLIC_PATH;
  }

  return APP_DOWNLOAD_PUBLIC_PATH;
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
