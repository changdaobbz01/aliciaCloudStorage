import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { AUTH_EXPIRED_EVENT, fetchCurrentUser, isApiError, logoutAuthToken, refreshAuthSession } from '../lib/api';
import {
  clearCurrentSession as clearStoredSession,
  hasStoredSessionChanged,
  hasStoredSessionTokens,
  isSessionWriteLocked,
  isSessionRevisionStorageKey,
  isSessionStorageKey,
  loadAuthToken,
  loadCurrentUser,
  loadRefreshToken,
  notifySessionChanged,
  readStoredSessionSnapshot,
  runAfterSessionWriteSettled,
  saveCurrentUser,
  saveIdentityTokenSession,
  SESSION_CHANGE_EVENT,
  SESSION_REVISION_STORAGE_KEY,
} from '../lib/session';
import { cloudConsoleReturnTo, redirectToUnifiedLogin, type LoginRedirectReason } from '../lib/unifiedLogin';
import type { User } from '../types';

const TOKEN_REFRESH_INTERVAL_MS = 30 * 60 * 1000;

type SessionContextValue = {
  currentUser: User | null;
  authToken: string | null;
  isSessionChecking: boolean;
  loginRedirectReason: LoginRedirectReason | null;
  clearCurrentSession: () => void;
  logoutCurrentSession: () => Promise<void>;
  updateCurrentUser: (user: User) => void;
};

const SessionContext = createContext<SessionContextValue | null>(null);

function toLoginRedirectReason(reason: string | null | undefined): LoginRedirectReason | null {
  return reason === 'expired' ? 'session-expired' : null;
}

function parseSessionChangeReason(revision: string | null | undefined) {
  const parts = revision?.split(':') ?? [];
  return parts.length >= 3 ? parts[2] : null;
}

function isAuthenticationSessionError(error: unknown) {
  return isApiError(error) && error.status === 401;
}

function toCachedCloudUser(sessionUser: { id: number; phoneNumber: string | null; email: string | null; nickname: string; avatarUrl: string | null; role: User['role']; status: User['status']; createdAt: string; appRoles: User['appRoles'] }): User {
  return {
    id: sessionUser.id,
    phoneNumber: sessionUser.phoneNumber ?? '',
    email: sessionUser.email,
    nickname: sessionUser.nickname,
    avatarUrl: sessionUser.avatarUrl,
    homeBackgroundUrl: null,
    role: sessionUser.role,
    status: sessionUser.status,
    createdAt: sessionUser.createdAt,
    storageQuotaBytes: null,
    usedBytes: 0,
    remainingBytes: null,
    appRoles: sessionUser.appRoles,
  };
}

/**
 * 提供全局会话状态，统一管理登录态、资料刷新和令牌过期处理。
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const loginRedirectingRef = useRef(false);
  const [currentUser, setCurrentUser] = useState<User | null>(() =>
    loadAuthToken() && loadRefreshToken() ? loadCurrentUser() : null,
  );
  const [authToken, setAuthToken] = useState<string | null>(() => loadAuthToken());
  const [isSessionChecking, setIsSessionChecking] = useState(() => hasStoredSessionTokens());
  const [loginRedirectReason, setLoginRedirectReason] = useState<LoginRedirectReason | null>(null);
  const authTokenRef = useRef(authToken);

  useEffect(() => {
    authTokenRef.current = authToken;
  }, [authToken]);

  async function restoreStoredSession(isCancelled: () => boolean = () => false) {
    if (isSessionWriteLocked()) {
      setIsSessionChecking(true);
      runAfterSessionWriteSettled(() => {
        if (!isCancelled()) {
          void restoreStoredSession(isCancelled);
        }
      });
      return;
    }

    const snapshot = readStoredSessionSnapshot();
    const token = snapshot.token;
    const refreshToken = snapshot.refreshToken;
    const cachedUser = loadCurrentUser();

    if (cachedUser) {
      setCurrentUser(cachedUser);
    }

    if (!token && !refreshToken) {
      resetSessionState();
      return;
    }

    if (!token || !refreshToken) {
      if (!hasStoredSessionChanged(snapshot)) {
        expireCurrentSession();
      }
      return;
    }

    setIsSessionChecking(true);

    try {
      const refreshedSession = await refreshAuthSession(token, refreshToken);
      let user = cachedUser ?? toCachedCloudUser(refreshedSession.user);

      try {
        user = await fetchCurrentUser(refreshedSession.token);
      } catch (error) {
        if (isAuthenticationSessionError(error)) {
          throw error;
        }
      }

      if (isCancelled()) {
        return;
      }

      if (hasStoredSessionChanged(snapshot)) {
        return;
      }

      saveIdentityTokenSession(refreshedSession);
      saveCurrentUser(user);
      setCurrentUser(user);
      setAuthToken(refreshedSession.token);
      setLoginRedirectReason(null);
    } catch (error) {
      if (!isCancelled() && !hasStoredSessionChanged(snapshot) && isAuthenticationSessionError(error)) {
        expireCurrentSession();
      }
    } finally {
      if (!isCancelled()) {
        setIsSessionChecking(false);
      }
    }
  }

  useEffect(() => {
    /**
     * 监听全局鉴权过期事件，并将用户带回登录页。
     */
    let cancelled = false;

    function redirectExpiredSession() {
      if (loginRedirectingRef.current) {
        return;
      }

      loginRedirectingRef.current = true;
      redirectToUnifiedLogin(
        cloudConsoleReturnTo(location.pathname, location.search, location.hash),
        true,
        'session-expired',
      );
    }

    async function confirmCurrentSessionExpired() {
      if (isSessionWriteLocked()) {
        runAfterSessionWriteSettled(() => {
          if (!cancelled) {
            void confirmCurrentSessionExpired();
          }
        });
        return;
      }

      const snapshot = readStoredSessionSnapshot();
      const token = snapshot.token;
      const refreshToken = snapshot.refreshToken;

      if (!token && !refreshToken) {
        resetSessionState();
        return;
      }

      if (!token || !refreshToken) {
        if (!cancelled && !hasStoredSessionChanged(snapshot)) {
          expireCurrentSession();
          redirectExpiredSession();
        }
        return;
      }

      setIsSessionChecking(true);

      try {
        const refreshedSession = await refreshAuthSession(token, refreshToken);

        if (cancelled || hasStoredSessionChanged(snapshot)) {
          return;
        }

        const user = toCachedCloudUser(refreshedSession.user);
        saveIdentityTokenSession(refreshedSession);
        saveCurrentUser(user);
        setCurrentUser(user);
        setAuthToken(refreshedSession.token);
        setLoginRedirectReason(null);
      } catch (error) {
        if (!cancelled && !hasStoredSessionChanged(snapshot) && isAuthenticationSessionError(error)) {
          expireCurrentSession();
          redirectExpiredSession();
        }
      } finally {
        if (!cancelled) {
          setIsSessionChecking(false);
        }
      }
    }

    function handleAuthExpired() {
      void confirmCurrentSessionExpired();
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);

    return () => {
      cancelled = true;
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    };
  }, [location.hash, location.pathname, location.search]);

  useEffect(() => {
    let cancelled = false;

    function handlePageShow() {
      void restoreStoredSession(() => cancelled);
    }

    void restoreStoredSession(() => cancelled);
    window.addEventListener('pageshow', handlePageShow);

    return () => {
      cancelled = true;
      window.removeEventListener('pageshow', handlePageShow);
    };
  }, []);

  useEffect(() => {
    function refreshCurrentUserFromToken(token: string) {
      void fetchCurrentUser(token)
        .then((user) => {
          saveCurrentUser(user);
          setCurrentUser(user);
        })
        .catch(() => undefined);
    }

    function handleSessionChange(key: string | null, reason?: string | null) {
      if (!isSessionStorageKey(key)) {
        return;
      }

      runAfterSessionWriteSettled(() => {
        const token = loadAuthToken();
        const refreshToken = loadRefreshToken();

        if (!token || !refreshToken) {
          resetSessionState(toLoginRedirectReason(reason));
          return;
        }

        const cachedUser = loadCurrentUser();
        if (cachedUser) {
          setCurrentUser(cachedUser);
        }

        if (isSessionRevisionStorageKey(key)) {
          setAuthToken(token);
          refreshCurrentUserFromToken(token);
          return;
        }

        if (!authTokenRef.current) {
          void restoreStoredSession();
          return;
        }

        if (token !== authTokenRef.current) {
          setAuthToken(token);
          if (!cachedUser) {
            refreshCurrentUserFromToken(token);
          }
        }
      });
    }

    function handleSessionStorageChange(event: StorageEvent) {
      handleSessionChange(event.key, parseSessionChangeReason(event.newValue));
    }

    function handleLocalSessionChange(event: Event) {
      const reason = (event as CustomEvent<{ reason?: string }>).detail?.reason;
      handleSessionChange(SESSION_REVISION_STORAGE_KEY, reason);
    }

    window.addEventListener('storage', handleSessionStorageChange);
    window.addEventListener(SESSION_CHANGE_EVENT, handleLocalSessionChange);

    return () => {
      window.removeEventListener('storage', handleSessionStorageChange);
      window.removeEventListener(SESSION_CHANGE_EVENT, handleLocalSessionChange);
    };
  }, []);

  useEffect(() => {
    if (!authToken) {
      return;
    }

    let cancelled = false;

    async function refreshStoredToken() {
      if (isSessionWriteLocked()) {
        runAfterSessionWriteSettled(() => {
          if (!cancelled) {
            void refreshStoredToken();
          }
        });
        return;
      }

      const snapshot = readStoredSessionSnapshot();
      const storedToken = snapshot.token;
      const storedRefreshToken = snapshot.refreshToken;

      if (!storedToken || !storedRefreshToken) {
        if (!hasStoredSessionChanged(snapshot)) {
          resetSessionState();
        }
        return;
      }

      try {
        const refreshedSession = await refreshAuthSession(storedToken, storedRefreshToken);

        if (!cancelled && !hasStoredSessionChanged(snapshot)) {
          saveIdentityTokenSession(refreshedSession);
          if (!loadCurrentUser()) {
            saveCurrentUser(toCachedCloudUser(refreshedSession.user));
          }
          setAuthToken(refreshedSession.token);
          setLoginRedirectReason(null);
        }
      } catch (error) {
        if (!cancelled && !hasStoredSessionChanged(snapshot) && isAuthenticationSessionError(error)) {
          expireCurrentSession();
        }
      }
    }

    const intervalId = window.setInterval(() => {
      void refreshStoredToken();
    }, TOKEN_REFRESH_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [authToken]);

  /**
   * 在用户修改资料后同步更新本地缓存中的用户信息。
   */
  function updateCurrentUser(user: User) {
    saveCurrentUser(user);
    setCurrentUser(user);
    notifySessionChanged('profile');
  }

  /**
   * 清空当前会话在内存和本地缓存中的全部状态。
   */
  function resetSessionState(reason: LoginRedirectReason | null = null) {
    clearStoredSession();
    setCurrentUser(null);
    setAuthToken(null);
    setIsSessionChecking(false);
    setLoginRedirectReason(reason);
  }

  function expireCurrentSession() {
    resetSessionState('session-expired');
    notifySessionChanged('expired');
  }

  /**
   * 主动退出登录时调用，复用统一的会话清理逻辑。
   */
  function clearCurrentSession() {
    resetSessionState();
    notifySessionChanged('logout');
  }

  async function logoutCurrentSession() {
    const token = loadAuthToken();
    const refreshToken = loadRefreshToken();

    if (token) {
      try {
        await logoutAuthToken(token, refreshToken);
      } catch {
        // 本地退出不依赖服务端响应；过期或网络失败时仍清掉本地会话。
      }
    }

    resetSessionState();
    notifySessionChanged('logout');
  }

  return (
    <SessionContext.Provider
      value={{
        currentUser,
        authToken,
        isSessionChecking,
        loginRedirectReason,
        clearCurrentSession,
        logoutCurrentSession,
        updateCurrentUser,
      }}
    >
      {children}
    </SessionContext.Provider>
  );
}

/**
 * 读取全局会话上下文，供页面组件共享当前登录态。
 */
export function useSession() {
  const context = useContext(SessionContext);

  if (!context) {
    throw new Error('useSession 必须在 SessionProvider 内部使用。');
  }

  return context;
}
