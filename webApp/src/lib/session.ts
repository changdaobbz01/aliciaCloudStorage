import type { User } from '../types';

const USER_STORAGE_KEY = 'alicia-cloud-storage.current-user';
const TOKEN_STORAGE_KEY = 'alicia-cloud-storage.auth-token';
const REFRESH_TOKEN_STORAGE_KEY = 'alicia-cloud-storage.refresh-token';

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
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

/**
 * 从本地存储中读取当前刷新令牌。
 */
export function loadRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
}

/**
 * 单独更新本地保存的身份令牌。
 */
export function saveAuthToken(token: string) {
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

/**
 * 单独更新本地保存的刷新令牌。
 */
export function saveRefreshToken(refreshToken: string) {
  localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken);
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
