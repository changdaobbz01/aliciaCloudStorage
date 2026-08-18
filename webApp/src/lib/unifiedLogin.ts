import { appPath } from './appPaths';

const UNIFIED_LOGIN_PATH = '/login';
const DEFAULT_RETURN_TO = appPath('/');

function normalizeReturnTo(returnTo: string) {
  const trimmed = returnTo.trim();

  if (!trimmed || !trimmed.startsWith('/') || trimmed.startsWith('//') || trimmed.startsWith(UNIFIED_LOGIN_PATH)) {
    return DEFAULT_RETURN_TO;
  }

  return trimmed;
}

export function cloudReturnTo(pathname: string, search = '', hash = '') {
  return appPath(`${pathname}${search}${hash}`);
}

export function buildUnifiedLoginUrl(returnTo = DEFAULT_RETURN_TO) {
  const safeReturnTo = normalizeReturnTo(returnTo);

  if (typeof window === 'undefined') {
    return `${UNIFIED_LOGIN_PATH}?returnTo=${encodeURIComponent(safeReturnTo)}`;
  }

  const url = new URL(UNIFIED_LOGIN_PATH, window.location.origin);
  url.searchParams.set('returnTo', safeReturnTo);
  return url.toString();
}

export function redirectToUnifiedLogin(returnTo = DEFAULT_RETURN_TO, replace = true) {
  if (typeof window === 'undefined') {
    return;
  }

  const loginUrl = buildUnifiedLoginUrl(returnTo);

  if (replace) {
    window.location.replace(loginUrl);
    return;
  }

  window.location.assign(loginUrl);
}
