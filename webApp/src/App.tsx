import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './components/protected-route';
import { DrivePage } from './pages/DrivePage';
import { AppDownloadPage } from './pages/AppDownloadPage';
import { LoginPage } from './pages/LoginPage';
import { SharePage } from './pages/SharePage';

/**
 * 定义前端应用的路由入口。
 */
export default function RootApp() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
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
