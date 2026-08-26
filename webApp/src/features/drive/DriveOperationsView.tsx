import type { LucideIcon } from 'lucide-react';
import {
  Database,
  File,
  Folder,
  HardDrive,
  Link2,
  Lock,
  RefreshCw,
  RotateCcw,
  Search,
  Share2,
  Trash2,
  UploadCloud,
  UsersRound,
} from 'lucide-react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Progress,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { Icon } from '../../components/Icon';
import type {
  AdminCloudOperationsOverview,
  AdminCloudShareLink,
  AdminCloudShareLinksPage,
  AdminCloudShareLinksQuery,
  AdminCloudShareSortField,
  AdminCloudShareStatusFilter,
  AdminCloudStorageUserSortField,
  AdminCloudStorageUserUsage,
  AdminCloudStorageUsersPage,
  AdminCloudStorageUsersQuery,
  AdminCloudTrashNode,
  AdminCloudTrashNodesPage,
  AdminCloudTrashNodesQuery,
  AdminCloudTrashSortField,
  SortDirection,
  StorageNodeType,
} from '../../types';
import { formatFileSize, formatNullableBytes } from './driveShared';

type DriveOperationsViewProps = {
  isAdmin: boolean;
  overview: AdminCloudOperationsOverview | null;
  overviewLoading: boolean;
  storageUsersPage: AdminCloudStorageUsersPage | null;
  storageUsersQuery: AdminCloudStorageUsersQuery;
  storageUsersLoading: boolean;
  trashNodesPage: AdminCloudTrashNodesPage | null;
  trashNodesQuery: AdminCloudTrashNodesQuery;
  trashNodesLoading: boolean;
  shareLinksPage: AdminCloudShareLinksPage | null;
  shareLinksQuery: AdminCloudShareLinksQuery;
  shareLinksLoading: boolean;
  onRefresh: () => void;
  onApplyStorageUsersQuery: (query: AdminCloudStorageUsersQuery) => void;
  onStorageUsersPageChange: (page: number, size: number) => void;
  onApplyTrashNodesQuery: (query: AdminCloudTrashNodesQuery) => void;
  onTrashNodesPageChange: (page: number, size: number) => void;
  onApplyShareLinksQuery: (query: AdminCloudShareLinksQuery) => void;
  onShareLinksPageChange: (page: number, size: number) => void;
};

type MetricCard = {
  key: string;
  label: string;
  value: string;
  detail: string;
  icon: LucideIcon;
  tone?: 'default' | 'warning' | 'success';
};

const storageUserSortOptions: Array<{ value: AdminCloudStorageUserSortField; label: string }> = [
  { value: 'usedBytes', label: '已用空间' },
  { value: 'usageRatio', label: '使用率' },
  { value: 'storageQuotaBytes', label: '最大额度' },
  { value: 'remainingBytes', label: '剩余额度' },
  { value: 'activeItems', label: '活跃项目' },
  { value: 'trashItems', label: '回收站项目' },
  { value: 'shareLinks', label: '分享链接' },
  { value: 'createdAt', label: '创建时间' },
  { value: 'nickname', label: '昵称' },
  { value: 'id', label: '用户 ID' },
];
const trashSortOptions: Array<{ value: AdminCloudTrashSortField; label: string }> = [
  { value: 'deletedAt', label: '删除时间' },
  { value: 'updatedAt', label: '更新时间' },
  { value: 'size', label: '大小' },
  { value: 'name', label: '名称' },
  { value: 'ownerId', label: '用户 ID' },
];
const shareSortOptions: Array<{ value: AdminCloudShareSortField; label: string }> = [
  { value: 'createdAt', label: '创建时间' },
  { value: 'updatedAt', label: '更新时间' },
  { value: 'lastAccessedAt', label: '最近访问' },
  { value: 'expiresAt', label: '过期时间' },
  { value: 'viewCount', label: '访问次数' },
  { value: 'title', label: '标题' },
  { value: 'ownerId', label: '用户 ID' },
];
const sortDirectionOptions: Array<{ value: SortDirection; label: string }> = [
  { value: 'desc', label: '降序' },
  { value: 'asc', label: '升序' },
];
const nodeTypeOptions: Array<{ value: StorageNodeType; label: string }> = [
  { value: 'FOLDER', label: '文件夹' },
  { value: 'FILE', label: '文件' },
];
const shareStatusOptions: Array<{ value: AdminCloudShareStatusFilter; label: string }> = [
  { value: 'AVAILABLE', label: '可访问' },
  { value: 'ACTIVE', label: '未撤销' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'REVOKED', label: '已撤销' },
];
const passwordProtectedOptions: Array<{ value: string; label: string }> = [
  { value: 'true', label: '有密码' },
  { value: 'false', label: '无密码' },
];

function formatCount(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value);
}

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function formatNullableId(value: number | null | undefined) {
  return value === null || value === undefined ? '-' : String(value);
}

function formatRatio(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return '无限制';
  }

  return `${Math.round(value * 1000) / 10}%`;
}

function toProgressPercent(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return 0;
  }

  return Math.min(100, Math.max(0, Math.round(value * 100)));
}

function formatBoolean(value: boolean, trueLabel: string, falseLabel: string) {
  return value ? trueLabel : falseLabel;
}

function getShareStatusTag(record: AdminCloudShareLink) {
  if (record.effectiveStatus === 'AVAILABLE') {
    return <Tag color="green">可访问</Tag>;
  }

  if (record.effectiveStatus === 'EXPIRED') {
    return <Tag color="orange">已过期</Tag>;
  }

  return <Tag color="red">已撤销</Tag>;
}

function buildMetricCards(overview: AdminCloudOperationsOverview | null): MetricCard[] {
  const capacity = overview?.capacity;
  const activeNodes = overview?.activeNodes;
  const trash = overview?.trash;
  const shares = overview?.shares;
  const multipartUploads = overview?.multipartUploads;

  return [
    {
      key: 'actualUsed',
      label: '实际占用',
      value: formatFileSize(capacity?.actualUsedBytes ?? 0),
      detail: `已分配 ${formatFileSize(capacity?.allocatedQuotaBytes ?? 0)}`,
      icon: HardDrive,
    },
    {
      key: 'activeNodes',
      label: '活跃项目',
      value: formatCount(activeNodes?.totalItems ?? 0),
      detail: `${formatCount(activeNodes?.folderCount ?? 0)} 个文件夹 / ${formatCount(activeNodes?.fileCount ?? 0)} 个文件`,
      icon: Database,
    },
    {
      key: 'trash',
      label: '回收站',
      value: formatCount(trash?.totalItems ?? 0),
      detail: `${formatCount(trash?.rootItems ?? 0)} 个顶层项目，${formatFileSize(trash?.bytes ?? 0)}`,
      icon: Trash2,
      tone: trash?.totalItems ? 'warning' : 'default',
    },
    {
      key: 'shares',
      label: '分享链接',
      value: formatCount(shares?.totalLinks ?? 0),
      detail: `${formatCount(shares?.availableLinks ?? 0)} 个可访问，${formatCount(shares?.totalViews ?? 0)} 次访问`,
      icon: Share2,
      tone: shares?.availableLinks ? 'success' : 'default',
    },
    {
      key: 'multipart',
      label: '分片上传',
      value: formatCount(multipartUploads?.inProgressSessions ?? 0),
      detail: `${formatCount(multipartUploads?.staleInProgressSessions ?? 0)} 个超过 ${multipartUploads?.staleHours ?? 0} 小时`,
      icon: UploadCloud,
      tone: multipartUploads?.staleInProgressSessions ? 'warning' : 'default',
    },
  ];
}

function MetricSummary({
  overview,
  loading,
}: {
  overview: AdminCloudOperationsOverview | null;
  loading: boolean;
}) {
  return (
    <Spin spinning={loading}>
      <div className="operations-summary-grid">
        {buildMetricCards(overview).map((metric) => (
          <div key={metric.key} className={`management-summary-card operations-summary-card operations-summary-card-${metric.tone ?? 'default'}`}>
            <span className="operations-summary-icon">
              <Icon icon={metric.icon} size={20} />
            </span>
            <div className="operations-summary-copy">
              <div className="management-summary-label">{metric.label}</div>
              <div className="management-summary-value">{metric.value}</div>
              <Typography.Text className="table-secondary-text">{metric.detail}</Typography.Text>
            </div>
          </div>
        ))}
      </div>
      <div className="operations-overview-meta">
        <span>概览生成时间：{formatTimestamp(overview?.generatedAt)}</span>
        <span>最近删除：{formatTimestamp(overview?.trash.latestDeletedAt)}</span>
        <span>最近分享访问：{formatTimestamp(overview?.shares.latestAccessedAt)}</span>
      </div>
    </Spin>
  );
}

function renderPagination(
  page: { page: number; size: number; totalItems: number } | null,
  query: { page?: number; size?: number },
  onPageChange: (page: number, size: number) => void,
): TablePaginationConfig {
  return {
    current: page?.page ?? query.page ?? 1,
    pageSize: page?.size ?? query.size ?? 10,
    total: page?.totalItems ?? 0,
    showSizeChanger: true,
    pageSizeOptions: [10, 20, 50, 100],
    position: ['bottomRight'],
    onChange: onPageChange,
  };
}

export function DriveOperationsView({
  isAdmin,
  overview,
  overviewLoading,
  storageUsersPage,
  storageUsersQuery,
  storageUsersLoading,
  trashNodesPage,
  trashNodesQuery,
  trashNodesLoading,
  shareLinksPage,
  shareLinksQuery,
  shareLinksLoading,
  onRefresh,
  onApplyStorageUsersQuery,
  onStorageUsersPageChange,
  onApplyTrashNodesQuery,
  onTrashNodesPageChange,
  onApplyShareLinksQuery,
  onShareLinksPageChange,
}: DriveOperationsViewProps) {
  const [storageUsersDraft, setStorageUsersDraft] = useState<AdminCloudStorageUsersQuery>(storageUsersQuery);
  const [trashNodesDraft, setTrashNodesDraft] = useState<AdminCloudTrashNodesQuery>(trashNodesQuery);
  const [shareLinksDraft, setShareLinksDraft] = useState<AdminCloudShareLinksQuery>(shareLinksQuery);

  useEffect(() => {
    setStorageUsersDraft(storageUsersQuery);
  }, [storageUsersQuery]);

  useEffect(() => {
    setTrashNodesDraft(trashNodesQuery);
  }, [trashNodesQuery]);

  useEffect(() => {
    setShareLinksDraft(shareLinksQuery);
  }, [shareLinksQuery]);

  if (!isAdmin) {
    return (
      <section className="content-panel">
        <Alert type="warning" showIcon message="当前账号没有运营查看权限" description="只有云盘管理员可以查看全局文件运营明细。" />
      </section>
    );
  }

  const storageUserColumns: ColumnsType<AdminCloudStorageUserUsage> = [
    {
      title: '用户',
      key: 'user',
      fixed: 'left',
      width: 260,
      render: (_, user) => (
        <div className="user-cell">
          <span className="operations-user-avatar">{user.nickname.slice(0, 1).toUpperCase()}</span>
          <div className="user-cell-copy">
            <Typography.Text strong className="table-primary-text" ellipsis={{ tooltip: user.nickname }}>
              {user.nickname}
            </Typography.Text>
            <Typography.Text className="table-secondary-text" ellipsis={{ tooltip: user.email ?? user.phoneNumber ?? undefined }}>
              {user.email ?? user.phoneNumber ?? `用户 #${user.userId}`}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '角色',
      key: 'role',
      width: 150,
      render: (_, user) => (
        <Space size={4} wrap>
          <Tag color={user.role === 'ADMIN' ? 'gold' : 'blue'}>{user.role === 'ADMIN' ? '身份管理员' : '普通用户'}</Tag>
          <Tag color={user.status === 'ACTIVE' ? 'green' : 'red'}>{user.status === 'ACTIVE' ? '启用' : '停用'}</Tag>
        </Space>
      ),
    },
    {
      title: '容量使用',
      key: 'usage',
      width: 260,
      render: (_, user) => (
        <div className="operations-progress-cell">
          <div className="operations-progress-copy">
            <span>{formatFileSize(user.usedBytes)}</span>
            <span>{formatRatio(user.usageRatio)}</span>
          </div>
          {user.usageRatio === null ? (
            <Tag color="purple">无限制</Tag>
          ) : (
            <Progress percent={toProgressPercent(user.usageRatio)} size="small" showInfo={false} strokeColor="#2563eb" trailColor="#e5edff" />
          )}
          <Typography.Text className="table-secondary-text">
            配额 {formatNullableBytes(user.storageQuotaBytes)}，剩余 {formatNullableBytes(user.remainingBytes)}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '文件结构',
      key: 'nodes',
      width: 220,
      render: (_, user) => (
        <Space size={6} wrap>
          <Tag color="blue">{formatCount(user.activeItems)} 项</Tag>
          <Tag>{formatCount(user.activeFolders)} 文件夹</Tag>
          <Tag>{formatCount(user.activeFiles)} 文件</Tag>
        </Space>
      ),
    },
    {
      title: '回收站',
      dataIndex: 'trashItems',
      key: 'trashItems',
      width: 120,
      render: (value: number) => formatCount(value),
    },
    {
      title: '分享',
      dataIndex: 'shareLinks',
      key: 'shareLinks',
      width: 120,
      render: (value: number) => formatCount(value),
    },
    {
      title: '用户 ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 110,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value: string | null) => formatTimestamp(value),
    },
  ];
  const trashColumns: ColumnsType<AdminCloudTrashNode> = [
    {
      title: '项目',
      key: 'node',
      fixed: 'left',
      width: 300,
      render: (_, node) => (
        <div className="storage-name-cell">
          <span className={node.type === 'FOLDER' ? 'storage-icon-shell storage-folder-icon' : 'storage-icon-shell storage-file-icon'}>
            <Icon icon={node.type === 'FOLDER' ? Folder : File} />
          </span>
          <div className="storage-name-copy">
            <Typography.Text strong className="storage-name-title" ellipsis={{ tooltip: node.name }}>
              {node.name}
            </Typography.Text>
            <Typography.Text className="table-secondary-text">
              {node.type === 'FOLDER' ? '文件夹' : '文件'} · {formatFileSize(node.size)}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '范围',
      key: 'rootItem',
      width: 110,
      render: (_, node) => (node.rootItem ? <Tag color="blue">顶层</Tag> : <Tag>子项</Tag>),
    },
    {
      title: '用户 ID',
      dataIndex: 'ownerId',
      key: 'ownerId',
      width: 110,
    },
    {
      title: '父级',
      key: 'parent',
      width: 180,
      render: (_, node) => (
        <Typography.Text className="table-secondary-text">
          当前 {formatNullableId(node.parentId)} / 原始 {formatNullableId(node.originalParentId)}
        </Typography.Text>
      ),
    },
    {
      title: '删除人',
      dataIndex: 'deletedBy',
      key: 'deletedBy',
      width: 110,
      render: (value: number | null) => formatNullableId(value),
    },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      key: 'deletedAt',
      width: 190,
      render: (value: string | null) => formatTimestamp(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 190,
      render: (value: string) => formatTimestamp(value),
    },
  ];
  const shareColumns: ColumnsType<AdminCloudShareLink> = [
    {
      title: '分享',
      key: 'share',
      fixed: 'left',
      width: 280,
      render: (_, share) => (
        <div className="storage-name-cell">
          <span className="storage-icon-shell storage-folder-icon">
            <Icon icon={Link2} />
          </span>
          <div className="storage-name-copy">
            <Typography.Text strong className="storage-name-title" ellipsis={{ tooltip: share.title }}>
              {share.title}
            </Typography.Text>
            <Typography.Text className="table-secondary-text">用户 #{share.ownerId}</Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_, share) => getShareStatusTag(share),
    },
    {
      title: '权限',
      key: 'permissions',
      width: 210,
      render: (_, share) => (
        <Space size={4} wrap>
          <Tag color={share.allowDownload ? 'blue' : 'default'}>{formatBoolean(share.allowDownload, '可下载', '禁下载')}</Tag>
          <Tag color={share.allowSave ? 'blue' : 'default'}>{formatBoolean(share.allowSave, '可保存', '禁保存')}</Tag>
          {share.passwordProtected ? <Tag color="purple" icon={<Icon icon={Lock} />}>有密码</Tag> : <Tag>无密码</Tag>}
        </Space>
      ),
    },
    {
      title: '访问 / 项目',
      key: 'stats',
      width: 150,
      render: (_, share) => `${formatCount(share.viewCount)} 次 / ${formatCount(share.itemCount)} 项`,
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 190,
      render: (value: string | null) => formatTimestamp(value),
    },
    {
      title: '最近访问',
      dataIndex: 'lastAccessedAt',
      key: 'lastAccessedAt',
      width: 190,
      render: (value: string | null) => formatTimestamp(value),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value: string) => formatTimestamp(value),
    },
  ];

  function submitStorageUsersFilter() {
    onApplyStorageUsersQuery({ ...storageUsersDraft, page: 1 });
  }

  function resetStorageUsersFilter() {
    onApplyStorageUsersQuery({
      page: 1,
      size: storageUsersQuery.size,
      sortBy: 'usedBytes',
      sortDirection: 'desc',
    });
  }

  function submitTrashNodesFilter() {
    onApplyTrashNodesQuery({ ...trashNodesDraft, keyword: trashNodesDraft.keyword?.trim() || null, page: 1 });
  }

  function resetTrashNodesFilter() {
    onApplyTrashNodesQuery({
      page: 1,
      size: trashNodesQuery.size,
      sortBy: 'deletedAt',
      sortDirection: 'desc',
      rootOnly: true,
    });
  }

  function submitShareLinksFilter() {
    onApplyShareLinksQuery({ ...shareLinksDraft, page: 1 });
  }

  function resetShareLinksFilter() {
    onApplyShareLinksQuery({
      page: 1,
      size: shareLinksQuery.size,
      sortBy: 'createdAt',
      sortDirection: 'desc',
    });
  }

  return (
    <section className="content-panel operations-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>云盘运营明细</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            从容量、回收站和分享三个角度查看全站文件状态，辅助定位异常操作和资源占用。
          </Typography.Paragraph>
        </div>
        <div className="panel-actions">
          <Button icon={<Icon icon={RefreshCw} />} onClick={onRefresh} loading={overviewLoading || storageUsersLoading || trashNodesLoading || shareLinksLoading}>
            刷新
          </Button>
        </div>
      </div>

      <MetricSummary overview={overview} loading={overviewLoading} />

      <Tabs
        className="account-admin-tabs operations-admin-tabs"
        items={[
          {
            key: 'storageUsers',
            label: (
              <span className="operations-tab-label">
                <Icon icon={UsersRound} />
                容量用户
              </span>
            ),
            children: (
              <>
                <Form className="audit-filter-bar operations-filter-bar operations-filter-bar-compact" layout="vertical" onFinish={submitStorageUsersFilter}>
                  <Form.Item label="排序字段">
                    <Select<AdminCloudStorageUserSortField>
                      value={storageUsersDraft.sortBy ?? 'usedBytes'}
                      options={storageUserSortOptions}
                      onChange={(value) => setStorageUsersDraft((current) => ({ ...current, sortBy: value }))}
                    />
                  </Form.Item>
                  <Form.Item label="排序方向">
                    <Select<SortDirection>
                      value={storageUsersDraft.sortDirection ?? 'desc'}
                      options={sortDirectionOptions}
                      onChange={(value) => setStorageUsersDraft((current) => ({ ...current, sortDirection: value }))}
                    />
                  </Form.Item>
                  <Form.Item label=" ">
                    <Space size="small" wrap>
                      <Button type="primary" htmlType="submit" icon={<Icon icon={Search} />}>
                        查询
                      </Button>
                      <Button icon={<Icon icon={RotateCcw} />} onClick={resetStorageUsersFilter}>
                        重置
                      </Button>
                    </Space>
                  </Form.Item>
                </Form>
                <div className="audit-filter-summary">
                  <Typography.Text className="audit-result-count">
                    共 <strong>{storageUsersPage?.totalItems ?? 0}</strong> 个用户容量记录
                  </Typography.Text>
                </div>
                <Table
                  rowKey="userId"
                  className="management-table"
                  loading={storageUsersLoading}
                  columns={storageUserColumns}
                  dataSource={storageUsersPage?.items ?? []}
                  pagination={renderPagination(storageUsersPage, storageUsersQuery, onStorageUsersPageChange)}
                  scroll={{ x: 1540 }}
                  locale={{ emptyText: '暂无用户容量记录。' }}
                />
              </>
            ),
          },
          {
            key: 'trashNodes',
            label: (
              <span className="operations-tab-label">
                <Icon icon={Trash2} />
                回收站
              </span>
            ),
            children: (
              <>
                <Form className="audit-filter-bar operations-filter-bar" layout="vertical" onFinish={submitTrashNodesFilter}>
                  <Form.Item label="用户 ID">
                    <InputNumber
                      min={1}
                      precision={0}
                      value={trashNodesDraft.ownerId ?? null}
                      placeholder="不限"
                      onChange={(value) => setTrashNodesDraft((current) => ({ ...current, ownerId: value ?? null }))}
                    />
                  </Form.Item>
                  <Form.Item label="关键字">
                    <Input
                      allowClear
                      value={trashNodesDraft.keyword ?? ''}
                      placeholder="文件或文件夹名称"
                      onChange={(event) => setTrashNodesDraft((current) => ({ ...current, keyword: event.target.value }))}
                      onPressEnter={submitTrashNodesFilter}
                    />
                  </Form.Item>
                  <Form.Item label="类型">
                    <Select<StorageNodeType>
                      allowClear
                      value={trashNodesDraft.type ?? undefined}
                      options={nodeTypeOptions}
                      placeholder="全部类型"
                      onChange={(value) => setTrashNodesDraft((current) => ({ ...current, type: value ?? null }))}
                    />
                  </Form.Item>
                  <Form.Item label="展示范围">
                    <Select<'root' | 'all'>
                      value={trashNodesDraft.rootOnly === false ? 'all' : 'root'}
                      options={[
                        { value: 'root', label: '仅顶层' },
                        { value: 'all', label: '全部项目' },
                      ]}
                      onChange={(value) => setTrashNodesDraft((current) => ({ ...current, rootOnly: value === 'root' }))}
                    />
                  </Form.Item>
                  <Form.Item label="排序字段">
                    <Select<AdminCloudTrashSortField>
                      value={trashNodesDraft.sortBy ?? 'deletedAt'}
                      options={trashSortOptions}
                      onChange={(value) => setTrashNodesDraft((current) => ({ ...current, sortBy: value }))}
                    />
                  </Form.Item>
                  <Form.Item label="排序方向">
                    <Select<SortDirection>
                      value={trashNodesDraft.sortDirection ?? 'desc'}
                      options={sortDirectionOptions}
                      onChange={(value) => setTrashNodesDraft((current) => ({ ...current, sortDirection: value }))}
                    />
                  </Form.Item>
                  <Form.Item label=" ">
                    <Space size="small" wrap>
                      <Button type="primary" htmlType="submit" icon={<Icon icon={Search} />}>
                        查询
                      </Button>
                      <Button icon={<Icon icon={RotateCcw} />} onClick={resetTrashNodesFilter}>
                        重置
                      </Button>
                    </Space>
                  </Form.Item>
                </Form>
                <div className="audit-filter-summary">
                  <Typography.Text className="audit-result-count">
                    共 <strong>{trashNodesPage?.totalItems ?? 0}</strong> 个回收站项目
                  </Typography.Text>
                </div>
                <Table
                  rowKey="id"
                  className="management-table"
                  loading={trashNodesLoading}
                  columns={trashColumns}
                  dataSource={trashNodesPage?.items ?? []}
                  pagination={renderPagination(trashNodesPage, trashNodesQuery, onTrashNodesPageChange)}
                  scroll={{ x: 1360 }}
                  locale={{ emptyText: '暂无回收站项目。' }}
                />
              </>
            ),
          },
          {
            key: 'shareLinks',
            label: (
              <span className="operations-tab-label">
                <Icon icon={Share2} />
                分享链接
              </span>
            ),
            children: (
              <>
                <Form className="audit-filter-bar operations-filter-bar" layout="vertical" onFinish={submitShareLinksFilter}>
                  <Form.Item label="用户 ID">
                    <InputNumber
                      min={1}
                      precision={0}
                      value={shareLinksDraft.ownerId ?? null}
                      placeholder="不限"
                      onChange={(value) => setShareLinksDraft((current) => ({ ...current, ownerId: value ?? null }))}
                    />
                  </Form.Item>
                  <Form.Item label="状态">
                    <Select<AdminCloudShareStatusFilter>
                      allowClear
                      value={shareLinksDraft.status ?? undefined}
                      options={shareStatusOptions}
                      placeholder="全部状态"
                      onChange={(value) => setShareLinksDraft((current) => ({ ...current, status: value ?? null }))}
                    />
                  </Form.Item>
                  <Form.Item label="密码">
                    <Select<string>
                      allowClear
                      value={shareLinksDraft.passwordProtected === undefined || shareLinksDraft.passwordProtected === null ? undefined : String(shareLinksDraft.passwordProtected)}
                      options={passwordProtectedOptions}
                      placeholder="不限"
                      onChange={(value) =>
                        setShareLinksDraft((current) => ({
                          ...current,
                          passwordProtected: value === undefined ? null : value === 'true',
                        }))
                      }
                    />
                  </Form.Item>
                  <Form.Item label="排序字段">
                    <Select<AdminCloudShareSortField>
                      value={shareLinksDraft.sortBy ?? 'createdAt'}
                      options={shareSortOptions}
                      onChange={(value) => setShareLinksDraft((current) => ({ ...current, sortBy: value }))}
                    />
                  </Form.Item>
                  <Form.Item label="排序方向">
                    <Select<SortDirection>
                      value={shareLinksDraft.sortDirection ?? 'desc'}
                      options={sortDirectionOptions}
                      onChange={(value) => setShareLinksDraft((current) => ({ ...current, sortDirection: value }))}
                    />
                  </Form.Item>
                  <Form.Item label=" ">
                    <Space size="small" wrap>
                      <Button type="primary" htmlType="submit" icon={<Icon icon={Search} />}>
                        查询
                      </Button>
                      <Button icon={<Icon icon={RotateCcw} />} onClick={resetShareLinksFilter}>
                        重置
                      </Button>
                    </Space>
                  </Form.Item>
                </Form>
                <div className="audit-filter-summary">
                  <Typography.Text className="audit-result-count">
                    共 <strong>{shareLinksPage?.totalItems ?? 0}</strong> 条分享记录
                  </Typography.Text>
                </div>
                <Table
                  rowKey="id"
                  className="management-table"
                  loading={shareLinksLoading}
                  columns={shareColumns}
                  dataSource={shareLinksPage?.items ?? []}
                  pagination={renderPagination(shareLinksPage, shareLinksQuery, onShareLinksPageChange)}
                  scroll={{ x: 1420 }}
                  locale={{ emptyText: '暂无分享链接。' }}
                />
              </>
            ),
          },
        ]}
      />
    </section>
  );
}

export default DriveOperationsView;
