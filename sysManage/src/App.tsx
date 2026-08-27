import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { ProtectedRoute } from './components/protected-route';
import { CloudConsolePage } from './pages/CloudConsolePage';

const defaultCloudViewRoutes = new Map([
  ['users', '/users'],
  ['operations', '/operations'],
  ['app-package', '/app-package'],
  ['appPackage', '/app-package'],
]);

function defaultCloudViewRoute(search: string) {
  const view = new URLSearchParams(search).get('view') ?? '';

  return defaultCloudViewRoutes.get(view) ?? '/users';
}

function DefaultCloudConsoleRoute() {
  const location = useLocation();

  return <Navigate to={`${defaultCloudViewRoute(location.search)}${location.search}${location.hash}`} replace />;
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
