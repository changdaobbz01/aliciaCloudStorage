import type { User } from '../types';

const USER_STORAGE_KEY = 'alicia-cloud-storage.current-user';
const TOKEN_STORAGE_KEY = 'alicia-cloud-storage.auth-token';
const REFRESH_TOKEN_STORAGE_KEY = 'alicia-cloud-storage.refresh-token';

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
