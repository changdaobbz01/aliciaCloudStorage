import type { LoginResponse, User } from '../types';

const USER_STORAGE_KEY = 'alicia-cloud-storage.current-user';
const TOKEN_STORAGE_KEY = 'alicia-cloud-storage.auth-token';

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
 * 将完整的登录态写入浏览器本地存储。
 */
export function saveCurrentSession(session: LoginResponse) {
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(session.user));
  localStorage.setItem(TOKEN_STORAGE_KEY, session.token);
}

/**
 * 清空浏览器里保存的登录态信息。
 */
export function clearCurrentSession() {
  localStorage.removeItem(USER_STORAGE_KEY);
  localStorage.removeItem(TOKEN_STORAGE_KEY);
}

/**
 * 单独更新本地缓存中的用户资料。
 */
export function saveCurrentUser(user: User) {
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
}
