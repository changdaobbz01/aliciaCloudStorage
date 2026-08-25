import type { User } from '../types';

const USER_STORAGE_KEY = 'alicia-cloud-storage.current-user';
const TOKEN_STORAGE_KEY = 'alicia-cloud-storage.auth-token';
const REFRESH_TOKEN_STORAGE_KEY = 'alicia-cloud-storage.refresh-token';
export const SESSION_REVISION_STORAGE_KEY = 'alicia-cloud-storage.session-revision';
export const SESSION_CHANGE_EVENT = 'alicia-cloud-storage:session-change';
const SESSION_STORAGE_KEYS = new Set([
  USER_STORAGE_KEY,
  TOKEN_STORAGE_KEY,
  REFRESH_TOKEN_STORAGE_KEY,
  SESSION_REVISION_STORAGE_KEY,
]);

type IdentityTokenSession = {
  token?: string | null;
  refreshToken?: string | null;
};

function normalizeToken(value: string | null | undefined) {
  const token = value?.trim();
  return token || null;
}

/**
 * 从本地存储中读取当前登录用户信息。
 */
export function loadCurrentUser() {
  const raw = localStorage.getItem(USER_STORAGE_KEY);

  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as User;
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY);
    return null;
  }
}

/**
 * 从本地存储中读取当前登录令牌。
 */
export function loadAuthToken() {
  return normalizeToken(localStorage.getItem(TOKEN_STORAGE_KEY));
}

/**
 * 从本地存储中读取当前刷新令牌。
 */
export function loadRefreshToken() {
  return normalizeToken(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY));
}

export function hasStoredSessionTokens() {
  return Boolean(loadAuthToken() || loadRefreshToken());
}

export function isSessionStorageKey(key: string | null) {
  return key !== null && SESSION_STORAGE_KEYS.has(key);
}

export function isSessionRevisionStorageKey(key: string | null) {
  return key === SESSION_REVISION_STORAGE_KEY;
}

export function notifySessionChanged(reason = 'session-updated') {
  if (typeof window === 'undefined') {
    return;
  }

  const revision = `${Date.now()}:${Math.random().toString(36).slice(2)}:${reason}`;
  localStorage.setItem(SESSION_REVISION_STORAGE_KEY, revision);
  window.dispatchEvent(
    new CustomEvent(SESSION_CHANGE_EVENT, {
      detail: {
        reason,
        revision,
      },
    }),
  );
}

/**
 * 单独更新本地保存的身份令牌。
 */
export function saveAuthToken(token: string) {
  const normalizedToken = normalizeToken(token);

  if (!normalizedToken) {
    clearCurrentSession();
    throw new Error('身份服务响应缺少 access token。');
  }

  localStorage.setItem(TOKEN_STORAGE_KEY, normalizedToken);
}

/**
 * 单独更新本地保存的刷新令牌。
 */
export function saveRefreshToken(refreshToken: string) {
  const normalizedRefreshToken = normalizeToken(refreshToken);

  if (!normalizedRefreshToken) {
    clearCurrentSession();
    throw new Error('身份服务响应缺少 refresh token。');
  }

  localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, normalizedRefreshToken);
}

export function saveIdentityTokenSession(session: IdentityTokenSession) {
  saveAuthToken(session.token ?? '');
  saveRefreshToken(session.refreshToken ?? '');
}

/**
 * 清空浏览器里保存的登录态信息。
 */
export function clearCurrentSession() {
  localStorage.removeItem(USER_STORAGE_KEY);
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
}

/**
 * 单独更新本地缓存中的用户资料。
 */
export function saveCurrentUser(user: User) {
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
}
