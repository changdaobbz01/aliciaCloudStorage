import {
  CloudServerOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderAddOutlined,
  FolderOpenOutlined,
  HomeOutlined,
  LockOutlined,
  LogoutOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SearchOutlined,
  SwapOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App as AntApp,
  Avatar,
  Breadcrumb,
  Button,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Layout,
  Menu,
  Modal,
  Popconfirm,
  Progress,
  Segmented,
  Select,
  Space,
  Spin,
  TreeSelect,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import type { ChangeEvent } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { StatusPanel } from '../components/StatusPanel';
import { StorageTable } from '../components/StorageTable';
import { UserManagementPanel } from '../components/UserManagementPanel';
import { useSession } from '../context/session-context';
import {
  abortMultipartUpload,
  completeMultipartUpload,
  changePassword,
  clearCurrentUserHomeBackground,
  createFolder,
  createMultipartUpload,
  createUser,
  deleteStorageNodes,
  downloadStorageFile,
  fetchDriveOverview,
  fetchHealth,
  fetchStorageFolders,
  fetchStorageNodes,
  fetchTrashNodes,
  fetchUsageHistory,
  fetchUsers,
  moveStorageNodes,
  permanentlyDeleteStorageNodes,
  renameStorageNode,
  restoreStorageNodes,
  isApiError,
  updateProfile,
  updateUserStorageQuota,
  uploadCurrentUserAvatar,
  uploadCurrentUserHomeBackground,
  uploadMultipartPart,
  uploadStorageFile,
} from '../lib/api';
import type {
  BatchMoveNodePayload,
  ChangePasswordPayload,
  CreateFolderPayload,
  DriveOverview,
  HealthResponse,
  RenameNodePayload,
  SortDirection,
  StorageNode,
  StorageNodeFilter,
  StorageNodeSortField,
  StorageViewMode,
  UpdateProfilePayload,
  UsageHistoryPoint,
  User,
} from '../types';

const { Header, Sider, Content } = Layout;

type FolderCrumb = {
  id: number | null;
  label: string;
};

type MoveNodeFormValues = {
  parentKey: string;
};

type CreateUserFormValues = {
  phoneNumber: string;
  nickname: string;
  avatarUrl: string | null;
  password: string;
  role: User['role'];
  storageQuotaGb: number;
};

type UpdateUserQuotaFormValues = {
  storageQuotaGb: number;
};

type FolderTreeNode = {
  title: string;
  value: string;
  children?: FolderTreeNode[];
};

type ListState = {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  sortBy: StorageNodeSortField;
  sortDirection: SortDirection;
};

type PreviewKind = 'image' | 'pdf' | 'text' | 'audio' | 'video' | 'unsupported';

type PreviewState = {
  target: StorageNode | null;
  kind: PreviewKind | null;
  loading: boolean;
  objectUrl: string | null;
  textContent: string;
  note: string | null;
  error: string | null;
};

type UploadTaskStatus = 'queued' | 'uploading' | 'retrying' | 'completing' | 'success' | 'error' | 'canceled';

type UploadTask = {
  id: string;
  file: File;
  parentId: number | null;
  uploadToken: string | null;
  progress: number;
  loadedBytes: number;
  totalBytes: number;
  status: UploadTaskStatus;
  attempt: number;
  error: string | null;
};

const ROOT_PARENT_KEY = 'ROOT';
const MAX_TEXT_PREVIEW_BYTES = 2 * 1024 * 1024;
const MULTIPART_UPLOAD_THRESHOLD_BYTES = 20 * 1024 * 1024;
const MULTIPART_CHUNK_SIZE_BYTES = 8 * 1024 * 1024;
const BYTES_PER_GIB = 1024 * 1024 * 1024;
const MAX_UPLOAD_RETRIES = 2;
const MAX_CHUNK_UPLOAD_RETRIES = 2;
const MAX_HOME_BACKGROUND_BYTES = 10 * 1024 * 1024;
const DEFAULT_NEW_USER_QUOTA_GB = 0.5;
const PREVIEWABLE_TEXT_EXTENSIONS = new Set([
  'txt',
  'md',
  'csv',
  'tsv',
  'log',
  'json',
  'xml',
  'yaml',
  'yml',
]);
const initialPreviewState: PreviewState = {
  target: null,
  kind: null,
  loading: false,
  objectUrl: null,
  textContent: '',
  note: null,
  error: null,
};

function resolvePreviewKind(node: StorageNode): PreviewKind {
  const mimeType = node.mimeType?.toLowerCase() ?? '';
  const extension = node.extension?.toLowerCase() ?? '';

  if (mimeType.startsWith('image/')) {
    return 'image';
  }

  if (mimeType === 'application/pdf' || extension === 'pdf') {
    return 'pdf';
  }

  if (mimeType.startsWith('video/')) {
    return 'video';
  }

  if (mimeType.startsWith('audio/')) {
    return 'audio';
  }

  if (mimeType.startsWith('text/') || PREVIEWABLE_TEXT_EXTENSIONS.has(extension)) {
    return 'text';
  }

  return 'unsupported';
}

function createDefaultListState(view: StorageViewMode): ListState {
  return {
    page: 1,
    size: 10,
    totalItems: 0,
    totalPages: 0,
    sortBy: view === 'trash' ? 'deletedAt' : 'name',
    sortDirection: view === 'trash' ? 'desc' : 'asc',
  };
}

function formatFileSize(value: number) {
  if (value === 0) {
    return '0 B';
  }

  if (value < 1024) {
    return `${value} B`;
  }

  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unitIndex = -1;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(size >= 100 ? 0 : 1)} ${units[unitIndex]}`;
}

function bytesToGigabytes(bytes: number) {
  return Number((bytes / BYTES_PER_GIB).toFixed(2));
}

function formatNullableBytes(value: number | null) {
  return value === null ? '无限制' : formatFileSize(value);
}

function gigabytesToBytes(gigabytes: number) {
  return Math.round(gigabytes * BYTES_PER_GIB);
}

function createUploadTasks(files: FileList | File[], parentId: number | null) {
  const seed = Date.now();

  return Array.from(files).map((file, index) => ({
    id: `${seed}-${index}-${file.name}`,
    file,
    parentId,
    uploadToken: null,
    progress: 0,
    loadedBytes: 0,
    totalBytes: file.size,
    status: 'queued' as const,
    attempt: 0,
    error: null,
  }));
}

function isRetryableUploadError(error: unknown) {
  if (isApiError(error)) {
    return error.status >= 500;
  }

  return error instanceof Error && error.name !== 'AbortError';
}

function resolveAvatarSrc(user: User | null | undefined) {
  if (!user?.avatarUrl) {
    return undefined;
  }

  if (user.avatarUrl.startsWith('cos:')) {
    return `/api/auth/avatar/${user.id}?v=${encodeURIComponent(user.avatarUrl)}`;
  }

  return user.avatarUrl;
}

function resolveHomeBackgroundSrc(user: User | null | undefined) {
  if (!user?.homeBackgroundUrl) {
    return null;
  }

  if (user.homeBackgroundUrl.startsWith('cosbg:')) {
    return `/api/auth/background/${user.id}?v=${encodeURIComponent(user.homeBackgroundUrl)}`;
  }

  return user.homeBackgroundUrl;
}

function shouldUseMultipartUpload(file: File) {
  return file.size > MULTIPART_UPLOAD_THRESHOLD_BYTES;
}

function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function toHex(buffer: ArrayBuffer) {
  return Array.from(new Uint8Array(buffer))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

function fallbackHash(input: string) {
  let hash = 0x811c9dc5;

  for (let index = 0; index < input.length; index += 1) {
    hash ^= input.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }

  return hash.toString(16).padStart(8, '0');
}

async function createUploadFingerprint(file: File) {
  const signature = `${file.name}\n${file.size}\n${file.lastModified}\n${file.type}`;

  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(signature));
    return toHex(digest);
  }

  return fallbackHash(signature);
}

async function retryUploadRequest<T>(producer: () => Promise<T>, retries: number) {
  for (let attempt = 1; attempt <= retries + 1; attempt += 1) {
    try {
      return await producer();
    } catch (error) {
      if (attempt > retries || !isRetryableUploadError(error)) {
        throw error;
      }

      await wait(Math.min(800 * attempt, 2000));
    }
  }

  throw new Error('上传失败，请稍后重试。');
}

const baseMenuItems = [
  { key: 'home', icon: <HomeOutlined />, label: '主页' },
  { key: 'drive', icon: <FolderOpenOutlined />, label: '我的文件' },
  { key: 'accounts', icon: <TeamOutlined />, label: '账号管理' },
  { key: 'trash', icon: <DeleteOutlined />, label: '回收站' },
];

/**
 * 渲染云盘主工作台，并串联上传、下载、筛选、批量操作和账号管理等核心交互。 */
export function DrivePage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const { authToken, currentUser, clearCurrentSession, updateCurrentUser } = useSession();

  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [overview, setOverview] = useState<DriveOverview | null>(null);
  const [usageHistory, setUsageHistory] = useState<UsageHistoryPoint[]>([]);
  const [items, setItems] = useState<StorageNode[]>([]);
  const [listState, setListState] = useState<ListState>(() => createDefaultListState('drive'));
  const [users, setUsers] = useState<User[]>([]);
  const [folderOptions, setFolderOptions] = useState<StorageNode[]>([]);
  const [breadcrumbs, setBreadcrumbs] = useState<FolderCrumb[]>([{ id: null, label: '根目录' }]);
  const [activeView, setActiveView] = useState<StorageViewMode>('home');
  const [loading, setLoading] = useState(true);
  const [usersLoading, setUsersLoading] = useState(false);
  const [folderOptionsLoading, setFolderOptionsLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [uploadTasks, setUploadTasks] = useState<UploadTask[]>([]);
  const [downloadingFileId, setDownloadingFileId] = useState<number | null>(null);
  const [previewState, setPreviewState] = useState<PreviewState>(initialPreviewState);
  const [error, setError] = useState<string | null>(null);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [nodeTypeFilter, setNodeTypeFilter] = useState<StorageNodeFilter>('ALL');
  const [selectedItems, setSelectedItems] = useState<StorageNode[]>([]);
  const [profileOpen, setProfileOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [createUserOpen, setCreateUserOpen] = useState(false);
  const [createFolderOpen, setCreateFolderOpen] = useState(false);
  const [editQuotaTarget, setEditQuotaTarget] = useState<User | null>(null);
  const [renameTarget, setRenameTarget] = useState<StorageNode | null>(null);
  const [moveTargets, setMoveTargets] = useState<StorageNode[]>([]);

  const [profileForm] = Form.useForm<UpdateProfilePayload>();
  const [passwordForm] = Form.useForm<ChangePasswordPayload & { confirmPassword: string }>();
  const [createUserForm] = Form.useForm<CreateUserFormValues>();
  const [createFolderForm] = Form.useForm<CreateFolderPayload>();
  const [quotaForm] = Form.useForm<UpdateUserQuotaFormValues>();
  const createUserRole = Form.useWatch('role', createUserForm) ?? 'USER';
  const [renameForm] = Form.useForm<RenameNodePayload>();
  const [moveForm] = Form.useForm<MoveNodeFormValues>();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const avatarInputRef = useRef<HTMLInputElement | null>(null);
  const backgroundInputRef = useRef<HTMLInputElement | null>(null);
  const previewObjectUrlRef = useRef<string | null>(null);
  const previewRequestIdRef = useRef(0);
  const uploadControllersRef = useRef<Map<string, AbortController>>(new Map());

  const currentFolderId = breadcrumbs[breadcrumbs.length - 1]?.id ?? null;
  const isHomeView = activeView === 'home';
  const isDriveView = activeView === 'drive';
  const isAccountsView = activeView === 'accounts';
  const isTrashView = activeView === 'trash';
  const isListView = isDriveView || isTrashView;
  const panelTitle = isTrashView ? '回收站' : currentFolderId === null ? '全部文件' : breadcrumbs[breadcrumbs.length - 1]?.label ?? '我的文件';
  const currentViewLabel = isHomeView
    ? '主页'
    : isAccountsView
      ? '账号管理'
      : isTrashView
        ? '回收站'
        : '我的文件';
  const isAdmin = currentUser?.role === 'ADMIN';
  const currentAvatarSrc = resolveAvatarSrc(currentUser);
  const homeBackgroundImage = resolveHomeBackgroundSrc(currentUser);
  const menuItems = useMemo(
    () => (isAdmin ? baseMenuItems : baseMenuItems.filter((item) => item.key !== 'accounts')),
    [isAdmin],
  );
  const currentViewIcon = isHomeView ? (
    <HomeOutlined />
  ) : isAccountsView ? (
    <TeamOutlined />
  ) : isTrashView ? (
    <DeleteOutlined />
  ) : (
    <FolderOpenOutlined />
  );
  const headerEyebrow = isHomeView ? '系统概览' : isAccountsView ? '管理中心' : isTrashView ? '回收与恢复' : '文件工作台';
  const headerSearchPlaceholder = isTrashView ? '搜索回收站' : '搜索当前目录';
  const panelDescription = isTrashView ? '回收站中的项目可以恢复，也可以彻底删除。' : '统一处理上传、筛选、预览和批量操作。';
  const moveTarget = moveTargets.length === 1 ? moveTargets[0] : null;
  const selectedRowKeys = useMemo(() => selectedItems.map((item) => item.id), [selectedItems]);
  const selectedCount = selectedItems.length;
  const uploadPanelVisible = uploadTasks.length > 0;
  const uploadFailedCount = uploadTasks.filter((task) => task.status === 'error').length;
  const uploadSuccessCount = uploadTasks.filter((task) => task.status === 'success').length;
  const uploadCanceledCount = uploadTasks.filter((task) => task.status === 'canceled').length;
  const profileUsedBytes = overview?.usedBytes ?? 0;
  const profileTotalBytes = overview?.totalSpaceBytes ?? (isAdmin ? null : currentUser?.storageQuotaBytes ?? null);
  const profileUsageLabel = isAdmin ? '当前总占用' : '已用空间';
  const profileUsagePercent =
    profileTotalBytes !== null && profileTotalBytes > 0
      ? Math.min(100, Math.round((profileUsedBytes / profileTotalBytes) * 100))
      : 0;
  const showProfileUsageMeter = profileTotalBytes !== null && profileTotalBytes > 0;
  const overallUploadProgress = useMemo(() => {
    if (uploadTasks.length === 0) {
      return 0;
    }

    const totalBytes = uploadTasks.reduce((sum, task) => sum + task.totalBytes, 0);
    if (totalBytes === 0) {
      return 0;
    }

    const uploadedBytes = uploadTasks.reduce((sum, task) => {
      if (task.status === 'success') {
        return sum + task.totalBytes;
      }

      return sum + Math.min(task.loadedBytes, task.totalBytes);
    }, 0);

    return Math.round((uploadedBytes / totalBytes) * 100);
  }, [uploadTasks]);
  const previewTarget = previewState.target;
  const previewingFileId = previewState.loading ? previewTarget?.id ?? null : null;
  const moveDialogTitle = moveTargets.length > 1 ? '批量移动' : '移动';

  const folderTreeData = useMemo(() => {
    const allChildrenMap = new Map<number | null, StorageNode[]>();

    folderOptions.forEach((folder) => {
      const siblings = allChildrenMap.get(folder.parentId) ?? [];
      siblings.push(folder);
      allChildrenMap.set(folder.parentId, siblings);
    });

    const blockedIds = new Set<number>();
    const collectDescendantIds = (folderId: number) => {
      (allChildrenMap.get(folderId) ?? []).forEach((childFolder) => {
        blockedIds.add(childFolder.id);
        collectDescendantIds(childFolder.id);
      });
    };

    moveTargets
      .filter((target) => target.type === 'FOLDER')
      .forEach((target) => {
        blockedIds.add(target.id);
        collectDescendantIds(target.id);
      });

    const visibleChildrenMap = new Map<number | null, StorageNode[]>();
    folderOptions
      .filter((folder) => !blockedIds.has(folder.id))
      .forEach((folder) => {
        const siblings = visibleChildrenMap.get(folder.parentId) ?? [];
        siblings.push(folder);
        visibleChildrenMap.set(folder.parentId, siblings);
      });

    const buildTree = (parentId: number | null): FolderTreeNode[] =>
      (visibleChildrenMap.get(parentId) ?? [])
        .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
        .map((folder) => {
          const children = buildTree(folder.id);

          return {
            title: folder.name,
            value: String(folder.id),
            children: children.length > 0 ? children : undefined,
          };
        });

    return [
      {
        title: '根目录',
        value: ROOT_PARENT_KEY,
        children: buildTree(null),
      },
    ];
  }, [folderOptions, moveTargets]);

  /**
   * 查询后端健康状态，供顶部概览面板展示。   */
  async function loadHealth() {
    try {
      setHealth(await fetchHealth());
    } catch {
      setHealth(null);
    }
  }

  /**
   * 按当前目录、关键字和类型筛选条件刷新文件列表。   */
  async function loadDrive() {
    if (!authToken) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      if (!isListView) {
        const [overviewData, usageHistoryData] = await Promise.all([
          fetchDriveOverview(authToken),
          fetchUsageHistory(authToken, 30),
        ]);

        setOverview(overviewData);
        setUsageHistory(usageHistoryData);
        return;
      }

      const nodeRequest = isTrashView
        ? fetchTrashNodes(authToken, keyword, nodeTypeFilter, listState)
        : fetchStorageNodes(authToken, currentFolderId, keyword, nodeTypeFilter, listState);
      const [overviewData, usageHistoryData, nodeData] = await Promise.all([
        fetchDriveOverview(authToken),
        fetchUsageHistory(authToken, 30),
        nodeRequest,
      ]);

      setOverview(overviewData);
      setUsageHistory(usageHistoryData);
      setItems(nodeData.items);
      setListState((current) => ({
        ...current,
        page: nodeData.page,
        size: nodeData.size,
        totalItems: nodeData.totalItems,
        totalPages: nodeData.totalPages,
        sortBy: nodeData.sortBy,
        sortDirection: nodeData.sortDirection,
      }));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载文件列表失败。');
    } finally {
      setLoading(false);
    }
  }

  /**
   * 当前用户是管理员时刷新账号管理列表。   */
  async function loadUsers() {
    if (!authToken || !isAdmin) {
      setUsers([]);
      return;
    }

    setUsersLoading(true);

    try {
      setUsers(await fetchUsers(authToken));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载账号列表失败。');
    } finally {
      setUsersLoading(false);
    }
  }

  /**
   * 刷新文件夹树，用于移动弹窗里的目标目录选择。   */
  async function loadFolderOptions() {
    if (!authToken) {
      return;
    }

    setFolderOptionsLoading(true);

    try {
      setFolderOptions(await fetchStorageFolders(authToken));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载文件夹列表失败。');
    } finally {
      setFolderOptionsLoading(false);
    }
  }

  /**
   * 刷新当前页需要的远程数据。   */
  async function refreshCurrentView() {
    await Promise.all([loadHealth(), loadDrive(), loadUsers()]);
  }

  useEffect(() => {
    void loadHealth();
  }, []);

  useEffect(() => {
    void loadDrive();
  }, [
    authToken,
    activeView,
    currentFolderId,
    keyword,
    nodeTypeFilter,
    listState.page,
    listState.size,
    listState.sortBy,
    listState.sortDirection,
  ]);

  useEffect(() => {
    void loadUsers();
  }, [authToken, isAdmin]);

  useEffect(() => {
    setSelectedItems((current) => current.filter((item) => items.some((candidate) => candidate.id === item.id)));
  }, [items]);

  useEffect(() => {
    if (!isAdmin && activeView === 'accounts') {
      setActiveView('home');
    }
  }, [activeView, isAdmin]);

  useEffect(() => {
    setSelectedItems([]);
  }, [activeView, currentFolderId, keyword, nodeTypeFilter]);

  useEffect(() => {
    setListState((current) => {
      if (current.page === 1) {
        return current;
      }

      return {
        ...current,
        page: 1,
      };
    });
  }, [currentFolderId, keyword, nodeTypeFilter]);

  useEffect(() => () => {
    previewRequestIdRef.current += 1;

    if (previewObjectUrlRef.current) {
      URL.revokeObjectURL(previewObjectUrlRef.current);
      previewObjectUrlRef.current = null;
    }
  }, []);

  useEffect(() => {
    previewRequestIdRef.current += 1;

    if (previewObjectUrlRef.current) {
      URL.revokeObjectURL(previewObjectUrlRef.current);
      previewObjectUrlRef.current = null;
    }

    setPreviewState(initialPreviewState);
  }, [activeView, currentFolderId]);

  /**
   * 清空当前页的多选状态，避免批量操作后保留过期选项。   */
  function clearSelection() {
    setSelectedItems([]);
  }

  function revokePreviewObjectUrl() {
    if (!previewObjectUrlRef.current) {
      return;
    }

    URL.revokeObjectURL(previewObjectUrlRef.current);
    previewObjectUrlRef.current = null;
  }

  function closePreviewModal() {
    previewRequestIdRef.current += 1;
    revokePreviewObjectUrl();
    setPreviewState(initialPreviewState);
  }

  /**
   * 进入双击或点击的目标文件夹，并更新面包屑路径。   */
  function openFolder(item: StorageNode) {
    if (!isDriveView) {
      return;
    }

    setBreadcrumbs((current) => [...current, { id: item.id, label: item.name }]);
  }

  /**
   * 点击面包屑时回到指定层级目录。   */
  function jumpToCrumb(index: number) {
    setBreadcrumbs((current) => current.slice(0, index + 1));
  }

  /**
   * 在侧边栏切换我的文件和回收站视图。   */
  function handleMenuClick(event: { key: string }) {
    const nextView = event.key as StorageViewMode;
    setActiveView(nextView);
    setListState(createDefaultListState(nextView));
  }

  /**
   * 同步当前表格的多选状态，供工具栏批量操作复用。   */
  function handleSelectionChange(nextItems: StorageNode[]) {
    setSelectedItems(nextItems);
  }

  function handleTableChange(options: {
    page: number;
    pageSize: number;
    sortBy: StorageNodeSortField;
    sortDirection: SortDirection;
  }) {
    clearSelection();
    setListState((current) => {
      const sortChanged =
        current.sortBy !== options.sortBy || current.sortDirection !== options.sortDirection;
      const sizeChanged = current.size !== options.pageSize;

      return {
        ...current,
        page: sortChanged || sizeChanged ? 1 : options.page,
        size: options.pageSize,
        sortBy: options.sortBy,
        sortDirection: options.sortDirection,
      };
    });
  }

  /**
   * 打开个人资料弹窗，并回填当前用户信息。   */
  function openProfileModal() {
    if (!currentUser) {
      return;
    }

    profileForm.setFieldsValue({
      phoneNumber: currentUser.phoneNumber,
      nickname: currentUser.nickname,
    });
    setProfileOpen(true);
  }

  function handleAvatarButtonClick() {
    avatarInputRef.current?.click();
  }

  async function handleAvatarFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken) {
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

    setAvatarUploading(true);

    try {
      const updatedUser = await uploadCurrentUserAvatar(selectedFile, authToken);
      updateCurrentUser(updatedUser);
      profileForm.setFieldsValue({ avatarUrl: updatedUser.avatarUrl ?? '' });
      message.success('头像已更新。');
    } catch (avatarError) {
      message.error(avatarError instanceof Error ? avatarError.message : '头像上传失败。');
    } finally {
      setAvatarUploading(false);
    }
  }

  function handleHomeBackgroundButtonClick() {
    const input = backgroundInputRef.current;

    if (!input) {
      message.error('背景图上传入口初始化失败，请刷新页面后重试。');
      return;
    }

    try {
      if (typeof input.showPicker === 'function') {
        input.showPicker();
        return;
      }
    } catch {
      // 部分浏览器限制 showPicker，回退到 click。
    }
    input.click();
  }

  async function handleHomeBackgroundFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken) {
      return;
    }

    const selectedFile = event.target.files?.[0] ?? null;
    event.target.value = '';

    if (!selectedFile) {
      return;
    }

    if (!selectedFile.type.startsWith('image/')) {
      message.error('请选择图片文件作为主页背景。');
      return;
    }

    if (selectedFile.size > MAX_HOME_BACKGROUND_BYTES) {
      message.error('背景图不能超过 10 MB，请换一张更小的图片。');
      return;
    }

    try {
      const updatedUser = await uploadCurrentUserHomeBackground(selectedFile, authToken);
      updateCurrentUser(updatedUser);
      message.success('主页背景图已更新。');
    } catch (backgroundError) {
      message.error(backgroundError instanceof Error ? backgroundError.message : '背景图上传失败。');
    }
  }

  async function clearHomeBackground() {
    if (!authToken) {
      return;
    }

    try {
      const updatedUser = await clearCurrentUserHomeBackground(authToken);
      updateCurrentUser(updatedUser);
      message.success('主页背景图已移除。');
    } catch (backgroundError) {
      message.error(backgroundError instanceof Error ? backgroundError.message : '移除背景图失败。');
    }
  }

  /**
   * 打开修改密码弹窗，并清空上一次输入内容。   */
  function openPasswordModal() {
    passwordForm.resetFields();
    setPasswordOpen(true);
  }

  /**
   * 打开管理员新增账号弹窗，并写入默认角色。   */
  function openCreateUserModal() {
    createUserForm.setFieldsValue({
      role: 'USER',
      avatarUrl: '',
      storageQuotaGb: DEFAULT_NEW_USER_QUOTA_GB,
    });
    setCreateUserOpen(true);
  }

  function openEditUserQuotaModal(user: User) {
    if (user.role === 'ADMIN' || user.storageQuotaBytes === null) {
      message.info('管理员账号不限制存储额度。');
      return;
    }

    quotaForm.setFieldsValue({
      storageQuotaGb: bytesToGigabytes(user.storageQuotaBytes),
    });
    setEditQuotaTarget(user);
  }

  /**
   * 打开新建文件夹弹窗，并清空上次填写的目录名。   */
  function openCreateFolderModal() {
    createFolderForm.resetFields();
    setCreateFolderOpen(true);
  }

  /**
   * 打开重命名弹窗，并回填当前项目名称。   */
  function openRenameModal(item: StorageNode) {
    renameForm.setFieldsValue({ name: item.name });
    setRenameTarget(item);
  }

  /**
   * 打开移动弹窗，并按当前选择初始化目标目录。   */
  function openMoveModal(targets: StorageNode[] | StorageNode) {
    const normalizedTargets = Array.isArray(targets) ? targets : [targets];
    const firstTarget = normalizedTargets[0];

    if (!firstTarget) {
      return;
    }

    const sameParent = normalizedTargets.every((target) => target.parentId === firstTarget.parentId);
    moveForm.setFieldsValue({
      parentKey: sameParent
        ? firstTarget.parentId === null
          ? ROOT_PARENT_KEY
          : String(firstTarget.parentId)
        : ROOT_PARENT_KEY,
    });
    setMoveTargets(normalizedTargets);
    void loadFolderOptions();
  }

  /**
   * 打开批量移动弹窗，没有选中项时直接忽略。   */
  function openBatchMoveModal() {
    if (selectedItems.length === 0) {
      return;
    }

    openMoveModal(selectedItems);
  }

  /**
   * 将搜索框里的值提交为当前列表筛选关键字。   */
  function handleSearch(value: string) {
    setKeyword(value.trim());
  }

  function handleKeywordInputChange(event: ChangeEvent<HTMLInputElement>) {
    const nextValue = event.target.value;
    setKeywordInput(nextValue);

    if (nextValue.trim() === '' && keyword !== '') {
      setKeyword('');
    }
  }

  /**
   * 打开系统文件选择器，让用户从本地选择待上传文件。   */
  function updateUploadTask(taskId: string, updater: (task: UploadTask) => UploadTask) {
    setUploadTasks((current) =>
      current.map((task) => (task.id === taskId ? updater(task) : task)),
    );
  }

  function createUploadAbortController(taskId: string) {
    const controller = new AbortController();
    uploadControllersRef.current.set(taskId, controller);
    return controller;
  }

  function clearUploadAbortController(taskId: string, controller: AbortController) {
    if (uploadControllersRef.current.get(taskId) === controller) {
      uploadControllersRef.current.delete(taskId);
    }
  }

  function cancelUploadTask(taskId: string) {
    const controller = uploadControllersRef.current.get(taskId);

    if (!controller) {
      return;
    }

    controller.abort();
    const task = uploadTasks.find((candidate) => candidate.id === taskId);
    if (authToken && task?.uploadToken && task.status !== 'completing') {
      void abortMultipartUpload(task.uploadToken, authToken).catch(() => undefined);
    }

    updateUploadTask(taskId, (current) => ({
      ...current,
      status: 'canceled',
      error: '上传已取消。',
    }));
  }

  function cancelActiveUploads() {
    if (uploadControllersRef.current.size === 0) {
      return;
    }

    Array.from(uploadControllersRef.current.keys()).forEach(cancelUploadTask);
  }

  function clearUploadHistory() {
    if (uploading) {
      return;
    }

    setUploadTasks([]);
  }

  function getUploadTaskStatusText(task: UploadTask) {
    if (task.status === 'success') {
      return '上传完成';
    }

    if (task.status === 'error') {
      return '上传失败';
    }

    if (task.status === 'canceled') {
      return '已取消';
    }

    if (task.status === 'completing') {
      return '正在合并文件';
    }

    if (task.status === 'retrying') {
      return `继续上传（第 ${task.attempt} 次）`;
    }

    if (task.status === 'uploading') {
      return `正在上传（第 ${task.attempt} 次）`;
    }

    return '等待上传';
  }

  async function uploadMultipartTask(task: UploadTask, token: string, signal?: AbortSignal) {
    const fingerprint = await createUploadFingerprint(task.file);
    const totalChunks = Math.ceil(task.file.size / MULTIPART_CHUNK_SIZE_BYTES);
    const session = await createMultipartUpload(
      {
        parentId: task.parentId,
        fileName: task.file.name,
        fileSize: task.file.size,
        contentType: task.file.type || null,
        chunkSize: MULTIPART_CHUNK_SIZE_BYTES,
        totalChunks,
        fingerprint,
      },
      token,
      { signal },
    );
    const uploadedParts = new Map(session.uploadedParts.map((part) => [part.partNumber, part]));
    let completedBytes = session.uploadedParts.reduce((sum, part) => sum + part.size, 0);

    updateUploadTask(task.id, (current) => ({
      ...current,
      uploadToken: session.uploadToken,
      loadedBytes: completedBytes,
      totalBytes: session.fileSize,
      progress: Math.round((completedBytes / session.fileSize) * 100),
    }));

    for (let partNumber = 1; partNumber <= session.totalChunks; partNumber += 1) {
      const uploadedPart = uploadedParts.get(partNumber);

      if (uploadedPart) {
        continue;
      }

      const start = (partNumber - 1) * session.chunkSize;
      const end = Math.min(start + session.chunkSize, task.file.size);
      const chunk = task.file.slice(start, end);

      await retryUploadRequest(
        () =>
          uploadMultipartPart(session.uploadToken, partNumber, chunk, token, {
            signal,
            onProgress: ({ loaded }) => {
              const loadedBytes = completedBytes + loaded;
              updateUploadTask(task.id, (current) => ({
                ...current,
                loadedBytes,
                totalBytes: session.fileSize,
                progress: Math.round((loadedBytes / session.fileSize) * 100),
                error: null,
              }));
            },
          }),
        MAX_CHUNK_UPLOAD_RETRIES,
      );

      completedBytes += chunk.size;
      updateUploadTask(task.id, (current) => ({
        ...current,
        loadedBytes: completedBytes,
        totalBytes: session.fileSize,
        progress: Math.round((completedBytes / session.fileSize) * 100),
        error: null,
      }));
    }

    updateUploadTask(task.id, (current) => ({
      ...current,
      status: 'completing',
      loadedBytes: session.fileSize,
      totalBytes: session.fileSize,
      progress: 100,
      error: null,
    }));

    return retryUploadRequest(
      () => completeMultipartUpload(session.uploadToken, token, { signal }),
      MAX_UPLOAD_RETRIES,
    );
  }

  async function uploadTaskFile(task: UploadTask, token: string, signal?: AbortSignal) {
    if (shouldUseMultipartUpload(task.file)) {
      return uploadMultipartTask(task, token, signal);
    }

    return uploadStorageFile(task.file, task.parentId, token, {
      signal,
      onProgress: ({ loaded, total, percent }) => {
        updateUploadTask(task.id, (current) => ({
          ...current,
          status: 'uploading',
          loadedBytes: loaded,
          totalBytes: total || current.totalBytes || current.file.size,
          progress: percent,
          error: null,
        }));
      },
    });
  }

  async function runUploadTasks(tasksToRun: UploadTask[]) {
    if (!authToken || tasksToRun.length === 0) {
      return;
    }

    setUploading(true);
    let successCount = 0;
    let canceledCount = 0;

    try {
      for (const task of tasksToRun) {
        for (let attempt = 1; attempt <= MAX_UPLOAD_RETRIES + 1; attempt += 1) {
          const controller = createUploadAbortController(task.id);

          updateUploadTask(task.id, (current) => ({
            ...current,
            status: attempt === 1 ? 'uploading' : 'retrying',
            attempt,
            progress: 0,
            loadedBytes: 0,
            totalBytes: current.totalBytes || current.file.size,
            error: null,
          }));

          try {
            await uploadTaskFile(task, authToken, controller.signal);

            updateUploadTask(task.id, (current) => ({
              ...current,
              status: 'success',
              progress: 100,
              loadedBytes: current.totalBytes || current.file.size,
              totalBytes: current.totalBytes || current.file.size,
              error: null,
            }));
            successCount += 1;
            break;
          } catch (uploadError) {
            if (uploadError instanceof Error && uploadError.name === 'AbortError') {
              updateUploadTask(task.id, (current) => ({
                ...current,
                status: 'canceled',
                error: '上传已取消。',
              }));
              canceledCount += 1;
              break;
            }

            const shouldRetry =
              attempt <= MAX_UPLOAD_RETRIES && isRetryableUploadError(uploadError);

            if (shouldRetry) {
              await new Promise((resolve) => setTimeout(resolve, Math.min(800 * attempt, 2000)));
              continue;
            }

            updateUploadTask(task.id, (current) => ({
              ...current,
              status: 'error',
              error: uploadError instanceof Error ? uploadError.message : '上传失败，请稍后重试。',
            }));
            break;
          } finally {
            clearUploadAbortController(task.id, controller);
          }
        }
      }
    } finally {
      setUploading(false);
    }

    if (successCount > 0) {
      clearSelection();
      await Promise.all([loadDrive(), loadHealth()]);
    }

    const failedCount = tasksToRun.length - successCount - canceledCount;
    if (failedCount === 0) {
      if (canceledCount > 0) {
        message.info(
          canceledCount === tasksToRun.length
            ? '上传已取消。'
            : `已完成 ${successCount} 个文件上传，取消 ${canceledCount} 个。`,
        );
        return;
      }

      message.success(
        tasksToRun.length === 1
          ? `文件“${tasksToRun[0].file.name}”上传成功。`
          : `已完成 ${tasksToRun.length} 个文件上传。`,
      );
      return;
    }

    if (successCount > 0) {
      message.warning(`已完成 ${successCount} 个文件上传，另有 ${failedCount} 个失败。`);
      return;
    }

    message.error(
      tasksToRun.length === 1
        ? `文件“${tasksToRun[0].file.name}”上传失败。`
        : `共 ${failedCount} 个文件上传失败。`,
    );
  }

  async function retryFailedUploads() {
    if (uploading) {
      return;
    }

    const failedTasks = uploadTasks.filter((task) => task.status === 'error');
    if (failedTasks.length === 0) {
      return;
    }

    await runUploadTasks(failedTasks);
  }

  async function retryUploadTask(taskId: string) {
    if (uploading) {
      return;
    }

    const retryTarget = uploadTasks.find((task) => task.id === taskId && task.status === 'error');
    if (!retryTarget) {
      return;
    }

    await runUploadTasks([retryTarget]);
  }

  function handleUploadButtonClick() {
    if (uploading) {
      message.info('当前仍有文件正在上传，请稍候。');
      return;
    }

    const input = fileInputRef.current;
    if (!input) {
      message.error('上传入口初始化失败，请刷新页面后重试。');
      return;
    }

    try {
      if (typeof input.showPicker === 'function') {
        input.showPicker();
        return;
      }
    } catch {
      // Fall back to the classic click flow when showPicker is unavailable.
    }

    input.click();
  }
  /**
   * 接收本地文件并通过后端上传到腾讯 COS。   */
  async function handleSelectedFiles(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken) {
      return;
    }

    const selectedFiles = event.target.files ? Array.from(event.target.files) : [];
    event.target.value = '';

    if (selectedFiles.length === 0) {
      return;
    }

    const nextUploadTasks = createUploadTasks(selectedFiles, currentFolderId);
    setUploadTasks(nextUploadTasks);
    await runUploadTasks(nextUploadTasks);
  }

  /**
   * 调用后端下载接口，将返回的文件流保存到浏览器本地。   */
  async function handleDownloadFile(item: StorageNode) {
    if (!authToken) {
      return;
    }

    setDownloadingFileId(item.id);

    try {
      const { blob, fileName } = await downloadStorageFile(item.id, authToken);
      const downloadUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = downloadUrl;
      anchor.download = fileName || item.name;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(downloadUrl);
    } catch (downloadError) {
      message.error(downloadError instanceof Error ? downloadError.message : '下载文件失败。');
    } finally {
      setDownloadingFileId(null);
    }
  }

  async function handlePreviewFile(item: StorageNode) {
    if (!authToken || item.type !== 'FILE') {
      return;
    }

    const kind = resolvePreviewKind(item);
    const requestId = previewRequestIdRef.current + 1;
    previewRequestIdRef.current = requestId;
    revokePreviewObjectUrl();

    if (kind === 'unsupported') {
      setPreviewState({
        target: item,
        kind,
        loading: false,
        objectUrl: null,
        textContent: '',
        note: '当前文件暂不支持在线预览，请直接下载查看。',
        error: null,
      });
      return;
    }

    if (kind === 'text' && item.size > MAX_TEXT_PREVIEW_BYTES) {
      setPreviewState({
        target: item,
        kind: 'unsupported',
        loading: false,
        objectUrl: null,
        textContent: '',
        note: '文本文件超过 2 MB，暂不在线预览，请直接下载查看。',
        error: null,
      });
      return;
    }

    setPreviewState({
      target: item,
      kind,
      loading: true,
      objectUrl: null,
      textContent: '',
      note: null,
      error: null,
    });

    try {
      const { blob } = await downloadStorageFile(item.id, authToken);

      if (previewRequestIdRef.current !== requestId) {
        return;
      }

      if (kind === 'text') {
        const textContent = await blob.text();

        if (previewRequestIdRef.current !== requestId) {
          return;
        }

        setPreviewState({
          target: item,
          kind,
          loading: false,
          objectUrl: null,
          textContent,
          note: null,
          error: null,
        });
        return;
      }

      const objectUrl = URL.createObjectURL(blob);

      if (previewRequestIdRef.current !== requestId) {
        URL.revokeObjectURL(objectUrl);
        return;
      }

      previewObjectUrlRef.current = objectUrl;
      setPreviewState({
        target: item,
        kind,
        loading: false,
        objectUrl,
        textContent: '',
        note: null,
        error: null,
      });
    } catch (previewError) {
      if (previewRequestIdRef.current !== requestId) {
        return;
      }

      setPreviewState({
        target: item,
        kind,
        loading: false,
        objectUrl: null,
        textContent: '',
        note: null,
        error: previewError instanceof Error ? previewError.message : '预览文件失败。',
      });
    }
  }

  function renderPreviewContent() {
    if (!previewTarget) {
      return null;
    }

    if (previewState.loading) {
      return (
        <div className="loading-box preview-loading-box">
          <Spin size="large" />
        </div>
      );
    }

    if (previewState.error) {
      return <Alert type="error" showIcon message="预览失败" description={previewState.error} />;
    }

    const notice = previewState.note ? (
      <Alert type="info" showIcon message={previewState.note} className="preview-notice" />
    ) : null;

    if (previewState.kind === 'image' && previewState.objectUrl) {
      return (
        <div className="preview-layout">
          {notice}
          <div className="preview-frame">
            <img src={previewState.objectUrl} alt={previewTarget.name} className="preview-image" />
          </div>
        </div>
      );
    }

    if (previewState.kind === 'pdf' && previewState.objectUrl) {
      return (
        <div className="preview-layout">
          {notice}
          <iframe title={previewTarget.name} src={previewState.objectUrl} className="preview-iframe" />
        </div>
      );
    }

    if (previewState.kind === 'video' && previewState.objectUrl) {
      return (
        <div className="preview-layout">
          {notice}
          <video controls className="preview-video" src={previewState.objectUrl} />
        </div>
      );
    }

    if (previewState.kind === 'audio' && previewState.objectUrl) {
      return (
        <div className="preview-layout">
          {notice}
          <audio controls className="preview-audio" src={previewState.objectUrl} />
        </div>
      );
    }

    if (previewState.kind === 'text') {
      return (
        <div className="preview-layout">
          {notice}
          <pre className="preview-text">{previewState.textContent || '文件内容为空。'}</pre>
        </div>
      );
    }

    return (
      <div className="preview-layout">
        {notice || <Alert type="info" showIcon message="当前文件暂不支持在线预览，请直接下载查看。" />}
      </div>
    );
  }

  /**
   * 将文件或文件夹移入回收站。   */
  async function handleDeleteNode(item: StorageNode) {
    return handleDeleteNodes([item]);
  }

  /**
   * 批量将文件或文件夹移入回收站。   */
  async function handleDeleteNodes(targets: StorageNode[]) {
    if (!authToken || targets.length === 0) {
      return;
    }

    try {
      await deleteStorageNodes({ nodeIds: targets.map((target) => target.id) }, authToken);
      clearSelection();
      await Promise.all([loadDrive(), loadHealth()]);
      message.success(targets.length === 1 ? '已移入回收站。' : `已将 ${targets.length} 项移入回收站。`);
    } catch (deleteError) {
      message.error(deleteError instanceof Error ? deleteError.message : '删除失败。');
    }
  }

  /**
   * 从回收站恢复文件或文件夹。   */
  async function handleRestoreNode(item: StorageNode) {
    return handleRestoreNodes([item]);
  }

  /**
   * 批量从回收站恢复文件或文件夹。   */
  async function handleRestoreNodes(targets: StorageNode[]) {
    if (!authToken || targets.length === 0) {
      return;
    }

    try {
      await restoreStorageNodes({ nodeIds: targets.map((target) => target.id) }, authToken);
      clearSelection();
      await Promise.all([loadDrive(), loadHealth()]);
      message.success(targets.length === 1 ? '已恢复。' : `已恢复 ${targets.length} 项。`);
    } catch (restoreError) {
      message.error(restoreError instanceof Error ? restoreError.message : '恢复失败。');
    }
  }

  /**
   * 从回收站彻底删除文件或文件夹。   */
  async function handlePermanentlyDeleteNode(item: StorageNode) {
    return handlePermanentlyDeleteNodes([item]);
  }

  /**
   * 批量从回收站彻底删除文件或文件夹。   */
  async function handlePermanentlyDeleteNodes(targets: StorageNode[]) {
    if (!authToken || targets.length === 0) {
      return;
    }

    try {
      await permanentlyDeleteStorageNodes({ nodeIds: targets.map((target) => target.id) }, authToken);
      clearSelection();
      await Promise.all([loadDrive(), loadHealth()]);
      message.success(targets.length === 1 ? '已彻底删除。' : `已彻底删除 ${targets.length} 项。`);
    } catch (deleteError) {
      message.error(deleteError instanceof Error ? deleteError.message : '彻底删除失败。');
    }
  }

  /**
   * 提交新建文件夹表单，在当前目录下创建新的目录节点。   */
  async function submitCreateFolder(values: CreateFolderPayload) {
    if (!authToken) {
      return;
    }

    await createFolder(
      {
        parentId: currentFolderId,
        folderName: values.folderName,
      },
      authToken,
    );

    createFolderForm.resetFields();
    setCreateFolderOpen(false);
    clearSelection();
    await Promise.all([loadDrive(), loadHealth()]);
    message.success('文件夹创建成功。');
  }

  /**
   * 提交文件或文件夹重命名。   */
  async function submitRename(values: RenameNodePayload) {
    if (!authToken || !renameTarget) {
      return;
    }

    try {
      await renameStorageNode(renameTarget.id, { name: values.name }, authToken);
      setRenameTarget(null);
      renameForm.resetFields();
      clearSelection();
      await loadDrive();
      message.success('重命名成功。');
    } catch (renameError) {
      message.error(renameError instanceof Error ? renameError.message : '重命名失败。');
    }
  }

  /**
   * 提交单个或批量移动操作。   */
  async function submitMove(values: MoveNodeFormValues) {
    if (!authToken || moveTargets.length === 0) {
      return;
    }

    const parentId = values.parentKey === ROOT_PARENT_KEY ? null : Number(values.parentKey);
    const payload: BatchMoveNodePayload = {
      nodeIds: moveTargets.map((target) => target.id),
      parentId,
    };

    try {
      await moveStorageNodes(payload, authToken);
      setMoveTargets([]);
      moveForm.resetFields();
      clearSelection();
      await loadDrive();
      message.success(moveTargets.length === 1 ? '移动成功。' : `已移动 ${moveTargets.length} 项。`);
    } catch (moveError) {
      message.error(moveError instanceof Error ? moveError.message : '移动失败。');
    }
  }

  /**
   * 提交个人资料修改，并同步更新页面顶部显示的当前用户信息。   */
  async function submitProfile(values: UpdateProfilePayload) {
    if (!authToken) {
      return;
    }

    const submittedAvatarUrl = (values as Partial<UpdateProfilePayload>).avatarUrl;
    const avatarUrl =
      submittedAvatarUrl === undefined
        ? currentUser?.avatarUrl ?? null
        : submittedAvatarUrl?.trim()
          ? submittedAvatarUrl.trim()
          : null;

    const updatedUser = await updateProfile(
      {
        ...values,
        avatarUrl,
      },
      authToken,
    );

    updateCurrentUser(updatedUser);
    setProfileOpen(false);
    message.success('个人资料已更新。');
  }

  /**
   * 提交密码修改表单，要求用户先输入旧密码做校验。   */
  async function submitPassword(values: ChangePasswordPayload & { confirmPassword: string }) {
    if (!authToken) {
      return;
    }

    await changePassword(
      {
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      },
      authToken,
    );

    passwordForm.resetFields();
    setPasswordOpen(false);
    message.success('密码修改成功。');
  }

  /**
   * 提交管理员新增账号表单，并在成功后刷新账号列表。   */
  async function submitCreateUser(values: CreateUserFormValues) {
    if (!authToken) {
      return;
    }

    await createUser(
      {
        phoneNumber: values.phoneNumber,
        nickname: values.nickname,
        avatarUrl: values.avatarUrl?.trim() ? values.avatarUrl.trim() : null,
        password: values.password,
        role: values.role,
        storageQuotaBytes: values.role === 'ADMIN' ? null : gigabytesToBytes(values.storageQuotaGb),
      },
      authToken,
    );

    createUserForm.resetFields();
    setCreateUserOpen(false);
    await refreshCurrentView();
    message.success('账号创建成功。');
  }

  async function submitUserQuota(values: UpdateUserQuotaFormValues) {
    if (!authToken || !editQuotaTarget) {
      return;
    }

    if (editQuotaTarget.role === 'ADMIN') {
      setEditQuotaTarget(null);
      message.info('管理员账号不限制存储额度。');
      return;
    }

    const updatedUser = await updateUserStorageQuota(
      editQuotaTarget.id,
      {
        storageQuotaBytes: gigabytesToBytes(values.storageQuotaGb),
      },
      authToken,
    );

    if (updatedUser.id === currentUser?.id) {
      updateCurrentUser(updatedUser);
    }

    setEditQuotaTarget(null);
    await refreshCurrentView();
    message.success('用户最大额度已更新。');
  }

  /**
   * 主动退出登录，并回到登录页。   */
  function handleLogout() {
    clearCurrentSession();
    void navigate('/login', { replace: true });
  }

  const avatarMenuItems: MenuProps['items'] = [
    { key: 'profile', icon: <EditOutlined />, label: '个人资料' },
    { key: 'password', icon: <LockOutlined />, label: '修改密码' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  function handleAvatarMenuClick(event: { key: string }) {
    if (event.key === 'profile') {
      openProfileModal();
      return;
    }

    if (event.key === 'password') {
      openPasswordModal();
      return;
    }

    if (event.key === 'logout') {
      handleLogout();
    }
  }

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
                <span>{currentUser?.phoneNumber}</span>
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
      </Sider>

      <Layout>
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

            <Dropdown menu={{ items: avatarMenuItems, onClick: handleAvatarMenuClick }} trigger={['click']} placement="bottomRight">
              <button type="button" className="avatar-menu-button" aria-label="打开用户菜单">
                <Avatar size={44} src={currentAvatarSrc}>
                  {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'U'}
                </Avatar>
              </button>
            </Dropdown>
          </div>
        </Header>

        <Content className="app-content">
          <input
            ref={backgroundInputRef}
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            className="upload-input"
            onChange={(event) => void handleHomeBackgroundFileChange(event)}
          />

          {isHomeView ? (
            <StatusPanel
              health={health}
              overview={overview}
              usageHistory={usageHistory}
              backgroundImage={homeBackgroundImage}
              onChooseBackground={handleHomeBackgroundButtonClick}
              onClearBackground={clearHomeBackground}
            />
          ) : null}

          {isListView ? (
            <section className="content-panel drive-panel">
              <div className="panel-header panel-header-spacious">
                <div className="panel-title-copy">
                  <Typography.Title level={4}>{panelTitle}</Typography.Title>
                  <Typography.Paragraph className="panel-subtitle">{panelDescription}</Typography.Paragraph>
                </div>

                <div className="panel-actions">
                  {!isTrashView ? (
                    <>
                      <Button type="primary" icon={<UploadOutlined />} loading={uploading} onClick={handleUploadButtonClick}>
                        上传文件
                      </Button>
                      <Button icon={<FolderAddOutlined />} onClick={openCreateFolderModal}>
                        新建文件夹
                      </Button>
                      <input
                        ref={fileInputRef}
                        type="file"
                        multiple
                        className="upload-input"
                        onChange={(event) => void handleSelectedFiles(event)}
                      />
                    </>
                  ) : null}

                  <Button icon={<ReloadOutlined />} onClick={() => void refreshCurrentView()}>
                    刷新
                  </Button>

                  {selectedCount > 0 ? <span className="selection-pill">已选 {selectedCount} 项</span> : null}

                  {isTrashView ? (
                    <>
                      <Button icon={<RollbackOutlined />} disabled={selectedCount === 0} onClick={() => void handleRestoreNodes(selectedItems)}>
                        恢复所选
                      </Button>
                      <Popconfirm
                        title="彻底删除所选项目"
                        description="彻底删除后无法从回收站恢复。"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                        disabled={selectedCount === 0}
                        onConfirm={() => handlePermanentlyDeleteNodes(selectedItems)}
                      >
                        <Button danger icon={<DeleteOutlined />} disabled={selectedCount === 0}>
                          彻底删除所选
                        </Button>
                      </Popconfirm>
                    </>
                  ) : (
                    <>
                      <Button icon={<SwapOutlined />} disabled={selectedCount === 0} onClick={openBatchMoveModal}>
                        移动所选
                      </Button>
                      <Popconfirm
                        title="移入回收站"
                        description="可稍后在回收站中恢复或彻底删除。"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                        disabled={selectedCount === 0}
                        onConfirm={() => handleDeleteNodes(selectedItems)}
                      >
                        <Button danger icon={<DeleteOutlined />} disabled={selectedCount === 0}>
                          删除所选
                        </Button>
                      </Popconfirm>
                    </>
                  )}
                </div>
              </div>

              <div className="drive-toolbar">
                <div className="drive-toolbar-left">
                  {isTrashView ? (
                    <Typography.Text className="drive-toolbar-note">回收站里的项目支持恢复，也支持彻底删除。</Typography.Text>
                  ) : (
                    <Breadcrumb
                      items={breadcrumbs.map((crumb, index) => ({
                        title: (
                          <button className="crumb-button" onClick={() => jumpToCrumb(index)}>
                            {crumb.label}
                          </button>
                        ),
                      }))}
                    />
                  )}
                </div>

                <div className="drive-toolbar-right">
                  <Segmented<StorageNodeFilter>
                    value={nodeTypeFilter}
                    onChange={setNodeTypeFilter}
                    options={[
                      { label: '全部', value: 'ALL' },
                      { label: '文件夹', value: 'FOLDER' },
                      { label: '文件', value: 'FILE' },
                    ]}
                  />
                </div>
              </div>

              {error ? <Alert type="error" showIcon message="文件列表加载失败" description={error} /> : null}

              {uploadPanelVisible ? (
                <section className="upload-panel">
                  <div className="upload-panel-header">
                    <div>
                      <Typography.Title level={5}>上传队列</Typography.Title>
                      <Typography.Text className="muted-text">
                        {uploading
                          ? `正在处理 ${uploadTasks.length} 个文件`
                          : `成功 ${uploadSuccessCount} 个，失败 ${uploadFailedCount} 个，取消 ${uploadCanceledCount} 个`}
                      </Typography.Text>
                    </div>
                    <Space wrap>
                      {uploading ? (
                        <Button size="small" danger onClick={cancelActiveUploads}>
                          取消当前上传
                        </Button>
                      ) : null}
                      {uploadFailedCount > 0 ? (
                        <Button size="small" onClick={() => void retryFailedUploads()} disabled={uploading}>
                          继续失败项
                        </Button>
                      ) : null}
                      <Button size="small" onClick={clearUploadHistory} disabled={uploading}>
                        清除记录
                      </Button>
                    </Space>
                  </div>

                  <Progress
                    percent={overallUploadProgress}
                    status={uploadFailedCount > 0 && !uploading ? 'exception' : undefined}
                  />

                  <div className="upload-task-list">
                    {uploadTasks.map((task) => (
                      <div key={task.id} className="upload-task-row">
                        <div className="upload-task-main">
                          <div className="upload-task-name">{task.file.name}</div>
                          <Typography.Text className="muted-text">
                            {formatFileSize(task.totalBytes || task.file.size)}
                          </Typography.Text>
                        </div>

                        <div className="upload-task-side">
                          <Typography.Text className="muted-text">{getUploadTaskStatusText(task)}</Typography.Text>
                          {task.status === 'error' ? (
                            <Button size="small" type="link" onClick={() => void retryUploadTask(task.id)} disabled={uploading}>
                              继续
                            </Button>
                          ) : null}
                          {['uploading', 'retrying', 'completing'].includes(task.status) ? (
                            <Button size="small" type="link" danger onClick={() => cancelUploadTask(task.id)}>
                              取消
                            </Button>
                          ) : null}
                        </div>

                        <Progress
                          percent={task.status === 'success' ? 100 : task.progress}
                          size="small"
                          showInfo={false}
                          status={
                            task.status === 'error' || task.status === 'canceled'
                              ? 'exception'
                              : task.status === 'success'
                                ? 'success'
                                : 'active'
                          }
                        />

                        {task.error ? <div className="upload-task-error">{task.error}</div> : null}
                      </div>
                    ))}
                  </div>
                </section>
              ) : null}

              {loading ? (
                <div className="loading-box">
                  <Spin size="large" />
                </div>
              ) : (
                <StorageTable
                  mode={isTrashView ? 'trash' : 'drive'}
                  items={items}
                  loading={false}
                  downloadingFileId={downloadingFileId}
                  previewingFileId={previewingFileId}
                  selectedRowKeys={selectedRowKeys}
                  page={listState.page}
                  pageSize={listState.size}
                  totalItems={listState.totalItems}
                  sortBy={listState.sortBy}
                  sortDirection={listState.sortDirection}
                  onSelectionChange={handleSelectionChange}
                  onTableChange={handleTableChange}
                  onOpenFolder={openFolder}
                  onPreviewFile={handlePreviewFile}
                  onDownloadFile={handleDownloadFile}
                  onRenameNode={openRenameModal}
                  onMoveNode={openMoveModal}
                  onDeleteNode={handleDeleteNode}
                  onRestoreNode={handleRestoreNode}
                  onPermanentlyDeleteNode={handlePermanentlyDeleteNode}
                />
              )}
            </section>
          ) : null}

          {isAccountsView ? (
            isAdmin ? (
              <UserManagementPanel
                users={users}
                loading={usersLoading}
                onCreateUser={openCreateUserModal}
                onEditUserQuota={openEditUserQuotaModal}
              />
            ) : (
              <section className="content-panel">
                <Alert
                  type="warning"
                  showIcon
                  message="当前账号没有账号管理权限"
                  description="只有管理员可以新增和查看账号。"
                />
              </section>
            )
          ) : null}
        </Content>
      </Layout>

      <Modal
        title={previewTarget ? `预览：${previewTarget.name}` : '文件预览'}
        open={previewTarget !== null}
        width={960}
        onCancel={closePreviewModal}
        destroyOnHidden
        footer={
          previewTarget
            ? [
                <Button
                  key="download"
                  loading={downloadingFileId === previewTarget.id}
                  onClick={() => void handleDownloadFile(previewTarget)}
                >
                  下载文件
                </Button>,
                <Button key="close" type="primary" onClick={closePreviewModal}>
                  关闭
                </Button>,
              ]
            : null
        }
      >
        {renderPreviewContent()}
      </Modal>

      <Modal
        title="修改个人资料"
        open={profileOpen}
        onCancel={() => setProfileOpen(false)}
        onOk={() => void profileForm.submit()}
        destroyOnHidden
      >
        <Form form={profileForm} layout="vertical" onFinish={(values) => void submitProfile(values)}>
          <div className="profile-avatar-row">
            <Avatar size={64} src={currentAvatarSrc}>
              {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'U'}
            </Avatar>
            <Space wrap>
              <Button icon={<UploadOutlined />} loading={avatarUploading} onClick={handleAvatarButtonClick}>
                上传本地头像
              </Button>
              <input
                ref={avatarInputRef}
                type="file"
                accept="image/png,image/jpeg,image/gif,image/webp"
                className="upload-input"
                onChange={(event) => void handleAvatarFileChange(event)}
              />
            </Space>
          </div>
          <Form.Item
            name="phoneNumber"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号。' },
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="鏄电О"
            rules={[{ required: true, message: '请输入昵称。' }]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="修改密码"
        open={passwordOpen}
        onCancel={() => setPasswordOpen(false)}
        onOk={() => void passwordForm.submit()}
        destroyOnHidden
      >
        <Form form={passwordForm} layout="vertical" onFinish={(values) => void submitPassword(values)}>
          <Form.Item
            name="oldPassword"
            label="旧密码"
            rules={[{ required: true, message: '请输入旧密码。' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码。' },
              { min: 6, message: '密码长度至少为 6 位。' },
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码。' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }

                  return Promise.reject(new Error('两次输入的新密码不一致。'));
                },
              }),
            ]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增账号"
        open={createUserOpen}
        onCancel={() => setCreateUserOpen(false)}
        onOk={() => void createUserForm.submit()}
        destroyOnHidden
      >
        <Form form={createUserForm} layout="vertical" onFinish={(values) => void submitCreateUser(values)}>
          <Form.Item
            name="phoneNumber"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号。' },
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="鏄电О"
            rules={[{ required: true, message: '请输入昵称。' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="avatarUrl" label="头像地址">
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码。' },
              { min: 6, message: '密码长度至少为 6 位。' },
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="role"
            label="角色"
            rules={[{ required: true, message: '请选择角色。' }]}
          >
            <Select
              options={[
                { value: 'USER', label: '普通用户' },
                { value: 'ADMIN', label: '管理员' },
              ]}
            />
          </Form.Item>
          {createUserRole === 'ADMIN' ? (
            <Typography.Text className="muted-text">管理员账号不受个人配额限制。</Typography.Text>
          ) : (
            <Form.Item
              name="storageQuotaGb"
              label="最大额度（GB）"
              rules={[
                { required: true, message: '请输入最大额度。' },
                {
                  validator(_, value: number | undefined) {
                    if (value === undefined || value === null || value <= 0) {
                      return Promise.reject(new Error('最大额度必须大于 0。'));
                    }

                    return Promise.resolve();
                  },
                },
              ]}
              extra="用于限制该账户最多可上传的存储空间。"
            >
              <InputNumber min={0.1} step={0.25} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        title={editQuotaTarget ? `修改额度：${editQuotaTarget.nickname}` : '修改额度'}
        open={editQuotaTarget !== null}
        onCancel={() => setEditQuotaTarget(null)}
        onOk={() => void quotaForm.submit()}
        destroyOnHidden
      >
        <Form form={quotaForm} layout="vertical" onFinish={(values) => void submitUserQuota(values)}>
          {editQuotaTarget ? (
            <Space direction="vertical" size={4} style={{ display: 'flex', marginBottom: 16 }}>
              <Typography.Text className="muted-text">
                当前已用：{formatFileSize(editQuotaTarget.usedBytes)}
              </Typography.Text>
              <Typography.Text className="muted-text">
                当前剩余：{formatNullableBytes(editQuotaTarget.remainingBytes)}
              </Typography.Text>
            </Space>
          ) : null}
          <Form.Item
            name="storageQuotaGb"
            label="最大额度（GB）"
            rules={[
              { required: true, message: '请输入最大额度。' },
              {
                validator(_, value: number | undefined) {
                  if (value === undefined || value === null || value <= 0) {
                    return Promise.reject(new Error('最大额度必须大于 0。'));
                  }

                  return Promise.resolve();
                },
              },
            ]}
          >
            <InputNumber min={0.1} step={0.25} precision={2} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新建文件夹"
        open={createFolderOpen}
        onCancel={() => setCreateFolderOpen(false)}
        onOk={() => void createFolderForm.submit()}
        destroyOnHidden
      >
        <Form form={createFolderForm} layout="vertical" onFinish={(values) => void submitCreateFolder(values)}>
          <Form.Item
            name="folderName"
            label="文件夹名称"
            rules={[{ required: true, message: '请输入文件夹名称。' }]}
          >
            <Input placeholder="例如：项目资料" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重命名"
        open={renameTarget !== null}
        onCancel={() => setRenameTarget(null)}
        onOk={() => void renameForm.submit()}
        destroyOnHidden
      >
        <Form form={renameForm} layout="vertical" onFinish={(values) => void submitRename(values)}>
          <Form.Item
            name="name"
            label="名称"
            rules={[
              { required: true, message: '请输入名称。' },
              { max: 255, message: '名称长度不能超过 255 个字符。' },
            ]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={moveDialogTitle}
        open={moveTargets.length > 0}
        onCancel={() => setMoveTargets([])}
        onOk={() => void moveForm.submit()}
        destroyOnHidden
      >
        <Form form={moveForm} layout="vertical" onFinish={(values) => void submitMove(values)}>
          <Form.Item
            name="parentKey"
            label="目标文件夹"
            rules={[{ required: true, message: '请选择目标文件夹。' }]}
          >
            <TreeSelect
              showSearch
              treeDefaultExpandAll
              treeData={folderTreeData}
              treeNodeFilterProp="title"
              disabled={folderOptionsLoading}
              placeholder="选择目标文件夹"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
}

