import { App as AntApp, Avatar, Button, Dropdown, Form, Input, Layout, Menu, Modal, Result, Space, Spin, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { ArrowUpRight, BarChart3, Cloud, Home, LayoutDashboard, LogOut, RefreshCw, Smartphone, Upload, UserCog, UsersRound } from 'lucide-react';
import { Suspense, lazy, useEffect, useRef, useState, type ChangeEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AliciaModalTitle } from '../components/AliciaModalTitle';
import { Icon } from '../components/Icon';
import { LazyChunkErrorBoundary } from '../components/lazy-chunk-error-boundary';
import { DriveAppPackageUploadModal } from '../features/drive/DriveAppPackageUploadModal';
import { useCloudUsersAdmin } from '../features/drive/hooks/useCloudUsersAdmin';
import { useDriveAppPackageAdmin } from '../features/drive/hooks/useDriveAppPackageAdmin';
import { useDriveOperationsAdmin } from '../features/drive/hooks/useDriveOperationsAdmin';
import { resolveAvatarSrc } from '../features/drive/driveShared';
import { useSession } from '../context/session-context';
import { publicAssetPath } from '../lib/appPaths';
import { updateProfile, uploadCurrentUserAvatar } from '../lib/api';
import { cloudRoleLabel, isCloudAdmin, type UpdateProfilePayload, type User } from '../types';

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

function normalizeOptionalText(value: string | null | undefined) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

function resolveProfileAvatarSrc(user: User | null, avatarUrl: string | null | undefined) {
  const trimmed = avatarUrl?.trim();

  if (!trimmed) {
    return undefined;
  }

  if (trimmed.startsWith('cos:')) {
    return user ? `/api/cloud-profile/avatar/${user.id}?v=${encodeURIComponent(trimmed)}` : undefined;
  }

  return trimmed;
}

export function CloudConsolePage() {
  const { message } = AntApp.useApp();
  const { authToken, currentUser, logoutCurrentSession, updateCurrentUser } = useSession();
  const navigate = useNavigate();
  const { view } = useParams<{ view?: string }>();
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [profileForm] = Form.useForm<UpdateProfilePayload>();
  const avatarInputRef = useRef<HTMLInputElement | null>(null);
  const profileSavingRef = useRef(false);
  const avatarUploadingRef = useRef(false);
  const activeView = viewFromRoute(view);
  const isAdmin = isCloudAdmin(currentUser);
  const activeMeta = viewMeta[activeView];
  const currentAvatarSrc = resolveAvatarSrc(currentUser);
  const watchedAvatarUrl = Form.useWatch('avatarUrl', profileForm);
  const profileAvatarSrc =
    watchedAvatarUrl === undefined ? currentAvatarSrc : resolveProfileAvatarSrc(currentUser, watchedAvatarUrl);
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
    { key: 'profile', icon: <Icon icon={UserCog} />, label: '个人资料' },
    { type: 'divider' },
    { key: 'consoleHome', icon: <Icon icon={LayoutDashboard} />, label: '管理控制台' },
    { key: 'mainSite', icon: <Icon icon={Home} />, label: '返回主站' },
    { key: 'cloudPan', icon: <Icon icon={Cloud} />, label: '进入云盘' },
    { type: 'divider' },
    { key: 'logout', icon: <Icon icon={LogOut} />, label: '退出登录', danger: true },
  ];

  useEffect(() => {
    document.title = `${activeMeta.title} - Alicia 云盘后台`;
  }, [activeMeta.title]);

  useEffect(() => {
    if (view && routeByView[activeView] !== `/${view}`) {
      navigate(routeByView[activeView], { replace: true });
    }
  }, [activeView, navigate, view]);

  async function refreshCurrentView() {
    if (!isAdmin) {
      return;
    }

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

  function openProfileModal() {
    if (!currentUser) {
      return;
    }

    profileForm.setFieldsValue({
      nickname: currentUser.nickname,
      phoneNumber: currentUser.phoneNumber || '',
      avatarUrl: currentUser.avatarUrl ?? '',
    });
    setProfileOpen(true);
  }

  function closeProfileModal() {
    if (profileSavingRef.current || avatarUploadingRef.current) {
      return;
    }

    setProfileOpen(false);
  }

  function handleAvatarButtonClick() {
    if (profileSavingRef.current || avatarUploadingRef.current) {
      return;
    }

    avatarInputRef.current?.click();
  }

  async function handleAvatarFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken || profileSavingRef.current || avatarUploadingRef.current) {
      event.target.value = '';
      return;
    }

    const selectedFile = event.target.files?.[0] ?? null;
    event.target.value = '';

    if (!selectedFile) {
      return;
    }

    if (!selectedFile.type.startsWith('image/')) {
      message.error('请选择图片文件作为头像。');
      return;
    }

    avatarUploadingRef.current = true;
    setAvatarUploading(true);

    try {
      const updatedUser = await uploadCurrentUserAvatar(selectedFile, authToken);
      updateCurrentUser(updatedUser);
      profileForm.setFieldsValue({ avatarUrl: updatedUser.avatarUrl ?? '' });
      message.success('头像已更新。');
    } catch (avatarError) {
      message.error(avatarError instanceof Error ? avatarError.message : '头像上传失败。');
    } finally {
      avatarUploadingRef.current = false;
      setAvatarUploading(false);
    }
  }

  async function submitProfile(values: UpdateProfilePayload) {
    if (!authToken || profileSavingRef.current) {
      return false;
    }

    const nickname = values.nickname.trim();

    if (!nickname) {
      message.error('请输入昵称。');
      return false;
    }

    profileSavingRef.current = true;
    setProfileSaving(true);

    try {
      const updatedUser = await updateProfile(
        {
          nickname,
          phoneNumber: normalizeOptionalText(values.phoneNumber),
          avatarUrl: normalizeOptionalText(values.avatarUrl),
        },
        authToken,
      );
      updateCurrentUser(updatedUser);
      setProfileOpen(false);
      message.success('个人资料已更新。');
      return true;
    } catch (profileError) {
      message.error(profileError instanceof Error ? profileError.message : '个人资料保存失败。');
      return false;
    } finally {
      profileSavingRef.current = false;
      setProfileSaving(false);
    }
  }

  async function handleAccountMenuClick(event: { key: string }) {
    if (event.key === 'profile') {
      openProfileModal();
      return;
    }

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
        subTitle="仅全局管理员或云盘管理员可以访问运营后台。"
        extra={
          <Space>
            <Button href="/console/">管理控制台</Button>
            <Button type="primary" href="/cloudPan/">
              返回云盘
            </Button>
            <Button href="/">返回主站</Button>
          </Space>
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
            deleting={appPackages.appPackageDeleting}
            onUploadClick={appPackages.openAppPackageUploadModal}
            onDeletePackage={appPackages.deleteCurrentAppPackage}
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
                <span className="sider-role-pill">{cloudRoleLabel(currentUser)}</span>
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
                  : appPackages.appPackageLoading || appPackages.appPackageUploading || appPackages.appPackageDeleting
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

      <Modal
        title={<AliciaModalTitle eyebrow="Account">个人资料</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-account-modal account-profile-modal"
        open={profileOpen}
        onCancel={closeProfileModal}
        onOk={() => void profileForm.submit()}
        okText="保存资料"
        cancelText="取消"
        confirmLoading={profileSaving}
        maskClosable={!profileSaving && !avatarUploading}
        closable={!profileSaving && !avatarUploading}
        cancelButtonProps={{ disabled: profileSaving || avatarUploading }}
        destroyOnHidden
      >
        <Form
          form={profileForm}
          layout="vertical"
          disabled={profileSaving}
          className="account-profile-form"
          onFinish={(values) => void submitProfile(values)}
        >
          <div className="profile-avatar-row account-profile-hero">
            <Avatar size={64} src={profileAvatarSrc} className="account-profile-avatar-frame">
              {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'A'}
            </Avatar>
            <div className="account-profile-copy">
              <strong>用户图标</strong>
              <small>上传本地图片或填写图片地址后，会同步更新所有 Alicia 账号入口。</small>
              <div className="account-profile-actions">
                <Button
                  icon={<Icon icon={Upload} />}
                  loading={avatarUploading}
                  disabled={profileSaving}
                  onClick={handleAvatarButtonClick}
                >
                  上传图片
                </Button>
              </div>
              <input
                ref={avatarInputRef}
                type="file"
                accept="image/png,image/jpeg,image/gif,image/webp"
                className="upload-input"
                onChange={(event) => void handleAvatarFileChange(event)}
              />
            </div>
          </div>

          <div className="account-profile-fields">
            <Form.Item name="nickname" label="昵称" rules={[{ required: true, message: '请输入昵称。' }]}>
              <Input maxLength={100} placeholder="请输入昵称" />
            </Form.Item>
            <Form.Item name="phoneNumber" label="手机号" rules={[{ pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' }]}>
              <Input placeholder="可选，绑定后也可用于登录" />
            </Form.Item>
            <Form.Item name="avatarUrl" label="头像图标地址">
              <Input maxLength={500} placeholder="可选，使用图片地址或 cos: 头像地址" />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </Layout>
  );
}
