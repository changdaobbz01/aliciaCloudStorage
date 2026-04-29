import { Result, Spin } from 'antd';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useSession } from '../context/session-context';

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
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
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
