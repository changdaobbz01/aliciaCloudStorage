import { Spin } from 'antd';
import { useEffect } from 'react';
import { redirectToUnifiedLogin } from '../lib/unifiedLogin';

export function UnifiedLoginRedirectPage() {
  useEffect(() => {
    redirectToUnifiedLogin();
  }, []);

  return (
    <div className="route-pending">
      <Spin size="large" />
    </div>
  );
}
