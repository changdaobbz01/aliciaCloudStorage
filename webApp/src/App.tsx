import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './components/protected-route';
import { DrivePage } from './pages/DrivePage';
import { AppDownloadPage } from './pages/AppDownloadPage';
import { SharePage } from './pages/SharePage';
import { UnifiedLoginRedirectPage } from './pages/UnifiedLoginRedirectPage';

/**
 * 定义前端应用的路由入口。
 */
export default function RootApp() {
  return (
    <Routes>
      <Route path="/login" element={<UnifiedLoginRedirectPage />} />
      <Route path="/app-download" element={<AppDownloadPage />} />
      <Route path="/share/:shareCode" element={<SharePage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <DrivePage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
