import { Button, Result, Spin } from 'antd';
import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useSession } from '../context/session-context';
import { cloudConsoleReturnTo, redirectToUnifiedLogin } from '../lib/unifiedLogin';
import type { LoginRedirectReason } from '../lib/unifiedLogin';
import { isCloudAdmin } from '../types';

function UnifiedLoginRedirect({ returnTo, reason }: { returnTo: string; reason: LoginRedirectReason | null }) {
  useEffect(() => {
    redirectToUnifiedLogin(returnTo, true, reason);
  }, [reason, returnTo]);

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
  const { authToken, currentUser, isSessionChecking, loginRedirectReason } = useSession();

  if (isSessionChecking) {
    return (
      <div className="route-pending">
        <Spin size="large" />
      </div>
    );
  }

  if (!authToken || !currentUser) {
    return (
      <UnifiedLoginRedirect
        returnTo={cloudConsoleReturnTo(location.pathname, location.search, location.hash)}
        reason={loginRedirectReason}
      />
    );
  }

  if (currentUser.status !== 'ACTIVE') {
    return (
      <div className="route-pending">
        <Result status="403" title="账号已停用" subTitle="请联系管理员处理。" />
      </div>
    );
  }

  if (!isCloudAdmin(currentUser)) {
    return (
      <div className="route-pending">
        <Result
          status="403"
          title="没有云盘后台权限"
          subTitle="仅全局管理员或云盘管理员可以访问运营后台。"
          extra={[
            <Button key="console" href="/console/">管理控制台</Button>,
            <Button key="cloud" type="primary" href="/cloudPan/">返回云盘</Button>,
            <Button key="home" href="/">返回主站</Button>,
          ]}
        />
      </div>
    );
  }

  return <>{children}</>;
}
