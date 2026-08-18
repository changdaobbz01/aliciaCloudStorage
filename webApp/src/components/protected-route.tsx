import { Result, Spin } from 'antd';
import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useSession } from '../context/session-context';
import { cloudReturnTo, redirectToUnifiedLogin } from '../lib/unifiedLogin';

function UnifiedLoginRedirect({ returnTo }: { returnTo: string }) {
  useEffect(() => {
    redirectToUnifiedLogin(returnTo);
  }, [returnTo]);

  return (
    <div className="route-pending">
      <Spin size="large" />
    </div>
  );
}

/**
 * 在进入受保护页面前校验登录态和账号状态。
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { authToken, currentUser, isSessionChecking } = useSession();

  if (isSessionChecking) {
    return (
      <div className="route-pending">
        <Spin size="large" />
      </div>
    );
  }

  if (!authToken || !currentUser) {
    return <UnifiedLoginRedirect returnTo={cloudReturnTo(location.pathname, location.search, location.hash)} />;
  }

  if (currentUser.status !== 'ACTIVE') {
    return (
      <div className="route-pending">
        <Result status="403" title="账号已停用" subTitle="请联系管理员处理。" />
      </div>
    );
  }

  return <>{children}</>;
}
