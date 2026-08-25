import { appPath } from './appPaths';

const UNIFIED_LOGIN_PATH = '/login';
const DEFAULT_RETURN_TO = appPath('/');
const LOGIN_REDIRECT_REASONS = new Set(['session-expired', 'login-required']);

export type LoginRedirectReason = 'session-expired' | 'login-required';

function normalizeReturnTo(returnTo: string) {
  const trimmed = returnTo.trim();

  if (!trimmed || !trimmed.startsWith('/') || trimmed.startsWith('//') || isLoginPath(trimmed)) {
    return DEFAULT_RETURN_TO;
  }

  return trimmed;
}

function safeSuffix(value: string, expectedPrefix: '?' | '#') {
  const trimmed = value.trim();
  return trimmed.startsWith(expectedPrefix) ? trimmed : '';
}

function normalizeReason(reason: LoginRedirectReason | null | undefined) {
  return reason && LOGIN_REDIRECT_REASONS.has(reason) ? reason : null;
}

function isLoginPath(pathname: string) {
  const pathOnly = pathname.split(/[?#]/, 1)[0];

  return (
    pathOnly === UNIFIED_LOGIN_PATH ||
    pathOnly.startsWith(`${UNIFIED_LOGIN_PATH}/`) ||
    pathOnly === appPath('/login') ||
    pathOnly.startsWith(`${appPath('/login')}/`)
  );
}

function normalizeCloudPath(pathname: string) {
  const trimmed = pathname.trim();
  const cloudBasePath = appPath('/');

  if (!trimmed || !trimmed.startsWith('/') || trimmed.startsWith('//') || isLoginPath(trimmed)) {
    return DEFAULT_RETURN_TO;
  }

  if (trimmed === cloudBasePath.replace(/\/$/, '') || trimmed.startsWith(cloudBasePath)) {
    return trimmed;
  }

  return appPath(trimmed);
}

export function cloudReturnTo(pathname: string, search = '', hash = '') {
  return `${normalizeCloudPath(pathname)}${safeSuffix(search, '?')}${safeSuffix(hash, '#')}`;
}

export function buildUnifiedLoginUrl(returnTo = DEFAULT_RETURN_TO, reason?: LoginRedirectReason | null) {
  const safeReturnTo = normalizeReturnTo(returnTo);
  const safeReason = normalizeReason(reason);

  if (typeof window === 'undefined') {
    const reasonSuffix = safeReason ? `&reason=${encodeURIComponent(safeReason)}` : '';
    return `${UNIFIED_LOGIN_PATH}?returnTo=${encodeURIComponent(safeReturnTo)}${reasonSuffix}`;
  }

  const url = new URL(UNIFIED_LOGIN_PATH, window.location.origin);
  url.searchParams.set('returnTo', safeReturnTo);
  if (safeReason) {
    url.searchParams.set('reason', safeReason);
  }
  return url.toString();
}

export function redirectToUnifiedLogin(
  returnTo = DEFAULT_RETURN_TO,
  replace = true,
  reason?: LoginRedirectReason | null,
) {
  if (typeof window === 'undefined') {
    return;
  }

  const loginUrl = buildUnifiedLoginUrl(returnTo, reason);

  if (replace) {
    window.location.replace(loginUrl);
    return;
  }

  window.location.assign(loginUrl);
}
