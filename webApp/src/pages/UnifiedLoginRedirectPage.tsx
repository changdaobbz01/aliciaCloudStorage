import { Spin } from 'antd';
import { useEffect } from 'react';
import { redirectToUnifiedLogin } from '../lib/unifiedLogin';

export function UnifiedLoginRedirectPage() {
  useEffect(() => {
    document.title = '统一登录 - Alicia 云盘';
    redirectToUnifiedLogin();
  }, []);

  return (
    <div className="route-pending">
      <Spin size="large" />
    </div>
  );
}
