import { App as AntApp } from 'antd';
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { AUTH_EXPIRED_EVENT, fetchCurrentUser, logoutAuthToken, refreshAuthSession } from '../lib/api';
import {
  clearCurrentSession as clearStoredSession,
  hasStoredSessionTokens,
  loadAuthToken,
  loadCurrentUser,
  loadRefreshToken,
  saveCurrentUser,
  saveIdentityTokenSession,
} from '../lib/session';
import { cloudReturnTo, redirectToUnifiedLogin } from '../lib/unifiedLogin';
import type { User } from '../types';

const TOKEN_REFRESH_INTERVAL_MS = 30 * 60 * 1000;

type SessionContextValue = {
  currentUser: User | null;
  authToken: string | null;
  isSessionChecking: boolean;
  clearCurrentSession: () => void;
  logoutCurrentSession: () => Promise<void>;
  updateCurrentUser: (user: User) => void;
};

const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * 提供全局会话状态，统一管理登录态、资料刷新和令牌过期处理。
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const { message } = AntApp.useApp();
  const location = useLocation();
  const lastAuthExpiredMessageAt = useRef(0);
  const [currentUser, setCurrentUser] = useState<User | null>(() =>
    loadAuthToken() && loadRefreshToken() ? loadCurrentUser() : null,
  );
  const [authToken, setAuthToken] = useState<string | null>(() => loadAuthToken());
  const [isSessionChecking, setIsSessionChecking] = useState(() => hasStoredSessionTokens());

  useEffect(() => {
    /**
     * 监听全局鉴权过期事件，并将用户带回登录页。
     */
    function handleAuthExpired(event: Event) {
      resetSessionState();

      const now = Date.now();
      if (now - lastAuthExpiredMessageAt.current > 1500) {
        const detail = (event as CustomEvent<{ message?: string }>).detail;
        message.warning(detail?.message || '登录状态已失效，请重新登录。');
        lastAuthExpiredMessageAt.current = now;
      }

      redirectToUnifiedLogin(cloudReturnTo(location.pathname, location.search, location.hash));
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);

    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    };
  }, [location.hash, location.pathname, location.search, message]);

  useEffect(() => {
    const token = loadAuthToken();
    const refreshToken = loadRefreshToken();

    if (!token && !refreshToken) {
      resetSessionState();
      setIsSessionChecking(false);
      return;
    }

    if (!token || !refreshToken) {
      resetSessionState();
      setIsSessionChecking(false);
      return;
    }

    const storedToken = token;
    const storedRefreshToken = refreshToken;
    let cancelled = false;

    /**
     * 在页面刷新后先向 Identity 续签，再用新令牌确认云盘资料。
     */
    async function verifyStoredToken() {
      try {
        const refreshedSession = await refreshAuthSession(storedToken, storedRefreshToken);
        const user = await fetchCurrentUser(refreshedSession.token);

        if (!cancelled) {
          saveIdentityTokenSession(refreshedSession);
          saveCurrentUser(user);
          setCurrentUser(user);
          setAuthToken(refreshedSession.token);
        }
      } catch {
        if (!cancelled) {
          resetSessionState();
        }
      } finally {
        if (!cancelled) {
          setIsSessionChecking(false);
        }
      }
    }

    void verifyStoredToken();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!authToken) {
      return;
    }

    let cancelled = false;

    async function refreshStoredToken() {
      const storedToken = loadAuthToken();
      const storedRefreshToken = loadRefreshToken();

      if (!storedToken || !storedRefreshToken) {
        resetSessionState();
        return;
      }

      try {
        const refreshedSession = await refreshAuthSession(storedToken, storedRefreshToken);

        if (!cancelled && loadAuthToken()) {
          saveIdentityTokenSession(refreshedSession);
          setAuthToken(refreshedSession.token);
        }
      } catch {
        if (!cancelled && !loadAuthToken()) {
          resetSessionState();
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
  }

  /**
   * 清空当前会话在内存和本地缓存中的全部状态。
   */
  function resetSessionState() {
    clearStoredSession();
    setCurrentUser(null);
    setAuthToken(null);
    setIsSessionChecking(false);
  }

  /**
   * 主动退出登录时调用，复用统一的会话清理逻辑。
   */
  function clearCurrentSession() {
    resetSessionState();
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
  }

  return (
    <SessionContext.Provider
      value={{
        currentUser,
        authToken,
        isSessionChecking,
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
