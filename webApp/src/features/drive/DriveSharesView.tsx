import { Copy, Link, RefreshCw, Trash2 } from 'lucide-react';
import { App as AntApp, Button, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import type { ShareLinkSummary } from '../../types';
import { resolveShareUrl } from './driveShared';
import { Icon } from '../../components/Icon';

type DriveSharesViewProps = {
  shareLinks: ShareLinkSummary[];
  loading: boolean;
  onRefresh: () => void;
  onRevokeShare: (shareId: number) => void | Promise<void>;
};

async function copyText(value: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return;
  }

  const input = document.createElement('textarea');
  input.value = value;
  input.style.position = 'fixed';
  input.style.opacity = '0';
  document.body.appendChild(input);
  input.select();
  document.execCommand('copy');
  input.remove();
}

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '永久有效';
}

function renderStatus(share: ShareLinkSummary) {
  if (share.status === 'ACTIVE') {
    return <Tag color="green">生效中</Tag>;
  }

  if (share.status === 'EXPIRED') {
    return <Tag color="orange">已过期</Tag>;
  }

  return <Tag>已取消</Tag>;
}

export default function DriveSharesView({ shareLinks, loading, onRefresh, onRevokeShare }: DriveSharesViewProps) {
  const { message } = AntApp.useApp();

  async function handleCopy(value: string) {
    try {
      await copyText(value);
      message.success('分享链接已复制。');
    } catch {
      message.error('复制失败，请手动复制。');
    }
  }

  const columns: TableProps<ShareLinkSummary>['columns'] = [
    {
      title: '分享名称',
      dataIndex: 'title',
      key: 'title',
      width: 280,
      render: (_, share) => (
        <div className="share-title-cell">
          <span className="storage-icon-shell storage-folder-icon">
            <Icon icon={Link} />
          </span>
          <div className="storage-name-copy">
            <Typography.Text strong ellipsis={{ tooltip: share.title }} className="storage-name-title">
              {share.title}
            </Typography.Text>
            <Typography.Text className="storage-name-meta">
              {share.itemCount} 项内容 · {share.hasPassword ? '需要提取码' : '无提取码'}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '权限',
      key: 'permission',
      width: 180,
      render: (_, share) => (
        <Space size={6} wrap>
          <Tag color={share.allowDownload ? 'blue' : 'default'}>{share.allowDownload ? '可下载' : '禁下载'}</Tag>
          <Tag color={share.allowSave ? 'cyan' : 'default'}>{share.allowSave ? '可保存' : '禁保存'}</Tag>
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_, share) => renderStatus(share),
    },
    {
      title: '访问次数',
      dataIndex: 'viewCount',
      key: 'viewCount',
      width: 110,
    },
    {
      title: '有效期',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 180,
      render: (value: string | null) => formatTimestamp(value),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (value: string) => formatTimestamp(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_, share) => (
        <Space size="small" wrap>
          <Button type="link" icon={<Icon icon={Copy} />} onClick={() => void handleCopy(resolveShareUrl(share.shareCode))}>
            复制链接
          </Button>
          {share.status === 'ACTIVE' ? (
            <Popconfirm
              title="取消分享"
              description="取消后，原分享链接将无法继续访问。"
              okText="取消分享"
              cancelText="保留"
              okButtonProps={{ danger: true }}
              onConfirm={() => onRevokeShare(share.id)}
            >
              <Button type="link" danger icon={<Icon icon={Trash2} />}>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <section className="content-panel drive-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>我的分享</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            管理已经创建的分享链接，随时复制或取消访问。
          </Typography.Paragraph>
        </div>
        <div className="panel-actions">
          <Button icon={<Icon icon={RefreshCw} />} onClick={onRefresh}>
            刷新
          </Button>
        </div>
      </div>

      <Table
        rowKey="id"
        className="management-table"
        loading={loading}
        columns={columns}
        dataSource={shareLinks}
        pagination={{
          pageSize: 10,
          showSizeChanger: false,
          position: ['bottomRight'],
        }}
        scroll={{ x: 1260 }}
        locale={{ emptyText: '还没有创建分享链接。' }}
      />
    </section>
  );
}
