import {
  AndroidOutlined,
  CloudServerOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FolderOpenOutlined,
  HomeOutlined,
  LockOutlined,
  LogoutOutlined,
  SearchOutlined,
  ShareAltOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { App as AntApp, Avatar, Badge, Dropdown, Input, Layout, Menu, Progress, QRCode, Spin, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { CSSProperties, ChangeEvent } from 'react';
import { Suspense, lazy, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LazyChunkErrorBoundary } from '../components/lazy-chunk-error-boundary';
import { RegulatoryFooter } from '../components/RegulatoryFooter';
import { publicAssetPath } from '../lib/appPaths';
import { redirectToUnifiedLogin } from '../lib/unifiedLogin';
import { DriveAccountsAdminModals } from '../features/drive/DriveAccountsAdminModals';
import { DriveAppPackageUploadModal } from '../features/drive/DriveAppPackageUploadModal';
import { DrivePreviewModal } from '../features/drive/DrivePreviewModal';
import { DriveProfileModals } from '../features/drive/DriveProfileModals';
import { DriveShareCreateModal } from '../features/drive/DriveShareCreateModal';
import { DriveStorageActionModals } from '../features/drive/DriveStorageActionModals';
import { DriveUploadFloatingPanel } from '../features/drive/DriveUploadFloatingPanel';
import { useSession } from '../context/session-context';
import { useDriveAccountsAdmin } from '../features/drive/hooks/useDriveAccountsAdmin';
import { useDriveAppPackageAdmin } from '../features/drive/hooks/useDriveAppPackageAdmin';
import { useDriveDashboard } from '../features/drive/hooks/useDriveDashboard';
import { useDriveDownloads } from '../features/drive/hooks/useDriveDownloads';
import { useDriveExplorer } from '../features/drive/hooks/useDriveExplorer';
import { useDriveProfileSettings } from '../features/drive/hooks/useDriveProfileSettings';
import { useDriveShares } from '../features/drive/hooks/useDriveShares';
import { useDriveStorageDialogs } from '../features/drive/hooks/useDriveStorageDialogs';
import {
  APP_DOWNLOAD_PUBLIC_PATH,
  formatFileSize,
  formatNullableBytes,
  getStorageFileCategoryLabel,
  resolveAppDownloadUrl,
  resolveAvatarSrc,
  resolveHomeBackgroundSrc,
  storageFileCategoryDescriptions,
} from '../features/drive/driveShared';
import type { StorageNode, StorageViewMode } from '../types';

const LazyDriveAccountsView = lazy(() => import('../features/drive/DriveAccountsView'));
const LazyDriveAppPackageView = lazy(() => import('../features/drive/DriveAppPackageView'));
const LazyDriveDownloadsView = lazy(() => import('../features/drive/DriveDownloadsView'));
const LazyDriveExplorerView = lazy(() => import('../features/drive/DriveExplorerView'));
const LazyDriveHomeView = lazy(() => import('../features/drive/DriveHomeView'));
const LazyDriveSharesView = lazy(() => import('../features/drive/DriveSharesView'));

const { Header, Sider, Content } = Layout;

const MAX_HOME_BACKGROUND_BYTES = 10 * 1024 * 1024;

const baseMenuItems = [
  { key: 'home', icon: <HomeOutlined />, label: '主页' },
  { key: 'drive', icon: <FolderOpenOutlined />, label: '我的文件' },
  { key: 'downloads', icon: <DownloadOutlined />, label: '下载管理' },
  { key: 'shares', icon: <ShareAltOutlined />, label: '我的分享' },
  { key: 'accounts', icon: <TeamOutlined />, label: '账号管理' },
  { key: 'appPackage', icon: <AndroidOutlined />, label: 'APP 上传' },
  { key: 'trash', icon: <DeleteOutlined />, label: '回收站' },
];

/**
 * 渲染云盘主工作台，并串联上传、下载、筛选、批量操作和账号管理等核心交互。 */
export function DrivePage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const { authToken, currentUser, clearCurrentSession, updateCurrentUser } = useSession();

  const [activeView, setActiveView] = useState<StorageViewMode>('home');
  const isHomeView = activeView === 'home';
  const isDriveView = activeView === 'drive';
  const isDownloadsView = activeView === 'downloads';
  const isSharesView = activeView === 'shares';
  const isAccountsView = activeView === 'accounts';
  const isAppPackageView = activeView === 'appPackage';
  const isTrashView = activeView === 'trash';
  const isListView = isDriveView || isTrashView;
  const isAdmin = currentUser?.role === 'ADMIN';
  const homeBackgroundImage = resolveHomeBackgroundSrc(currentUser);
  const dashboard = useDriveDashboard({ authToken, isHomeView, homeBackgroundImage });
  const explorer = useDriveExplorer({
    authToken,
    activeView,
    message,
    onStorageChanged: dashboard.loadOverview,
  });
  const downloads = useDriveDownloads({
    authToken,
    message,
  });
  const shares = useDriveShares({
    authToken,
    isSharesView,
    message,
  });
  const accounts = useDriveAccountsAdmin({
    authToken,
    isAdmin,
    isAccountsView,
    currentUser,
    message,
    updateCurrentUser,
  });
  const appPackages = useDriveAppPackageAdmin({
    authToken,
    isAdmin,
    isAppPackageView,
    message,
  });
  const profileSettings = useDriveProfileSettings({
    authToken,
    currentUser,
    message,
    updateCurrentUser,
    clearCurrentSession,
    onNavigateToLogin: () => redirectToUnifiedLogin(),
    maxHomeBackgroundBytes: MAX_HOME_BACKGROUND_BYTES,
  });
  const storageDialogs = useDriveStorageDialogs({
    selectedItems: explorer.selectedItems,
    folderOptions: explorer.folderOptions,
    loadFolderOptions: explorer.loadFolderOptions,
    createFolderNode: explorer.createFolderNode,
    renameNode: explorer.renameNode,
    moveNodes: explorer.moveNodes,
  });
  const currentFolderId = explorer.currentFolderId;
  const activeFileCategory = explorer.fileCategory;
  const activeFileCategoryLabel = getStorageFileCategoryLabel(activeFileCategory);
  const panelTitle =
    isTrashView
      ? '回收站'
      : activeFileCategoryLabel
        ? activeFileCategoryLabel
      : currentFolderId === null
        ? '全部文件'
        : explorer.breadcrumbs[explorer.breadcrumbs.length - 1]?.label ?? '我的文件';
  const currentViewLabel = isHomeView
    ? '主页'
    : isDownloadsView
      ? '下载管理'
    : isSharesView
      ? '我的分享'
    : isAccountsView
      ? '账号管理'
      : isAppPackageView
        ? 'APP 上传'
      : isTrashView
        ? '回收站'
        : '我的文件';
  const currentAvatarSrc = resolveAvatarSrc(currentUser);
  const appDownloadAvailable = appPackages.publicAppPackageInfo?.available ?? false;
  const appDownloadUrl = resolveAppDownloadUrl(appPackages.publicAppPackageInfo?.downloadUrl ?? APP_DOWNLOAD_PUBLIC_PATH);
  const appDownloadButtonLabel = appPackages.publicAppPackageLoading
    ? '正在检查…'
    : appPackages.publicAppPackageError
      ? '稍后重试'
      : appDownloadAvailable
        ? '下载 APK'
        : '等待上传';
  const appDownloadEmptyLabel = appPackages.publicAppPackageLoading
    ? '正在加载'
    : appPackages.publicAppPackageError
      ? '状态不可用'
      : '暂未上传 APK';
  const mainShellClassName = `app-main-shell${isHomeView && homeBackgroundImage ? ' app-main-shell-with-background' : ''}`;
  const mainShellStyle =
    isHomeView && homeBackgroundImage
      ? ({
          '--home-background-image': `url(${homeBackgroundImage})`,
          '--home-background-accent-eyebrow': dashboard.homeBackgroundAccent.eyebrow,
          '--home-background-accent-title': dashboard.homeBackgroundAccent.title,
        } as CSSProperties)
      : undefined;
  const contentClassName = `app-content${isHomeView ? ' app-content-home' : ''}`;
  const menuItems = useMemo(() => {
    const visibleItems = isAdmin
      ? baseMenuItems
      : baseMenuItems.filter((item) => !['accounts', 'appPackage'].includes(item.key));

    return visibleItems.map((item) =>
      item.key === 'downloads'
        ? {
            ...item,
            label: (
              <span className="sider-menu-badge-label">
                <span>下载管理</span>
                {downloads.activeDownloadCount > 0 ? <Badge size="small" count={downloads.activeDownloadCount} /> : null}
              </span>
            ),
          }
        : item,
    );
  }, [downloads.activeDownloadCount, isAdmin]);
  const currentViewIcon = isHomeView ? (
    <HomeOutlined />
  ) : isDownloadsView ? (
    <DownloadOutlined />
  ) : isSharesView ? (
    <ShareAltOutlined />
  ) : isAccountsView ? (
    <TeamOutlined />
  ) : isAppPackageView ? (
    <AndroidOutlined />
  ) : isTrashView ? (
    <DeleteOutlined />
  ) : (
    <FolderOpenOutlined />
  );
  const headerEyebrow = isHomeView
    ? '系统概览'
    : isDownloadsView
      ? '传输中心'
    : isSharesView
      ? '分享管理'
    : isAccountsView
      ? '管理中心'
      : isAppPackageView
        ? '客户端分发'
        : isTrashView
          ? '回收与恢复'
          : '文件工作台';
  const headerSearchPlaceholder = isTrashView
    ? '搜索回收站'
    : activeFileCategoryLabel
      ? `搜索${activeFileCategoryLabel}`
      : '搜索当前目录';
  const panelDescription = isTrashView
    ? '回收站中的项目可以恢复，也可以彻底删除。'
    : activeFileCategory
      ? storageFileCategoryDescriptions[activeFileCategory]
      : '统一处理上传、筛选、预览和批量操作。';
  const breadcrumbs = explorer.breadcrumbs;
  const items = explorer.items;
  const listState = explorer.listState;
  const loading = explorer.loading;
  const uploading = explorer.uploading;
  const uploadTasks = explorer.uploadTasks;
  const previewState = explorer.previewState;
  const error = explorer.error;
  const keywordInput = explorer.keywordInput;
  const nodeTypeFilter = explorer.nodeTypeFilter;
  const selectedItems = explorer.selectedItems;
  const selectedRowKeys = explorer.selectedRowKeys;
  const downloadSelectionState = downloads.getSelectionDownloadButtonState(selectedItems);
  const folderOptionsLoading = explorer.folderOptionsLoading;
  const overallUploadProgress = explorer.overallUploadProgress;
  const profileUsedBytes = dashboard.overview?.usedBytes ?? 0;
  const profileTotalBytes = dashboard.overview?.totalSpaceBytes ?? (isAdmin ? null : currentUser?.storageQuotaBytes ?? null);
  const profileUsageLabel = isAdmin ? '当前总占用' : '已用空间';
  const profileUsagePercent =
    profileTotalBytes !== null && profileTotalBytes > 0
      ? Math.min(100, Math.round((profileUsedBytes / profileTotalBytes) * 100))
      : 0;
  const showProfileUsageMeter = profileTotalBytes !== null && profileTotalBytes > 0;
  const previewingFileId = explorer.previewingFileId;

  async function refreshCurrentView() {
    const tasks: Promise<unknown>[] = [appPackages.loadPublicAppPackageInfo()];

    if (isHomeView) {
      tasks.push(dashboard.loadHomeDashboard());
    } else {
      tasks.push(dashboard.loadOverview());

      if (isListView) {
        tasks.push(explorer.loadDrive());
      }
    }

    if (isAccountsView) {
      tasks.push(accounts.loadUsers());
    }

    if (isAppPackageView) {
      tasks.push(appPackages.loadAppPackageInfo());
    }

    if (isSharesView) {
      tasks.push(shares.loadShareLinks());
    }

    await Promise.all(tasks);
  }

  useEffect(() => {
    if (!isAdmin && (activeView === 'accounts' || activeView === 'appPackage')) {
      setActiveView('home');
    }
  }, [activeView, isAdmin]);

  function handleMenuClick(event: { key: string }) {
    const nextView = event.key as StorageViewMode;
    setActiveView(nextView);
    explorer.resetListState(nextView);
  }

  function handleSearch(value: string) {
    explorer.handleSearch(value);
  }

  function handleKeywordInputChange(event: ChangeEvent<HTMLInputElement>) {
    explorer.handleKeywordInputChange(event);
  }
  function handleUploadButtonClick() {
    explorer.handleUploadButtonClick();
  }

  async function handleSelectedFiles(event: ChangeEvent<HTMLInputElement>) {
    await explorer.handleSelectedFiles(event);
  }

  function handleDownloadNode(item: StorageNode) {
    downloads.downloadNode(item);
  }

  async function handlePreviewFile(item: StorageNode) {
    await explorer.handlePreviewFile(item);
  }

  async function handleSubmitCreateShare(values: Parameters<typeof shares.submitCreateShare>[0]) {
    const created = await shares.submitCreateShare(values);
    if (created) {
      explorer.clearSelection();
    }
  }

  /**
   * 将文件或文件夹移入回收站。   */
  async function handleDeleteNode(item: StorageNode) {
    await explorer.deleteNodes([item]);
  }

  /**
   * 批量将文件或文件夹移入回收站。   */
  async function handleDeleteNodes(targets: StorageNode[]) {
    await explorer.deleteNodes(targets);
  }

  /**
   * 从回收站恢复文件或文件夹。   */
  async function handleRestoreNode(item: StorageNode) {
    await explorer.restoreNodes([item]);
  }

  /**
   * 批量从回收站恢复文件或文件夹。   */
  async function handleRestoreNodes(targets: StorageNode[]) {
    await explorer.restoreNodes(targets);
  }

  /**
   * 从回收站彻底删除文件或文件夹。   */
  async function handlePermanentlyDeleteNode(item: StorageNode) {
    await explorer.permanentlyDeleteNodes([item]);
  }

  /**
   * 批量从回收站彻底删除文件或文件夹。   */
  async function handlePermanentlyDeleteNodes(targets: StorageNode[]) {
    await explorer.permanentlyDeleteNodes(targets);
  }

  const avatarMenuItems: MenuProps['items'] = [
    { key: 'profile', icon: <EditOutlined />, label: '个人资料' },
    { key: 'password', icon: <LockOutlined />, label: '修改密码' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  const viewLoadingFallback = (
    <div className="loading-box">
      <Spin size="large" />
    </div>
  );

  const activeViewContent = (
    <LazyChunkErrorBoundary>
      <Suspense fallback={viewLoadingFallback}>
      {isHomeView ? (
        <LazyDriveHomeView
          health={dashboard.health}
          overview={dashboard.overview}
          usageHistory={dashboard.usageHistory}
          backgroundImage={homeBackgroundImage}
          onChooseBackground={profileSettings.handleHomeBackgroundButtonClick}
          onClearBackground={() => void profileSettings.clearHomeBackground()}
        />
      ) : null}

      {isDownloadsView ? (
        <LazyDriveDownloadsView
          tasks={downloads.downloadTasks}
          activeCount={downloads.activeDownloadCount}
          onCancelTask={downloads.cancelDownloadTask}
          onRetryTask={downloads.retryDownloadTask}
          onClearFinished={downloads.clearFinishedDownloads}
          onClearHistory={downloads.clearDownloadHistory}
        />
      ) : null}

      {isListView ? (
        <LazyDriveExplorerView
          mode={isTrashView ? 'trash' : 'drive'}
          title={panelTitle}
          description={panelDescription}
          breadcrumbs={breadcrumbs}
          nodeTypeFilter={nodeTypeFilter}
          fileCategory={activeFileCategory}
          error={error}
          loading={loading}
          uploading={uploading}
          items={items}
          selectedItems={selectedItems}
          selectedRowKeys={selectedRowKeys}
          listState={listState}
          downloadSelectionState={downloadSelectionState}
          previewingFileId={previewingFileId}
          onRefresh={() => void refreshCurrentView()}
          onUploadClick={handleUploadButtonClick}
          onCreateFolderClick={storageDialogs.openCreateFolderModal}
          onFileCategoryChange={explorer.setFileCategory}
          onNodeTypeFilterChange={explorer.setNodeTypeFilter}
          onJumpToCrumb={explorer.jumpToCrumb}
          onRestoreSelection={() => void handleRestoreNodes(selectedItems)}
          onDeleteSelection={() => void handleDeleteNodes(selectedItems)}
          onPermanentDeleteSelection={() => handlePermanentlyDeleteNodes(selectedItems)}
          onOpenBatchMove={storageDialogs.openBatchMoveModal}
          onDownloadSelection={() => downloads.downloadNodes(selectedItems)}
          onShareSelection={() => shares.openCreateShareModal(selectedItems)}
          onSelectionChange={explorer.handleSelectionChange}
          onTableChange={explorer.handleTableChange}
          onOpenFolder={explorer.openFolder}
          onPreviewFile={handlePreviewFile}
          onDownloadNode={handleDownloadNode}
          getNodeDownloadButtonState={downloads.getNodeDownloadButtonState}
          onShareNode={shares.openCreateShareModal}
          onRenameNode={storageDialogs.openRenameModal}
          onMoveNode={storageDialogs.openMoveModal}
          onDeleteNode={handleDeleteNode}
          onRestoreNode={handleRestoreNode}
          onPermanentlyDeleteNode={handlePermanentlyDeleteNode}
        />
      ) : null}

      {isSharesView ? (
        <LazyDriveSharesView
          shareLinks={shares.shareLinks}
          loading={shares.shareLinksLoading}
          onRefresh={() => void shares.loadShareLinks()}
          onRevokeShare={(shareId) => void shares.revokeShare(shareId)}
        />
      ) : null}

      {isAccountsView ? (
        <LazyDriveAccountsView
          isAdmin={isAdmin}
          currentUserId={currentUser?.id}
          users={accounts.users}
          loading={accounts.usersLoading}
          onCreateUser={accounts.openCreateUserModal}
          onEditUserQuota={accounts.openEditUserQuotaModal}
          onResetUserPassword={accounts.openResetUserPasswordModal}
        />
      ) : null}

      {isAppPackageView ? (
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
          <CloudServerOutlined className="brand-icon" />
          <div>
            <Typography.Title level={4}>Alicia 云盘</Typography.Title>
            <Typography.Text>腾讯 COS 文件工作台</Typography.Text>
          </div>
        </div>

        <section className="sider-profile-card">
          <div className="sider-profile-top">
            <Avatar size={52} src={currentAvatarSrc}>
              {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'U'}
            </Avatar>

            <div className="sider-profile-copy">
              <Typography.Title level={5} className="sider-profile-name">
                {currentUser?.nickname ?? '未登录用户'}
              </Typography.Title>
              <div className="sider-profile-meta">
                <span className="sider-role-pill">{isAdmin ? '管理员账号' : '个人空间'}</span>
                <span>{currentUser?.email ?? currentUser?.phoneNumber}</span>
              </div>
            </div>
          </div>

          <div className="sider-usage-row">
            <span className="sider-usage-label">{profileUsageLabel}</span>
            <span className="sider-usage-value">
              {formatFileSize(profileUsedBytes)} / {formatNullableBytes(profileTotalBytes)}
            </span>
          </div>

          {showProfileUsageMeter ? (
            <Progress percent={profileUsagePercent} size="small" showInfo={false} strokeColor="#2563eb" trailColor="#e5edff" />
          ) : (
            <Typography.Text className="muted-text sider-profile-note">
              {isAdmin ? '管理员视图按所有账号的实际占用统计，COS 没有固定容量上限。' : '当前账号没有可展示的个人容量上限。'}
            </Typography.Text>
          )}
        </section>

        <div className="sider-section-label">导航</div>

        <Menu
          mode="inline"
          selectedKeys={[activeView]}
          items={menuItems}
          className="sider-menu"
          onClick={handleMenuClick}
        />

        <section className="sider-download-card">
          <div className="sider-download-head">
            <img
              src={publicAssetPath('/apple-touch-icon.png')}
              alt="Alicia云盘"
              className="sider-download-icon"
            />
            <div className="sider-download-copy">
              <Typography.Text className="sider-download-eyebrow">Android App</Typography.Text>
              <Typography.Title level={5} className="sider-download-title">
                Alicia云盘 APK
              </Typography.Title>
            </div>
          </div>
          <Typography.Paragraph className="sider-download-note">
            {appDownloadAvailable ? '扫码下载移动端安装包' : '上传 APK 后即可开放下载'}
          </Typography.Paragraph>

          {appDownloadAvailable ? (
            <a
              href={appDownloadUrl}
              target="_blank"
              rel="noreferrer"
              className="sider-download-qr-link"
              aria-label="下载安卓客户端"
            >
              <div className="sider-download-qr">
                <QRCode value={appDownloadUrl} size={136} bordered={false} />
              </div>
            </a>
          ) : (
            <div className="sider-download-qr-link sider-download-qr-link-disabled" aria-hidden="true">
              <div className="sider-download-empty">{appDownloadEmptyLabel}</div>
            </div>
          )}
          {appDownloadAvailable ? (
            <a href={appDownloadUrl} target="_blank" rel="noreferrer" className="sider-download-link">
              {appDownloadButtonLabel}
            </a>
          ) : (
            <span className="sider-download-link sider-download-link-disabled">{appDownloadButtonLabel}</span>
          )}
        </section>
      </Sider>

      <Layout className={mainShellClassName} style={mainShellStyle}>
        <Header className="app-header">
          <div className="header-view">
            <Typography.Text className="header-eyebrow">{headerEyebrow}</Typography.Text>
            <div className="header-title">
              {currentViewIcon}
              <Typography.Text>{currentViewLabel}</Typography.Text>
            </div>
          </div>

          <div className="header-actions">
            {isListView ? (
              <Input
                allowClear
                value={keywordInput}
                onChange={handleKeywordInputChange}
                onPressEnter={() => handleSearch(keywordInput)}
                placeholder={headerSearchPlaceholder}
                prefix={<SearchOutlined />}
                className="header-search"
              />
            ) : null}

            <Dropdown
              menu={{ items: avatarMenuItems, onClick: profileSettings.handleAvatarMenuClick }}
              trigger={['click']}
              placement="bottomRight"
            >
              <button type="button" className="avatar-menu-button" aria-label="打开用户菜单">
                <Avatar size={44} src={currentAvatarSrc}>
                  {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'U'}
                </Avatar>
              </button>
            </Dropdown>
          </div>
        </Header>

        <Content className={contentClassName}>
          <input
            ref={explorer.fileInputRef}
            type="file"
            multiple
            className="upload-input"
            onChange={(event) => void handleSelectedFiles(event)}
          />

          <input
            ref={profileSettings.backgroundInputRef}
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            className="upload-input"
            onChange={(event) => void profileSettings.handleHomeBackgroundFileChange(event)}
          />

          {activeViewContent}

          {!isHomeView ? (
            <div className="app-footer">
              <RegulatoryFooter />
            </div>
          ) : null}
        </Content>
      </Layout>

      <DriveUploadFloatingPanel
        uploading={uploading}
        uploadTasks={uploadTasks}
        overallUploadProgress={overallUploadProgress}
        onCancelActiveUploads={explorer.cancelActiveUploads}
        onRetryFailedUploads={() => void explorer.retryFailedUploads()}
        onClearUploadHistory={explorer.clearUploadHistory}
        onRetryUploadTask={(taskId) => void explorer.retryUploadTask(taskId)}
        onCancelUploadTask={explorer.cancelUploadTask}
        getUploadTaskStatusText={explorer.getUploadTaskStatusText}
      />

      <DrivePreviewModal
        previewState={previewState}
        getDownloadButtonState={downloads.getNodeDownloadButtonState}
        onClose={explorer.closePreviewModal}
        onDownloadFile={handleDownloadNode}
      />

      <DriveProfileModals
        currentUser={currentUser}
        currentAvatarSrc={currentAvatarSrc}
        profileOpen={profileSettings.profileOpen}
        passwordOpen={profileSettings.passwordOpen}
        avatarUploading={profileSettings.avatarUploading}
        profileForm={profileSettings.profileForm}
        passwordForm={profileSettings.passwordForm}
        avatarInputRef={profileSettings.avatarInputRef}
        onCloseProfile={profileSettings.closeProfileModal}
        onSubmitProfile={profileSettings.submitProfile}
        onAvatarButtonClick={profileSettings.handleAvatarButtonClick}
        onAvatarFileChange={profileSettings.handleAvatarFileChange}
        onClosePassword={profileSettings.closePasswordModal}
        onSubmitPassword={profileSettings.submitPassword}
      />

      <DriveAccountsAdminModals
        currentUser={currentUser}
        createUserOpen={accounts.createUserOpen}
        createUserRole={accounts.createUserRole}
        editQuotaTarget={accounts.editQuotaTarget}
        resetPasswordTarget={accounts.resetPasswordTarget}
        createUserForm={accounts.createUserForm}
        quotaForm={accounts.quotaForm}
        resetUserPasswordForm={accounts.resetUserPasswordForm}
        onCloseCreateUser={accounts.closeCreateUserModal}
        onSubmitCreateUser={accounts.submitCreateUser}
        onCloseResetPassword={accounts.closeResetUserPasswordModal}
        onSubmitResetPassword={accounts.submitResetUserPassword}
        onCloseEditQuota={accounts.closeEditUserQuotaModal}
        onSubmitUserQuota={accounts.submitUserQuota}
      />

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

      <DriveShareCreateModal
        targets={shares.shareCreateTargets}
        authToken={authToken}
        creating={shares.shareCreating}
        form={shares.createShareForm}
        lastCreatedShare={shares.lastCreatedShare}
        lastCreatedPassword={shares.lastCreatedPassword}
        onClose={shares.closeCreateShareModal}
        onSubmit={handleSubmitCreateShare}
      />

      <DriveStorageActionModals
        createFolderOpen={storageDialogs.createFolderOpen}
        renameTarget={storageDialogs.renameTarget}
        moveTargetsCount={storageDialogs.moveTargets.length}
        moveDialogTitle={storageDialogs.moveDialogTitle}
        folderOptionsLoading={folderOptionsLoading}
        folderTreeData={storageDialogs.folderTreeData}
        createFolderForm={storageDialogs.createFolderForm}
        renameForm={storageDialogs.renameForm}
        moveForm={storageDialogs.moveForm}
        onCloseCreateFolder={storageDialogs.closeCreateFolderModal}
        onSubmitCreateFolder={storageDialogs.submitCreateFolder}
        onCloseRename={storageDialogs.closeRenameModal}
        onSubmitRename={storageDialogs.submitRename}
        onCloseMove={storageDialogs.closeMoveModal}
        onSubmitMove={storageDialogs.submitMove}
      />
    </Layout>
  );
}

