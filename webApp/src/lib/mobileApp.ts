import { appPath, appUrl } from './appPaths';

const DEFAULT_ANDROID_PACKAGE_NAME = 'com.alicia.cloudstorage.phone.add';

export const ANDROID_PACKAGE_NAME =
  import.meta.env.VITE_ANDROID_PACKAGE_NAME?.trim() || DEFAULT_ANDROID_PACKAGE_NAME;

export function buildAppDownloadUrl(shareCode?: string) {
  if (typeof window === 'undefined') {
    return appPath('/app-download');
  }

  const url = new URL(appPath('/app-download'), window.location.origin);
  if (shareCode) {
    url.searchParams.set('share', shareCode);
  }
  return url.toString();
}

export function buildShareIntentUrl(shareCode: string) {
  const sharePath = `/share/${encodeURIComponent(shareCode)}`;
  const shareUrl = appUrl(sharePath);

  return `intent://share/${encodeURIComponent(shareCode)}#Intent;scheme=aliciacloud;package=${ANDROID_PACKAGE_NAME};S.browser_fallback_url=${encodeURIComponent(
    buildAppDownloadUrl(shareCode),
  )};S.alicia_web_url=${encodeURIComponent(shareUrl)};end`;
}
