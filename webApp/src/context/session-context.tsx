import { App as AntApp } from 'antd';
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { AUTH_EXPIRED_EVENT, fetchCurrentUser } from '../lib/api';
import {
  clearCurrentSession as clearStoredSession,
  loadAuthToken,
  loadCurrentUser,
  saveCurrentSession,
  saveCurrentUser,
} from '../lib/session';
import type { LoginResponse, User } from '../types';

type SessionContextValue = {
  currentUser: User | null;
  authToken: string | null;
  isSessionChecking: boolean;
  setCurrentSession: (session: LoginResponse) => void;
  clearCurrentSession: () => void;
  updateCurrentUser: (user: User) => void;
};

const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * 提供全局会话状态，统一管理登录态、资料刷新和令牌过期处理。
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const { message } = AntApp.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const lastAuthExpiredMessageAt = useRef(0);
  const [currentUser, setCurrentUser] = useState<User | null>(() =>
    loadAuthToken() ? loadCurrentUser() : null,
  );
  const [authToken, setAuthToken] = useState<string | null>(() => loadAuthToken());
  const [isSessionChecking, setIsSessionChecking] = useState(() => Boolean(loadAuthToken()));

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

      if (location.pathname !== '/login') {
        void navigate('/login', { replace: true });
      }
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);

    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    };
  }, [location.pathname, message, navigate]);

  useEffect(() => {
    const token = loadAuthToken();

    if (!token) {
      resetSessionState();
      setIsSessionChecking(false);
      return;
    }

    const verifiedToken = token;
    let cancelled = false;

    /**
     * 在页面刷新后使用本地令牌重新向后端确认当前登录态。
     */
    async function verifyStoredToken() {
      try {
        const user = await fetchCurrentUser(verifiedToken);

        if (!cancelled) {
          setCurrentUser(user);
          setAuthToken(verifiedToken);
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

  /**
   * 保存一组新的登录态到内存和本地缓存中。
   */
  function setCurrentSession(session: LoginResponse) {
    saveCurrentSession(session);
    setCurrentUser(session.user);
    setAuthToken(session.token);
    setIsSessionChecking(false);
  }

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

  return (
    <SessionContext.Provider
      value={{
        currentUser,
        authToken,
        isSessionChecking,
        setCurrentSession,
        clearCurrentSession,
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
