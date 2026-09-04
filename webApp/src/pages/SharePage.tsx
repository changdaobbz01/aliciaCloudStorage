import { Download, File, FolderOpen, LogIn, Save, Smartphone } from 'lucide-react';
import { Alert, App as AntApp, Button, Card, Form, Input, Modal, Result, Space, Spin, Table, TreeSelect, Typography } from 'antd';
import type { TableProps } from 'antd';
import type { Key } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { AliciaModalTitle } from '../components/AliciaModalTitle';
import { RegulatoryFooter } from '../components/RegulatoryFooter';
import { publicAssetPath } from '../lib/appPaths';
import { useSession } from '../context/session-context';
import { Icon } from '../components/Icon';
import {
  downloadShareArchive,
  fetchShareFileAccessUrl,
  fetchStorageFolders,
  fetchPublicShareStatus,
  fetchShareDetail,
  isApiError,
  saveShareToDrive,
  verifySharePassword,
} from '../lib/api';
import type { ShareLinkDetail, ShareLinkStatus, StorageNode, VerifySharePasswordPayload } from '../types';
import { ROOT_PARENT_KEY } from '../features/drive/driveShared';
import { parseFolderParentKey, validateArchiveNodeIds, validateShareSaveNodeIds } from '../features/drive/cloudOperationPolicy';
import { collapseSelectedShareNodeIds } from '../features/drive/shareTreeSelection';
import type { FolderTreeNode } from '../features/drive/types';
import { buildAppDownloadUrl, buildShareIntentUrl } from '../lib/mobileApp';
import { cloudReturnTo, redirectToUnifiedLogin } from '../lib/unifiedLogin';

type ShareTreeNode = StorageNode & {
  children?: ShareTreeNode[];
};

type StoredShareAccess = {
  accessToken: string;
  expiresAt: string;
};

type ShareMobileOpenAction = 'intent' | 'download';

const SHARE_CODE_PATTERN = /^[A-Za-z0-9_-]{4,40}$/;

function isLikelyMobileClient() {
  if (typeof window === 'undefined') {
    return false;
  }

  return /Android|iPhone|iPad|iPod|Mobile/i.test(window.navigator.userAgent) ||
    window.matchMedia('(max-width: 760px)').matches;
}

function getShareAccessStorageKey(shareCode: string) {
  return `alicia-cloud-storage.share-access.${shareCode}`;
}

function loadStoredShareAccess(shareCode: string) {
  const raw = sessionStorage.getItem(getShareAccessStorageKey(shareCode));
  if (!raw) {
    return null;
  }

  try {
    const stored = JSON.parse(raw) as StoredShareAccess;
    if (!stored.accessToken || !stored.expiresAt || new Date(stored.expiresAt).getTime() <= Date.now()) {
      sessionStorage.removeItem(getShareAccessStorageKey(shareCode));
      return null;
    }

    return stored.accessToken;
  } catch {
    sessionStorage.removeItem(getShareAccessStorageKey(shareCode));
    return null;
  }
}

function saveStoredShareAccess(shareCode: string, accessToken: string, expiresAt: string) {
  sessionStorage.setItem(getShareAccessStorageKey(shareCode), JSON.stringify({ accessToken, expiresAt }));
}

function clearStoredShareAccess(shareCode: string) {
  sessionStorage.removeItem(getShareAccessStorageKey(shareCode));
}

function formatBytes(value: number) {
  if (value === 0) {
    return '-';
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

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '永久有效';
}

function isShareAccessInvalidError(error: unknown) {
  return isApiError(error) && error.status === 400 && /提取码|访问凭证/.test(error.message);
}

function triggerLinkDownload(url: string, fileName?: string | null) {
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.rel = 'noreferrer';
  if (fileName) {
    anchor.download = fileName;
  }
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
}

function triggerBlobDownload(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob);
  triggerLinkDownload(objectUrl, fileName);
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

function buildShareTree(detail: ShareLinkDetail | null): ShareTreeNode[] {
  if (!detail) {
    return [];
  }

  const nodeMap = new Map<number, ShareTreeNode>();
  detail.items.forEach((item) => {
    nodeMap.set(item.id, { ...item, children: [] });
  });

  const rootIdSet = new Set(detail.rootNodeIds);
  const roots: ShareTreeNode[] = [];

  detail.items.forEach((item) => {
    const current = nodeMap.get(item.id);
    if (!current) {
      return;
    }

    const parent = item.parentId === null ? null : nodeMap.get(item.parentId);
    if (parent && !rootIdSet.has(item.id)) {
      parent.children = parent.children ?? [];
      parent.children.push(current);
      return;
    }

    roots.push(current);
  });

  const sortNodes = (nodes: ShareTreeNode[]) => {
    nodes.sort((left, right) => {
      if (left.type !== right.type) {
        return left.type === 'FOLDER' ? -1 : 1;
      }

      return left.name.localeCompare(right.name, 'zh-CN');
    });
    nodes.forEach((node) => {
      if (node.children?.length) {
        sortNodes(node.children);
      } else {
        delete node.children;
      }
    });
  };

  sortNodes(roots);
  return roots;
}

function buildFolderTree(folders: StorageNode[]): FolderTreeNode[] {
  const childrenMap = new Map<number | null, StorageNode[]>();

  folders.forEach((folder) => {
    const siblings = childrenMap.get(folder.parentId) ?? [];
    siblings.push(folder);
    childrenMap.set(folder.parentId, siblings);
  });

  const buildTree = (parentId: number | null): FolderTreeNode[] =>
    (childrenMap.get(parentId) ?? [])
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
}

export function SharePage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const { shareCode = '' } = useParams();
  const normalizedShareCode = useMemo(() => shareCode.trim(), [shareCode]);
  const shareCodeValid = SHARE_CODE_PATTERN.test(normalizedShareCode);
  const { authToken, currentUser, isSessionChecking } = useSession();
  const [passwordForm] = Form.useForm<VerifySharePasswordPayload>();
  const [status, setStatus] = useState<ShareLinkStatus | null>(null);
  const [detail, setDetail] = useState<ShareLinkDetail | null>(null);
  const [shareAccessToken, setShareAccessToken] = useState<string | null>(() => loadStoredShareAccess(normalizedShareCode));
  const [statusLoading, setStatusLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [passwordChecking, setPasswordChecking] = useState(false);
  const [saving, setSaving] = useState(false);
  const [downloadingNodeId, setDownloadingNodeId] = useState<number | null>(null);
  const [downloadingSelection, setDownloadingSelection] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [showMobileOpenHint, setShowMobileOpenHint] = useState(false);
  const [mobileOpenAction, setMobileOpenAction] = useState<ShareMobileOpenAction | null>(null);
  const [saveTargetOpen, setSaveTargetOpen] = useState(false);
  const [saveFolderOptions, setSaveFolderOptions] = useState<StorageNode[]>([]);
  const [saveFolderOptionsLoading, setSaveFolderOptionsLoading] = useState(false);
  const [saveParentKey, setSaveParentKey] = useState(ROOT_PARENT_KEY);
  const [selectedShareRowKeys, setSelectedShareRowKeys] = useState<Key[]>([]);
  const passwordCheckingRef = useRef(false);
  const savingRef = useRef(false);
  const downloadingNodeIdRef = useRef<number | null>(null);
  const downloadingSelectionRef = useRef(false);
  const saveFolderOptionsLoadingRef = useRef(false);
  const mobileOpenActionRef = useRef<ShareMobileOpenAction | null>(null);
  const shareStatusRequestIdRef = useRef(0);
  const shareStatusLoadingKeyRef = useRef<string | null>(null);
  const shareDetailRequestIdRef = useRef(0);
  const shareDetailLoadingKeyRef = useRef<string | null>(null);
  const shareCodeRef = useRef(normalizedShareCode);
  const authTokenRef = useRef(authToken);
  const shareAccessTokenRef = useRef(shareAccessToken);
  const shareStatusRef = useRef(status);
  shareCodeRef.current = normalizedShareCode;
  authTokenRef.current = authToken;
  shareAccessTokenRef.current = shareAccessToken;
  shareStatusRef.current = status;
  const shareTree = useMemo(() => buildShareTree(detail), [detail]);
  const saveFolderTreeData = useMemo(() => buildFolderTree(saveFolderOptions), [saveFolderOptions]);
  const selectedShareNodeIds = useMemo(
    () => selectedShareRowKeys.map(Number).filter((nodeId) => Number.isFinite(nodeId)),
    [selectedShareRowKeys],
  );
  const selectedShareRootNodeIds = useMemo(
    () => collapseSelectedShareNodeIds(detail?.items ?? [], selectedShareNodeIds),
    [detail, selectedShareNodeIds],
  );

  useEffect(() => {
    const shareTitle = detail?.title.trim();
    document.title = shareTitle ? `${shareTitle} - Alicia 云盘分享` : '分享文件 - Alicia 云盘';
  }, [detail?.title]);

  useEffect(() => {
    shareStatusRequestIdRef.current += 1;
    shareStatusLoadingKeyRef.current = null;
    shareDetailRequestIdRef.current += 1;
    shareDetailLoadingKeyRef.current = null;
    setShareAccessToken(loadStoredShareAccess(normalizedShareCode));
    setStatus(null);
    setDetail(null);
    setDetailLoading(false);
    setSelectedShareRowKeys([]);
    setShowMobileOpenHint(shareCodeValid && isLikelyMobileClient());
    mobileOpenActionRef.current = null;
    setMobileOpenAction(null);
  }, [normalizedShareCode, shareCodeValid]);

  function createShareStatusRequestKey(code = normalizedShareCode) {
    return JSON.stringify([code]);
  }

  function isCurrentShareStatusRequest(requestId: number, requestKey: string) {
    return (
      shareStatusRequestIdRef.current === requestId
      && shareStatusLoadingKeyRef.current === requestKey
      && createShareStatusRequestKey(shareCodeRef.current) === requestKey
    );
  }

  useEffect(() => {
    async function loadStatus() {
      const requestKey = createShareStatusRequestKey();
      if (shareStatusLoadingKeyRef.current === requestKey) {
        return;
      }

      shareStatusRequestIdRef.current += 1;
      const requestId = shareStatusRequestIdRef.current;
      shareStatusLoadingKeyRef.current = requestKey;
      setStatusLoading(true);
      setPageError(null);

      if (!shareCodeValid) {
        setStatus(null);
        setPageError('分享链接格式不正确。');
        shareStatusLoadingKeyRef.current = null;
        setStatusLoading(false);
        return;
      }

      try {
        const nextStatus = await fetchPublicShareStatus(normalizedShareCode);
        if (!isCurrentShareStatusRequest(requestId, requestKey)) {
          return;
        }

        setStatus(nextStatus);
      } catch (error) {
        if (isCurrentShareStatusRequest(requestId, requestKey)) {
          setStatus(null);
          setPageError(error instanceof Error ? error.message : '分享链接不可用。');
        }
      } finally {
        if (isCurrentShareStatusRequest(requestId, requestKey)) {
          shareStatusLoadingKeyRef.current = null;
          setStatusLoading(false);
        }
      }
    }

    void loadStatus();

    return () => {
      shareStatusRequestIdRef.current += 1;
      shareStatusLoadingKeyRef.current = null;
    };
  }, [normalizedShareCode, shareCodeValid]);

  function createShareDetailRequestKey(
    currentStatus = status,
    code = normalizedShareCode,
    token = authToken,
    accessToken = shareAccessToken,
  ) {
    return JSON.stringify([
      code,
      token,
      accessToken,
      currentStatus?.shareCode ?? null,
      currentStatus?.available ?? false,
      currentStatus?.requiresPassword ?? false,
    ]);
  }

  function isCurrentShareDetailRequest(requestId: number, requestKey: string) {
    return (
      shareDetailRequestIdRef.current === requestId
      && shareDetailLoadingKeyRef.current === requestKey
      && createShareDetailRequestKey(
        shareStatusRef.current,
        shareCodeRef.current,
        authTokenRef.current,
        shareAccessTokenRef.current,
      ) === requestKey
    );
  }

  function invalidateShareDetailRequest(clearDetail = false) {
    shareDetailRequestIdRef.current += 1;
    shareDetailLoadingKeyRef.current = null;
    setDetailLoading(false);

    if (clearDetail) {
      setDetail(null);
    }
  }

  useEffect(() => {
    if (!status?.available || isSessionChecking || !authToken || !currentUser) {
      invalidateShareDetailRequest(!status?.available || !authToken || !currentUser);
      return;
    }

    if (status.requiresPassword && !shareAccessToken) {
      invalidateShareDetailRequest(true);
      return;
    }

    const currentStatus = status;

    async function loadDetail() {
      const requestKey = createShareDetailRequestKey(currentStatus);
      if (shareDetailLoadingKeyRef.current === requestKey) {
        return;
      }

      shareDetailRequestIdRef.current += 1;
      const requestId = shareDetailRequestIdRef.current;
      shareDetailLoadingKeyRef.current = requestKey;
      setDetailLoading(true);
      setPageError(null);

      try {
        const nextDetail = await fetchShareDetail(normalizedShareCode, authToken!, shareAccessToken);
        if (!isCurrentShareDetailRequest(requestId, requestKey)) {
          return;
        }

        setDetail(nextDetail);
      } catch (error) {
        if (isCurrentShareDetailRequest(requestId, requestKey)) {
          if (currentStatus.requiresPassword && isShareAccessInvalidError(error)) {
            clearStoredShareAccess(normalizedShareCode);
            setShareAccessToken(null);
          }
          setDetail(null);
          setPageError(error instanceof Error ? error.message : '分享详情加载失败。');
        }
      } finally {
        if (isCurrentShareDetailRequest(requestId, requestKey)) {
          shareDetailLoadingKeyRef.current = null;
          setDetailLoading(false);
        }
      }
    }

    void loadDetail();

    return () => {
      shareDetailRequestIdRef.current += 1;
      shareDetailLoadingKeyRef.current = null;
    };
  }, [authToken, currentUser, isSessionChecking, normalizedShareCode, shareAccessToken, status]);

  function resetShareAccessIfNeeded(error: unknown) {
    if (!status?.requiresPassword || !isShareAccessInvalidError(error)) {
      return false;
    }

    clearStoredShareAccess(normalizedShareCode);
    setShareAccessToken(null);
    setDetail(null);
    return true;
  }

  async function handlePasswordSubmit(values: VerifySharePasswordPayload) {
    if (passwordCheckingRef.current) {
      return;
    }

    passwordCheckingRef.current = true;
    setPasswordChecking(true);

    try {
      const response = await verifySharePassword(normalizedShareCode, values);
      if (response.accessToken && response.expiresAt) {
        saveStoredShareAccess(normalizedShareCode, response.accessToken, response.expiresAt);
        setShareAccessToken(response.accessToken);
      }
      passwordForm.resetFields();
      message.success('提取码校验通过。');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提取码校验失败。');
    } finally {
      passwordCheckingRef.current = false;
      setPasswordChecking(false);
    }
  }

  function goLogin() {
    redirectToUnifiedLogin(cloudReturnTo(location.pathname, location.search, location.hash), false);
  }

  function scheduleMobileOpenReset(action: ShareMobileOpenAction) {
    window.setTimeout(() => {
      if (mobileOpenActionRef.current !== action) {
        return;
      }

      mobileOpenActionRef.current = null;
      setMobileOpenAction(null);
    }, 1500);
  }

  function beginMobileOpenAction(action: ShareMobileOpenAction) {
    if (mobileOpenActionRef.current !== null) {
      return false;
    }

    mobileOpenActionRef.current = action;
    setMobileOpenAction(action);
    return true;
  }

  function openInAndroidApp() {
    if (!beginMobileOpenAction('intent')) {
      return;
    }

    window.location.assign(buildShareIntentUrl(normalizedShareCode));
    scheduleMobileOpenReset('intent');
  }

  function openAppDownloadPage() {
    if (!beginMobileOpenAction('download')) {
      return;
    }

    window.location.assign(buildAppDownloadUrl(normalizedShareCode));
    scheduleMobileOpenReset('download');
  }

  function hideMobileOpenHint() {
    if (mobileOpenActionRef.current !== null) {
      return;
    }

    setShowMobileOpenHint(false);
  }

  async function loadSaveFolderOptions() {
    if (!authToken || saveFolderOptionsLoadingRef.current) {
      return;
    }

    saveFolderOptionsLoadingRef.current = true;
    setSaveFolderOptionsLoading(true);

    try {
      setSaveFolderOptions(await fetchStorageFolders(authToken));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载文件夹目录失败。');
    } finally {
      saveFolderOptionsLoadingRef.current = false;
      setSaveFolderOptionsLoading(false);
    }
  }

  function closeSaveTargetModal() {
    if (savingRef.current) {
      return;
    }

    setSaveTargetOpen(false);
  }

  function openSaveTargetModal() {
    if (!authToken || !detail || savingRef.current || downloadingSelectionRef.current || downloadingNodeIdRef.current !== null) {
      return;
    }

    const saveSelection = validateShareSaveNodeIds(selectedShareRootNodeIds);
    if (!saveSelection.valid) {
      message.warning(saveSelection.message);
      return;
    }

    setSaveParentKey(ROOT_PARENT_KEY);
    setSaveTargetOpen(true);
    void loadSaveFolderOptions();
  }

  async function handleSaveShare() {
    if (!authToken || !detail || savingRef.current || downloadingSelectionRef.current || downloadingNodeIdRef.current !== null) {
      return;
    }

    const saveSelection = validateShareSaveNodeIds(selectedShareRootNodeIds);
    if (!saveSelection.valid) {
      message.warning(saveSelection.message);
      return;
    }

    const parent = parseFolderParentKey(saveParentKey, ROOT_PARENT_KEY);
    if (!parent.valid) {
      message.warning(parent.message);
      return;
    }

    savingRef.current = true;
    setSaving(true);

    try {
      await saveShareToDrive(
        detail.shareCode,
        { parentId: parent.value, selectedNodeIds: saveSelection.value },
        authToken,
        shareAccessToken,
      );
      message.success(parent.value === null ? '已保存到你的网盘根目录。' : '已保存到选定文件夹。');
      setSaveTargetOpen(false);
      setSelectedShareRowKeys([]);
      void navigate('/');
    } catch (error) {
      if (resetShareAccessIfNeeded(error)) {
        setSaveTargetOpen(false);
        message.warning('提取码凭证已失效，请重新输入。');
      } else {
        message.error(error instanceof Error ? error.message : '保存失败。');
      }
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  async function handleDownloadFile(item: ShareTreeNode) {
    if (!authToken || !detail || savingRef.current || downloadingSelectionRef.current || downloadingNodeIdRef.current !== null) {
      return;
    }

    if (!detail.allowDownload) {
      message.warning('分享者未开放下载权限。');
      return;
    }

    downloadingNodeIdRef.current = item.id;
    setDownloadingNodeId(item.id);

    try {
      const access = await fetchShareFileAccessUrl(
        detail.shareCode,
        item.id,
        authToken,
        shareAccessToken,
        'attachment',
      );
      triggerLinkDownload(access.url, access.fileName ?? item.name);
      message.success('已开始下载。');
    } catch (error) {
      if (resetShareAccessIfNeeded(error)) {
        message.warning('提取码凭证已失效，请重新输入。');
      } else {
        message.error(error instanceof Error ? error.message : '下载失败。');
      }
    } finally {
      downloadingNodeIdRef.current = null;
      setDownloadingNodeId(null);
    }
  }

  async function handleDownloadArchive(nodeIds: number[], busyNodeId: number | null = null) {
    if (!authToken || !detail || savingRef.current || downloadingSelectionRef.current || downloadingNodeIdRef.current !== null) {
      return;
    }

    if (!detail.allowDownload) {
      message.warning('分享者未开放下载权限。');
      return;
    }

    const archiveSelection = validateArchiveNodeIds(nodeIds, '请先选择要下载的分享内容。');
    if (!archiveSelection.valid) {
      message.warning(archiveSelection.message);
      return;
    }

    if (busyNodeId === null) {
      downloadingSelectionRef.current = true;
      setDownloadingSelection(true);
    } else {
      downloadingNodeIdRef.current = busyNodeId;
      setDownloadingNodeId(busyNodeId);
    }

    try {
      const { blob, fileName } = await downloadShareArchive(
        detail.shareCode,
        { nodeIds: archiveSelection.value },
        authToken,
        shareAccessToken,
      );
      triggerBlobDownload(blob, fileName ?? `${detail.title}.zip`);
      message.success('压缩包已开始下载。');
    } catch (error) {
      if (resetShareAccessIfNeeded(error)) {
        message.warning('提取码凭证已失效，请重新输入。');
      } else {
        message.error(error instanceof Error ? error.message : '下载失败。');
      }
    } finally {
      if (busyNodeId === null) {
        downloadingSelectionRef.current = false;
        setDownloadingSelection(false);
      } else {
        downloadingNodeIdRef.current = null;
        setDownloadingNodeId(null);
      }
    }
  }

  const columns: TableProps<ShareTreeNode>['columns'] = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 420,
      render: (_, item) => {
        const meta = item.type === 'FOLDER' ? '文件夹' : item.extension ? item.extension.toUpperCase() : '文件';

        return (
          <div className="storage-name-cell">
            <span className={`storage-icon-shell${item.type === 'FOLDER' ? ' storage-folder-icon' : ''}`}>
              {item.type === 'FOLDER' ? <Icon icon={FolderOpen} /> : <Icon icon={File} />}
            </span>
            <div className="storage-name-copy">
              <Typography.Text strong={item.type === 'FOLDER'} ellipsis={{ tooltip: item.name }} className="storage-name-title">
                {item.name}
              </Typography.Text>
              <Typography.Text className="storage-name-meta">{meta}</Typography.Text>
            </div>
          </div>
        );
      },
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 140,
      render: (value: number) => formatBytes(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 200,
      render: (value: string) => formatTimestamp(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, item) => {
        if (!detail?.allowDownload) {
          return <Typography.Text className="muted-text">-</Typography.Text>;
        }

        return (
          <Button
            type="link"
            icon={<Icon icon={Download} />}
            loading={downloadingNodeId === item.id}
            disabled={saving || downloadingSelection || (downloadingNodeId !== null && downloadingNodeId !== item.id)}
            onClick={(event) => {
              event.stopPropagation();
              if (item.type === 'FILE') {
                void handleDownloadFile(item);
              } else {
                void handleDownloadArchive([item.id], item.id);
              }
            }}
          >
            {item.type === 'FILE' ? '下载' : '打包下载'}
          </Button>
        );
      },
    },
  ];

  let content = null;

  if (statusLoading || isSessionChecking) {
    content = (
      <div className="loading-box">
        <Spin size="large" />
      </div>
    );
  } else if (pageError && !status) {
    content = <Result status="404" title="分享链接不可用" subTitle={pageError} />;
  } else if (status && !status.available) {
    content = (
      <Result
        status="warning"
        title={status.reason === 'EXPIRED' ? '分享链接已过期' : '分享链接已取消'}
        subTitle="请联系分享者重新创建链接。"
      />
    );
  } else if (status?.requiresPassword && !shareAccessToken) {
    content = (
      <Card className="share-gate-card" bordered={false}>
        <Typography.Title level={3}>请输入提取码</Typography.Title>
        <Typography.Paragraph className="panel-subtitle">
          这个分享设置了访问保护，通过校验后可继续登录查看。
        </Typography.Paragraph>
        <Form form={passwordForm} layout="vertical" disabled={passwordChecking} onFinish={(values) => void handlePasswordSubmit(values)}>
          <Form.Item name="password" label="提取码" rules={[{ required: true, message: '请输入提取码。' }]}>
            <Input.Password autoFocus placeholder="请输入提取码" />
          </Form.Item>
          <Button type="primary" htmlType="button" loading={passwordChecking} disabled={passwordChecking} onClick={() => void passwordForm.submit()}>
            校验提取码
          </Button>
        </Form>
      </Card>
    );
  } else if (!authToken || !currentUser) {
    content = (
      <Card className="share-gate-card" bordered={false}>
        <Typography.Title level={3}>登录后查看分享详情</Typography.Title>
        <Typography.Paragraph className="panel-subtitle">
          Alicia 云盘需要确认访问账号后，才能展示文件详情并执行保存或下载。
        </Typography.Paragraph>
        <Button type="primary" icon={<Icon icon={LogIn} />} onClick={goLogin}>
          去登录
        </Button>
      </Card>
    );
  } else if (detailLoading || !detail) {
    content = (
      <div className="loading-box">
        <Spin size="large" />
      </div>
    );
  } else {
    content = (
      <section className="content-panel share-detail-panel">
        <div className="panel-header panel-header-spacious">
          <div className="panel-title-copy">
            <Typography.Text className="header-eyebrow">来自 {detail.ownerNickname} 的分享</Typography.Text>
            <Typography.Title level={3}>{detail.title}</Typography.Title>
            <Typography.Paragraph className="panel-subtitle">
              有效期：{formatTimestamp(detail.expiresAt)}
            </Typography.Paragraph>
          </div>
          <div className="panel-actions">
            {detail.allowDownload ? (
              <Button
                icon={<Icon icon={Download} />}
                loading={downloadingSelection}
                disabled={selectedShareRootNodeIds.length === 0 || saving || downloadingNodeId !== null}
                onClick={() => void handleDownloadArchive(selectedShareRootNodeIds)}
              >
                下载选中
              </Button>
            ) : null}
            {detail.allowSave ? (
              <Button
                type="primary"
                icon={<Icon icon={Save} />}
                loading={saving}
                disabled={selectedShareRootNodeIds.length === 0 || saving || downloadingSelection || downloadingNodeId !== null}
                onClick={openSaveTargetModal}
              >
                保存到我的网盘
              </Button>
            ) : null}
          </div>
        </div>

        {!detail.allowDownload ? (
          <Alert type="info" showIcon message="分享者未开放下载权限。" className="share-detail-alert" />
        ) : null}

        <Table
          rowKey="id"
          className="storage-data-table"
          columns={columns}
          dataSource={shareTree}
          pagination={false}
          rowSelection={
            detail.allowSave || detail.allowDownload
              ? {
                  selectedRowKeys: selectedShareRowKeys,
                  checkStrictly: false,
                  onChange: (nextSelectedRowKeys) => setSelectedShareRowKeys(nextSelectedRowKeys),
                  getCheckboxProps: () => ({ disabled: saving || downloadingSelection || downloadingNodeId !== null }),
                }
              : undefined
          }
          scroll={{ x: 1060 }}
          expandable={{
            defaultExpandAllRows: false,
            defaultExpandedRowKeys: [],
            expandRowByClick: true,
            rowExpandable: (item) => item.type === 'FOLDER' && Boolean(item.children?.length),
          }}
          onRow={(item) => ({
            className: item.type === 'FOLDER' && item.children?.length ? 'share-expandable-row' : '',
          })}
          locale={{ emptyText: '分享内容暂不可用。' }}
        />
      </section>
    );
  }

  return (
    <div className="share-page-shell">
      <header className="share-page-header">
        <button
          type="button"
          className="brand-block share-brand-block share-brand-link"
          aria-label="进入 Alicia 云盘主页"
          onClick={() => void navigate('/')}
        >
          <img src={publicAssetPath('/apple-touch-icon.png')} alt="" className="brand-icon brand-icon-image" />
          <div>
            <Typography.Title level={4}>Alicia 云盘</Typography.Title>
            <Typography.Text>文件分享</Typography.Text>
          </div>
        </button>
      </header>

      {showMobileOpenHint ? (
        <section className="share-mobile-open-banner" aria-label="移动端打开方式">
          <div className="share-mobile-open-copy">
            <Icon icon={Smartphone} />
            <div>
              <Typography.Text strong>Alicia 云盘 App</Typography.Text>
              <Typography.Text className="muted-text">已安装可直接打开；未安装可下载安装包。</Typography.Text>
            </div>
          </div>
          <Space className="share-mobile-open-actions" wrap>
            <Button
              type="primary"
              size="small"
              icon={<Icon icon={Smartphone} />}
              loading={mobileOpenAction === 'intent'}
              disabled={mobileOpenAction === 'download'}
              onClick={openInAndroidApp}
            >
              打开 App
            </Button>
            <Button
              size="small"
              loading={mobileOpenAction === 'download'}
              disabled={mobileOpenAction === 'intent'}
              onClick={openAppDownloadPage}
            >
              下载 App
            </Button>
            <Button type="text" size="small" disabled={mobileOpenAction !== null} onClick={hideMobileOpenHint}>
              继续网页查看
            </Button>
          </Space>
        </section>
      ) : null}

      <main className="share-page-main">{content}</main>

      <Modal
        title={<AliciaModalTitle eyebrow="Share">选择保存位置</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-share-modal"
        open={saveTargetOpen}
        onCancel={closeSaveTargetModal}
        onOk={() => void handleSaveShare()}
        okText="保存到这里"
        cancelText="取消"
        confirmLoading={saving}
        maskClosable={!saving}
        closable={!saving}
        cancelButtonProps={{ disabled: saving }}
        destroyOnHidden
      >
        <Typography.Paragraph className="panel-subtitle">
          请选择分享内容要保存到的网盘文件夹。
        </Typography.Paragraph>
        <TreeSelect
          showSearch
          treeDefaultExpandAll
          treeData={saveFolderTreeData}
          treeNodeFilterProp="title"
          disabled={saveFolderOptionsLoading || saving}
          loading={saveFolderOptionsLoading}
          value={saveParentKey}
          onChange={(value) => setSaveParentKey(value)}
          placeholder="选择目标文件夹"
          style={{ width: '100%' }}
        />
      </Modal>

      <footer className="share-page-footer">
        <RegulatoryFooter />
      </footer>
    </div>
  );
}
