import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { ProtectedRoute } from './components/protected-route';
import { CloudConsolePage } from './pages/CloudConsolePage';

function DefaultCloudConsoleRoute() {
  const location = useLocation();

  return <Navigate to={`/users${location.search}${location.hash}`} replace />;
}

/**
 * 定义前端应用的路由入口。
 */
export default function RootApp() {
  return (
    <Routes>
      <Route
        path="/:view"
        element={
          <ProtectedRoute>
            <CloudConsolePage />
          </ProtectedRoute>
        }
      />
      <Route path="/" element={<DefaultCloudConsoleRoute />} />
      <Route path="*" element={<Navigate to="/users" replace />} />
    </Routes>
  );
}
