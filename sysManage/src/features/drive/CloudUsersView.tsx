import {
  Alert,
  Avatar,
  Button,
  Form,
  InputNumber,
  Modal,
  Progress,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { FormInstance } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Edit3, RefreshCw, UsersRound } from 'lucide-react';
import { Icon } from '../../components/Icon';
import type { User } from '../../types';
import { isCloudAdmin } from '../../types';
import {
  bytesToGigabytes,
  formatFileSize,
  formatNullableBytes,
  resolveAvatarSrc,
} from './driveShared';
import type { CloudQuotaFormValues } from './hooks/useCloudUsersAdmin';

const BYTES_PER_GIB = 1024 * 1024 * 1024;

type CloudUsersViewProps = {
  isAdmin: boolean;
  users: User[];
  loading: boolean;
  quotaForm: FormInstance<CloudQuotaFormValues>;
  quotaTarget: User | null;
  quotaModalOpen: boolean;
  quotaSaving: boolean;
  onRefresh: () => void;
  onOpenQuotaModal: (user: User) => void;
  onCloseQuotaModal: () => void;
  onSubmitQuotaUpdate: () => void;
};

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function usagePercent(user: User) {
  if (user.storageQuotaBytes === null || user.storageQuotaBytes <= 0) {
    return null;
  }

  return Number(Math.min(100, Math.max(0, (user.usedBytes / user.storageQuotaBytes) * 100)).toFixed(1));
}

function quotaInputMinimum(user: User | null) {
  if (!user) {
    return 0.01;
  }

  return Math.max(0.01, Math.ceil((user.usedBytes / BYTES_PER_GIB) * 100) / 100);
}

function summarizeUsers(users: User[]) {
  const usersWithQuota = users.filter((user) => user.storageQuotaBytes !== null);
  const totalUsedBytes = users.reduce((total, user) => total + user.usedBytes, 0);
  const assignedQuotaBytes = usersWithQuota.reduce((total, user) => total + (user.storageQuotaBytes ?? 0), 0);

  return {
    assignedQuotaBytes,
    usersWithQuota: usersWithQuota.length,
    totalUsedBytes,
  };
}

export default function CloudUsersView({
  isAdmin,
  users,
  loading,
  quotaForm,
  quotaTarget,
  quotaModalOpen,
  quotaSaving,
  onRefresh,
  onOpenQuotaModal,
  onCloseQuotaModal,
  onSubmitQuotaUpdate,
}: CloudUsersViewProps) {
  if (!isAdmin) {
    return (
      <section className="content-panel">
        <Alert
          type="warning"
          showIcon
          message="当前账号没有云盘用户管理权限"
          description="只有云盘管理员可以查看用户画像和调整存储额度。"
        />
      </section>
    );
  }

  const summary = summarizeUsers(users);
  const quotaMinimum = quotaInputMinimum(quotaTarget);
  const columns: ColumnsType<User> = [
    {
      title: '用户',
      key: 'user',
      fixed: 'left',
      width: 290,
      render: (_, user) => (
        <div className="user-cell">
          <Avatar size={40} src={resolveAvatarSrc(user)}>
            {user.nickname.slice(0, 1).toUpperCase()}
          </Avatar>
          <div className="user-cell-copy">
            <Typography.Text strong className="table-primary-text" ellipsis={{ tooltip: user.nickname }}>
              {user.nickname}
            </Typography.Text>
            <Typography.Text
              className="table-secondary-text"
              ellipsis={{ tooltip: user.email ?? user.phoneNumber }}
            >
              {user.email ?? user.phoneNumber}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '身份',
      key: 'role',
      width: 170,
      render: (_, user) => (
        <Space size={4} wrap>
          <Tag color={isCloudAdmin(user) ? 'gold' : 'blue'}>
            {isCloudAdmin(user) ? '云盘管理员' : '云盘用户'}
          </Tag>
          <Tag color={user.status === 'ACTIVE' ? 'green' : 'red'}>
            {user.status === 'ACTIVE' ? '启用' : '停用'}
          </Tag>
        </Space>
      ),
    },
    {
      title: '容量',
      key: 'quota',
      width: 300,
      render: (_, user) => {
        const percent = usagePercent(user);

        return (
          <div className="cloud-quota-cell">
            <div className="operations-progress-copy">
              <span>{formatFileSize(user.usedBytes)}</span>
              <span>{formatNullableBytes(user.storageQuotaBytes)}</span>
            </div>
            {percent === null ? (
              <Tag>未配置</Tag>
            ) : (
              <Progress
                percent={percent}
                size="small"
                strokeColor={percent >= 90 ? '#dc2626' : percent >= 75 ? '#f59e0b' : '#2563eb'}
                trailColor="#e5edff"
              />
            )}
            <Typography.Text className="table-secondary-text">
              剩余 {formatNullableBytes(user.remainingBytes)}
            </Typography.Text>
          </div>
        );
      },
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: '账号 ID',
      dataIndex: 'id',
      key: 'id',
      width: 110,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 140,
      render: (_, user) => (
        <Button
          type="link"
          icon={<Icon icon={Edit3} />}
          disabled={user.storageQuotaBytes === null}
          title={user.storageQuotaBytes === null ? '当前账号未初始化云盘额度' : undefined}
          onClick={() => onOpenQuotaModal(user)}
        >
          调整额度
        </Button>
      ),
    },
  ];

  return (
    <section className="content-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>用户额度与画像</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            维护云盘资料、容量配额和使用状态，身份账号与应用角色在身份后台处理。
          </Typography.Paragraph>
        </div>
        <div className="panel-actions">
          <Button icon={<Icon icon={RefreshCw} />} onClick={onRefresh} loading={loading}>
            刷新
          </Button>
        </div>
      </div>

      <div className="management-summary-grid cloud-users-summary-grid">
        <div className="management-summary-card">
          <div className="management-summary-label">用户总数</div>
          <div className="management-summary-value">{users.length}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">已配置额度</div>
          <div className="management-summary-value">{summary.usersWithQuota}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">已用空间</div>
          <div className="management-summary-value">{formatFileSize(summary.totalUsedBytes)}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">已分配额度</div>
          <div className="management-summary-value">{formatFileSize(summary.assignedQuotaBytes)}</div>
        </div>
      </div>

      <Table
        rowKey="id"
        className="management-table"
        loading={loading}
        columns={columns}
        dataSource={users}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        scroll={{ x: 1200 }}
        locale={{ emptyText: '暂无云盘用户。' }}
      />

      <Modal
        title={
          <span className="modal-title-with-icon">
            <Icon icon={UsersRound} />
            调整云盘额度
          </span>
        }
        open={quotaModalOpen}
        okText="保存额度"
        cancelText="取消"
        confirmLoading={quotaSaving}
        onCancel={onCloseQuotaModal}
        onOk={() => void onSubmitQuotaUpdate()}
        destroyOnHidden
      >
        {quotaTarget ? (
          <div className="quota-modal-user">
            <Avatar size={44} src={resolveAvatarSrc(quotaTarget)}>
              {quotaTarget.nickname.slice(0, 1).toUpperCase()}
            </Avatar>
            <div className="user-cell-copy">
              <Typography.Text strong>{quotaTarget.nickname}</Typography.Text>
              <Typography.Text className="table-secondary-text">
                已用 {formatFileSize(quotaTarget.usedBytes)}，当前额度 {formatNullableBytes(quotaTarget.storageQuotaBytes)}
              </Typography.Text>
            </div>
          </div>
        ) : null}

        <Form form={quotaForm} layout="vertical" onFinish={onSubmitQuotaUpdate}>
          <Form.Item
            name="storageQuotaGb"
            label="最大容量"
            rules={[
              { required: true, message: '请输入最大容量。' },
              { type: 'number', min: quotaMinimum, message: `不能低于当前已用空间 ${quotaMinimum} GB。` },
            ]}
          >
            <InputNumber min={quotaMinimum} precision={2} step={1} addonAfter="GB" className="quota-input" />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}
