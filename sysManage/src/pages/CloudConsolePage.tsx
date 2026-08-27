import { App as AntApp, Avatar, Button, Dropdown, Layout, Menu, Result, Spin, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { ArrowUpRight, BarChart3, Cloud, Home, LayoutDashboard, LogOut, RefreshCw, Smartphone, UsersRound } from 'lucide-react';
import { Suspense, lazy, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Icon } from '../components/Icon';
import { LazyChunkErrorBoundary } from '../components/lazy-chunk-error-boundary';
import { DriveAppPackageUploadModal } from '../features/drive/DriveAppPackageUploadModal';
import { useCloudUsersAdmin } from '../features/drive/hooks/useCloudUsersAdmin';
import { useDriveAppPackageAdmin } from '../features/drive/hooks/useDriveAppPackageAdmin';
import { useDriveOperationsAdmin } from '../features/drive/hooks/useDriveOperationsAdmin';
import { resolveAvatarSrc } from '../features/drive/driveShared';
import { useSession } from '../context/session-context';
import { publicAssetPath } from '../lib/appPaths';
import { isCloudAdmin } from '../types';

const LazyCloudUsersView = lazy(() => import('../features/drive/CloudUsersView'));
const LazyDriveAppPackageView = lazy(() => import('../features/drive/DriveAppPackageView'));
const LazyDriveOperationsView = lazy(() => import('../features/drive/DriveOperationsView'));

const { Header, Sider, Content } = Layout;

type CloudConsoleView = 'users' | 'operations' | 'appPackage';

const menuItems: MenuProps['items'] = [
  { key: 'users', icon: <Icon icon={UsersRound} />, label: '用户额度' },
  { key: 'operations', icon: <Icon icon={BarChart3} />, label: '云盘运营' },
  { key: 'appPackage', icon: <Icon icon={Smartphone} />, label: 'APK 包管理' },
];

const viewMeta: Record<CloudConsoleView, { eyebrow: string; title: string; icon: typeof Cloud }> = {
  users: {
    eyebrow: 'Cloud Users',
    title: '用户额度',
    icon: UsersRound,
  },
  operations: {
    eyebrow: 'Cloud Operations',
    title: '云盘运营',
    icon: BarChart3,
  },
  appPackage: {
    eyebrow: 'Android Release',
    title: 'APK 包管理',
    icon: Smartphone,
  },
};

const routeByView: Record<CloudConsoleView, string> = {
  users: '/users',
  operations: '/operations',
  appPackage: '/app-package',
};

function viewFromRoute(value: string | undefined): CloudConsoleView {
  if (value === 'operations') {
    return 'operations';
  }

  if (value === 'app-package') {
    return 'appPackage';
  }

  return 'users';
}

export function CloudConsolePage() {
  const { message } = AntApp.useApp();
  const { authToken, currentUser, logoutCurrentSession, updateCurrentUser } = useSession();
  const navigate = useNavigate();
  const { view } = useParams<{ view?: string }>();
  const activeView = viewFromRoute(view);
  const isAdmin = isCloudAdmin(currentUser);
  const activeMeta = viewMeta[activeView];
  const currentAvatarSrc = resolveAvatarSrc(currentUser);
  const cloudUsers = useCloudUsersAdmin({
    authToken,
    currentUser,
    isAdmin,
    isUsersView: activeView === 'users',
    message,
    onCurrentUserUpdate: updateCurrentUser,
  });
  const operations = useDriveOperationsAdmin({
    authToken,
    isAdmin,
    isOperationsView: activeView === 'operations',
    message,
  });
  const appPackages = useDriveAppPackageAdmin({
    authToken,
    isAdmin,
    isAppPackageView: activeView === 'appPackage',
    message,
  });
  const viewLoadingFallback = (
    <div className="loading-box">
      <Spin size="large" />
    </div>
  );
  const avatarMenuItems: MenuProps['items'] = [
    { key: 'consoleHome', icon: <Icon icon={LayoutDashboard} />, label: '管理控制台' },
    { key: 'mainSite', icon: <Icon icon={Home} />, label: '返回主站' },
    { key: 'cloudPan', icon: <Icon icon={Cloud} />, label: '进入云盘' },
    { type: 'divider' },
    { key: 'logout', icon: <Icon icon={LogOut} />, label: '退出登录', danger: true },
  ];

  useEffect(() => {
    if (view && routeByView[activeView] !== `/${view}`) {
      navigate(routeByView[activeView], { replace: true });
    }
  }, [activeView, navigate, view]);

  async function refreshCurrentView() {
    if (activeView === 'users') {
      await cloudUsers.loadUsers();
      return;
    }

    if (activeView === 'operations') {
      await operations.loadAll();
      return;
    }

    await appPackages.loadAppPackageInfo();
  }

  function handleMenuClick(event: { key: string }) {
    navigate(routeByView[event.key as CloudConsoleView]);
  }

  async function handleAccountMenuClick(event: { key: string }) {
    if (event.key === 'consoleHome') {
      window.location.assign('/console/');
      return;
    }

    if (event.key === 'mainSite') {
      window.location.assign('/');
      return;
    }

    if (event.key === 'cloudPan') {
      window.location.assign('/cloudPan/');
      return;
    }

    if (event.key === 'logout') {
      await logoutCurrentSession();
      window.location.assign('/');
    }
  }

  const activeViewContent = !isAdmin ? (
    <section className="content-panel">
      <Result
        status="403"
        title="没有云盘后台权限"
        subTitle="只有云盘管理员可以访问运营后台。"
        extra={
          <Button type="primary" href="/cloudPan/">
            返回云盘
          </Button>
        }
      />
    </section>
  ) : (
    <LazyChunkErrorBoundary>
      <Suspense fallback={viewLoadingFallback}>
        {activeView === 'users' ? (
          <LazyCloudUsersView
            isAdmin={isAdmin}
            users={cloudUsers.users}
            loading={cloudUsers.usersLoading}
            quotaForm={cloudUsers.quotaForm}
            quotaTarget={cloudUsers.quotaTarget}
            quotaModalOpen={cloudUsers.quotaModalOpen}
            quotaSaving={cloudUsers.quotaSaving}
            onRefresh={() => void cloudUsers.loadUsers()}
            onOpenQuotaModal={cloudUsers.openQuotaModal}
            onCloseQuotaModal={cloudUsers.closeQuotaModal}
            onSubmitQuotaUpdate={cloudUsers.submitQuotaUpdate}
          />
        ) : null}

        {activeView === 'operations' ? (
          <LazyDriveOperationsView
            isAdmin={isAdmin}
            overview={operations.overview}
            overviewLoading={operations.overviewLoading}
            storageUsersPage={operations.storageUsersPage}
            storageUsersQuery={operations.storageUsersQuery}
            storageUsersLoading={operations.storageUsersLoading}
            trashNodesPage={operations.trashNodesPage}
            trashNodesQuery={operations.trashNodesQuery}
            trashNodesLoading={operations.trashNodesLoading}
            shareLinksPage={operations.shareLinksPage}
            shareLinksQuery={operations.shareLinksQuery}
            shareLinksLoading={operations.shareLinksLoading}
            onRefresh={() => void operations.loadAll()}
            onApplyStorageUsersQuery={operations.applyStorageUsersQuery}
            onStorageUsersPageChange={operations.changeStorageUsersPage}
            onApplyTrashNodesQuery={operations.applyTrashNodesQuery}
            onTrashNodesPageChange={operations.changeTrashNodesPage}
            onApplyShareLinksQuery={operations.applyShareLinksQuery}
            onShareLinksPageChange={operations.changeShareLinksPage}
          />
        ) : null}

        {activeView === 'appPackage' ? (
          <LazyDriveAppPackageView
            isAdmin={isAdmin}
            packageInfo={appPackages.appPackageInfo}
            loading={appPackages.appPackageLoading}
            uploading={appPackages.appPackageUploading}
            onUploadClick={appPackages.openAppPackageUploadModal}
            onDeletePackage={() => void appPackages.deleteCurrentAppPackage()}
          />
        ) : null}
      </Suspense>
    </LazyChunkErrorBoundary>
  );

  return (
    <Layout className="app-shell">
      <Sider width={276} className="app-sider">
        <div className="brand-block">
          <img src={publicAssetPath('/apple-touch-icon.png')} alt="" className="brand-icon brand-icon-image" />
          <div>
            <Typography.Title level={4}>Alicia 云盘后台</Typography.Title>
            <Typography.Text>运营与客户端分发</Typography.Text>
          </div>
        </div>

        <section className="sider-profile-card">
          <div className="sider-profile-top">
            <Avatar size={52} src={currentAvatarSrc}>
              {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'A'}
            </Avatar>
            <div className="sider-profile-copy">
              <Typography.Title level={5} className="sider-profile-name">
                {currentUser?.nickname ?? '未登录用户'}
              </Typography.Title>
              <div className="sider-profile-meta">
                <span className="sider-role-pill">{isAdmin ? '云盘管理员' : '无后台权限'}</span>
                <span>{currentUser?.email ?? currentUser?.phoneNumber}</span>
              </div>
            </div>
          </div>
        </section>

        <div className="sider-section-label">后台导航</div>
        <Menu
          mode="inline"
          selectedKeys={[activeView]}
          items={menuItems}
          className="sider-menu"
          onClick={handleMenuClick}
        />

        <section className="sider-download-card">
          <div className="sider-download-head">
            <span className="sider-download-icon console-link-icon">
              <Icon icon={Cloud} />
            </span>
            <div className="sider-download-copy">
              <Typography.Text className="sider-download-eyebrow">CloudPan</Typography.Text>
              <Typography.Title level={5} className="sider-download-title">
                普通云盘入口
              </Typography.Title>
            </div>
          </div>
          <Typography.Paragraph className="sider-download-note">
            文件浏览、上传、下载和个人分享已经留在普通用户端。
          </Typography.Paragraph>
          <a href="/cloudPan/" className="sider-download-link console-entry-link">
            进入云盘
            <Icon icon={ArrowUpRight} />
          </a>
        </section>
      </Sider>

      <Layout className="app-main-shell">
        <Header className="app-header">
          <div className="header-view">
            <Typography.Text className="header-eyebrow">{activeMeta.eyebrow}</Typography.Text>
            <div className="header-title">
              <Icon icon={activeMeta.icon} />
              <Typography.Text>{activeMeta.title}</Typography.Text>
            </div>
          </div>

          <div className="header-actions">
            <Button
              icon={<Icon icon={RefreshCw} />}
              onClick={() => void refreshCurrentView()}
              loading={
                activeView === 'users'
                  ? cloudUsers.usersLoading
                  : activeView === 'operations'
                  ? operations.overviewLoading ||
                    operations.storageUsersLoading ||
                    operations.trashNodesLoading ||
                    operations.shareLinksLoading
                  : appPackages.appPackageLoading
              }
            >
              刷新
            </Button>

            <Dropdown
              menu={{ items: avatarMenuItems, onClick: (event) => void handleAccountMenuClick(event) }}
              trigger={['click']}
              placement="bottomRight"
              overlayClassName="avatar-account-dropdown"
            >
              <button type="button" className="avatar-menu-button" aria-label="打开用户菜单">
                <Avatar size={44} src={currentAvatarSrc}>
                  {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'A'}
                </Avatar>
              </button>
            </Dropdown>
          </div>
        </Header>

        <Content className="app-content">{activeViewContent}</Content>
      </Layout>

      <DriveAppPackageUploadModal
        open={appPackages.appPackageUploadOpen}
        uploading={appPackages.appPackageUploading}
        selectedFile={appPackages.selectedAppPackageFile}
        form={appPackages.appPackageUploadForm}
        inputRef={appPackages.appPackageInputRef}
        onClose={appPackages.closeAppPackageUploadModal}
        onSubmit={appPackages.submitAppPackageUpload}
        onPickFile={appPackages.handleAppPackageFilePickerClick}
        onFileChange={appPackages.handleAppPackageFileChange}
      />
    </Layout>
  );
}
