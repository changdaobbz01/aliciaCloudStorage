import {
  AndroidOutlined,
  CloudDownloadOutlined,
  CloudServerOutlined,
  FileOutlined,
  FolderOpenFilled,
  LoginOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { Alert, App as AntApp, Button, Card, Form, Input, Result, Space, Spin, Table, Typography } from 'antd';
import type { TableProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { RegulatoryFooter } from '../components/RegulatoryFooter';
import { useSession } from '../context/session-context';
import {
  fetchPublicShareStatus,
  fetchShareDetail,
  fetchShareFileAccessUrl,
  isApiError,
  saveShareToDrive,
  verifySharePassword,
} from '../lib/api';
import type { ShareLinkDetail, ShareLinkStatus, StorageNode, VerifySharePasswordPayload } from '../types';
import { resolveShareUrl } from '../features/drive/driveShared';

type ShareTreeNode = StorageNode & {
  children?: ShareTreeNode[];
};

const ANDROID_PACKAGE_NAME = 'com.alicia.cloudstorage.phone';

type StoredShareAccess = {
  accessToken: string;
  expiresAt: string;
};

function isLikelyMobileClient() {
  if (typeof window === 'undefined') {
    return false;
  }

  return /Android|iPhone|iPad|iPod|Mobile/i.test(window.navigator.userAgent) ||
    window.matchMedia('(max-width: 760px)').matches;
}

function buildAppDownloadUrl(shareCode: string) {
  const url = new URL('/app-download', window.location.origin);
  url.searchParams.set('share', shareCode);
  return url.toString();
}

function buildShareIntentUrl(shareCode: string) {
  return `intent://share/${encodeURIComponent(shareCode)}#Intent;scheme=aliciacloud;package=${ANDROID_PACKAGE_NAME};S.browser_fallback_url=${encodeURIComponent(
    buildAppDownloadUrl(shareCode),
  )};S.alicia_web_url=${encodeURIComponent(resolveShareUrl(shareCode))};end`;
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

export function SharePage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const { shareCode = '' } = useParams();
  const { authToken, currentUser, isSessionChecking } = useSession();
  const [passwordForm] = Form.useForm<VerifySharePasswordPayload>();
  const [status, setStatus] = useState<ShareLinkStatus | null>(null);
  const [detail, setDetail] = useState<ShareLinkDetail | null>(null);
  const [shareAccessToken, setShareAccessToken] = useState<string | null>(() => loadStoredShareAccess(shareCode));
  const [statusLoading, setStatusLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [passwordChecking, setPasswordChecking] = useState(false);
  const [saving, setSaving] = useState(false);
  const [downloadingFileId, setDownloadingFileId] = useState<number | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [showMobileOpenHint, setShowMobileOpenHint] = useState(false);
  const shareTree = useMemo(() => buildShareTree(detail), [detail]);

  useEffect(() => {
    setShareAccessToken(loadStoredShareAccess(shareCode));
    setDetail(null);
    setShowMobileOpenHint(Boolean(shareCode) && isLikelyMobileClient());
  }, [shareCode]);

  useEffect(() => {
    let cancelled = false;

    async function loadStatus() {
      setStatusLoading(true);
      setPageError(null);

      try {
        const nextStatus = await fetchPublicShareStatus(shareCode);
        if (!cancelled) {
          setStatus(nextStatus);
        }
      } catch (error) {
        if (!cancelled) {
          setStatus(null);
          setPageError(error instanceof Error ? error.message : '分享链接不可用。');
        }
      } finally {
        if (!cancelled) {
          setStatusLoading(false);
        }
      }
    }

    void loadStatus();

    return () => {
      cancelled = true;
    };
  }, [shareCode]);

  useEffect(() => {
    if (!status?.available || isSessionChecking || !authToken || !currentUser) {
      return;
    }

    if (status.requiresPassword && !shareAccessToken) {
      return;
    }

    let cancelled = false;
    const currentStatus = status;

    async function loadDetail() {
      setDetailLoading(true);
      setPageError(null);

      try {
        const nextDetail = await fetchShareDetail(shareCode, authToken!, shareAccessToken);
        if (!cancelled) {
          setDetail(nextDetail);
        }
      } catch (error) {
        if (!cancelled) {
          if (currentStatus.requiresPassword && isApiError(error) && error.status === 400) {
            clearStoredShareAccess(shareCode);
            setShareAccessToken(null);
          }
          setDetail(null);
          setPageError(error instanceof Error ? error.message : '分享详情加载失败。');
        }
      } finally {
        if (!cancelled) {
          setDetailLoading(false);
        }
      }
    }

    void loadDetail();

    return () => {
      cancelled = true;
    };
  }, [authToken, currentUser, isSessionChecking, shareAccessToken, shareCode, status]);

  async function handlePasswordSubmit(values: VerifySharePasswordPayload) {
    setPasswordChecking(true);

    try {
      const response = await verifySharePassword(shareCode, values);
      if (response.accessToken && response.expiresAt) {
        saveStoredShareAccess(shareCode, response.accessToken, response.expiresAt);
        setShareAccessToken(response.accessToken);
      }
      passwordForm.resetFields();
      message.success('提取码校验通过。');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提取码校验失败。');
    } finally {
      setPasswordChecking(false);
    }
  }

  function goLogin() {
    void navigate('/login', { state: { from: location.pathname }, replace: false });
  }

  function openInAndroidApp() {
    window.location.href = buildShareIntentUrl(shareCode);
  }

  function openAppDownloadPage() {
    window.location.href = buildAppDownloadUrl(shareCode);
  }

  async function handleSaveShare() {
    if (!authToken || !detail) {
      return;
    }

    setSaving(true);

    try {
      await saveShareToDrive(detail.shareCode, { parentId: null }, authToken, shareAccessToken);
      message.success('已保存到你的网盘根目录。');
      void navigate('/');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败。');
    } finally {
      setSaving(false);
    }
  }

  async function handleDownloadFile(item: StorageNode) {
    if (!authToken || !detail || item.type !== 'FILE') {
      return;
    }

    setDownloadingFileId(item.id);

    try {
      const access = await fetchShareFileAccessUrl(detail.shareCode, item.id, authToken, shareAccessToken, 'attachment');
      const anchor = document.createElement('a');
      anchor.href = access.url;
      anchor.target = '_blank';
      anchor.rel = 'noreferrer';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下载失败。');
    } finally {
      setDownloadingFileId(null);
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
              {item.type === 'FOLDER' ? <FolderOpenFilled /> : <FileOutlined />}
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
      width: 140,
      render: (_, item) =>
        item.type === 'FILE' && detail?.allowDownload ? (
          <Button
            type="link"
            icon={<CloudDownloadOutlined />}
            loading={downloadingFileId === item.id}
            onClick={() => void handleDownloadFile(item)}
          >
            下载
          </Button>
        ) : (
          <Typography.Text className="muted-text">-</Typography.Text>
        ),
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
        <Form form={passwordForm} layout="vertical" onFinish={(values) => void handlePasswordSubmit(values)}>
          <Form.Item name="password" label="提取码" rules={[{ required: true, message: '请输入提取码。' }]}>
            <Input.Password autoFocus placeholder="请输入提取码" />
          </Form.Item>
          <Button type="primary" htmlType="button" loading={passwordChecking} onClick={() => void passwordForm.submit()}>
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
        <Button type="primary" icon={<LoginOutlined />} onClick={goLogin}>
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
            {detail.allowSave ? (
              <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => void handleSaveShare()}>
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
          scroll={{ x: 900 }}
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
          <CloudServerOutlined className="brand-icon" />
          <div>
            <Typography.Title level={4}>Alicia 云盘</Typography.Title>
            <Typography.Text>文件分享</Typography.Text>
          </div>
        </button>
      </header>

      {showMobileOpenHint ? (
        <section className="share-mobile-open-banner" aria-label="移动端打开方式">
          <div className="share-mobile-open-copy">
            <AndroidOutlined />
            <div>
              <Typography.Text strong>Alicia 云盘 App</Typography.Text>
              <Typography.Text className="muted-text">已安装可直接打开；未安装可下载安装包。</Typography.Text>
            </div>
          </div>
          <Space className="share-mobile-open-actions" wrap>
            <Button type="primary" size="small" icon={<AndroidOutlined />} onClick={openInAndroidApp}>
              打开 App
            </Button>
            <Button size="small" onClick={openAppDownloadPage}>
              下载 App
            </Button>
            <Button type="text" size="small" onClick={() => setShowMobileOpenHint(false)}>
              继续网页查看
            </Button>
          </Space>
        </section>
      ) : null}

      <main className="share-page-main">{content}</main>

      <footer className="share-page-footer">
        <RegulatoryFooter />
      </footer>
    </div>
  );
}
