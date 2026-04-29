import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { Avatar, Button, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { User } from '../types';

function resolveAvatarSrc(user: User) {
  if (!user.avatarUrl) {
    return undefined;
  }

  if (user.avatarUrl.startsWith('cos:')) {
    return `/api/auth/avatar/${user.id}?v=${encodeURIComponent(user.avatarUrl)}`;
  }

  return user.avatarUrl;
}

function formatBytes(value: number) {
  if (value <= 0) {
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

function formatQuota(value: number | null) {
  return value === null ? '无限制' : formatBytes(value);
}

type UserManagementPanelProps = {
  users: User[];
  loading: boolean;
  onCreateUser: () => void;
  onEditUserQuota: (user: User) => void;
};

export function UserManagementPanel({
  users,
  loading,
  onCreateUser,
  onEditUserQuota,
}: UserManagementPanelProps) {
  const totalUsers = users.length;
  const activeUsers = users.filter((user) => user.status === 'ACTIVE').length;
  const adminUsers = users.filter((user) => user.role === 'ADMIN').length;
  const assignedQuotaBytes = users.reduce((sum, user) => sum + (user.storageQuotaBytes ?? 0), 0);

  const columns: ColumnsType<User> = [
    {
      title: '用户',
      key: 'user',
      fixed: 'left',
      width: 240,
      render: (_, user) => (
        <div className="user-cell">
          <Avatar size={40} src={resolveAvatarSrc(user)}>
            {user.nickname.slice(0, 1).toUpperCase()}
          </Avatar>
          <div className="user-cell-copy">
            <Typography.Text strong className="table-primary-text">
              {user.nickname}
            </Typography.Text>
            <Typography.Text className="table-secondary-text">{user.phoneNumber}</Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 120,
      render: (role: User['role']) => (
        <Tag color={role === 'ADMIN' ? 'gold' : 'blue'}>{role === 'ADMIN' ? '管理员' : '普通用户'}</Tag>
      ),
    },
    {
      title: '已用空间',
      dataIndex: 'usedBytes',
      key: 'usedBytes',
      width: 150,
      render: (value: number) => formatBytes(value),
    },
    {
      title: '剩余额度',
      dataIndex: 'remainingBytes',
      key: 'remainingBytes',
      width: 150,
      render: (value: number | null) => formatQuota(value),
    },
    {
      title: '最大额度',
      dataIndex: 'storageQuotaBytes',
      key: 'storageQuotaBytes',
      width: 150,
      render: (value: number | null) => formatQuota(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: User['status']) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>{status === 'ACTIVE' ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 200,
      render: (value: string) => new Date(value).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 140,
      render: (_, user) =>
        user.role === 'ADMIN' ? (
          <Typography.Text className="table-secondary-text">无限制</Typography.Text>
        ) : (
          <Button type="link" icon={<EditOutlined />} onClick={() => onEditUserQuota(user)}>
            修改额度
          </Button>
        ),
    },
  ];

  return (
    <section className="content-panel account-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>账号管理</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            在这里集中查看账号状态、已用空间、剩余额度和个人配额；管理员账号默认不受个人额度限制。
          </Typography.Paragraph>
        </div>

        <div className="panel-actions">
          <Button type="primary" icon={<PlusOutlined />} onClick={onCreateUser}>
            新增账号
          </Button>
        </div>
      </div>

      <div className="management-summary-grid">
        <div className="management-summary-card">
          <div className="management-summary-label">账号总数</div>
          <div className="management-summary-value">{totalUsers}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">启用账号</div>
          <div className="management-summary-value">{activeUsers}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">管理员</div>
          <div className="management-summary-value">{adminUsers}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">已分配额度</div>
          <div className="management-summary-value">{formatBytes(assignedQuotaBytes)}</div>
        </div>
      </div>

      <Table
        rowKey="id"
        className="management-table"
        loading={loading}
        columns={columns}
        dataSource={users}
        pagination={false}
        scroll={{ x: 1260 }}
        locale={{ emptyText: '暂无账号记录。' }}
      />
    </section>
  );
}
