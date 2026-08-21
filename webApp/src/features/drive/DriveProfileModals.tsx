import { LogoutOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { Avatar, Button, Form, Input, Modal, Popconfirm, Space, Switch, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { MutableRefObject, ChangeEvent } from 'react';
import type { ChangePasswordPayload, IdentitySession, UpdateProfilePayload, User } from '../../types';

type PasswordFormValues = ChangePasswordPayload & {
  confirmPassword: string;
};

type DriveProfileModalsProps = {
  currentUser: User | null;
  currentAvatarSrc: string | undefined;
  profileOpen: boolean;
  passwordOpen: boolean;
  sessionsOpen: boolean;
  identitySessions: IdentitySession[];
  identitySessionsLoading: boolean;
  identitySessionRevokingId: number | null;
  includeRevokedSessions: boolean;
  avatarUploading: boolean;
  profileForm: FormInstance<UpdateProfilePayload>;
  passwordForm: FormInstance<PasswordFormValues>;
  avatarInputRef: MutableRefObject<HTMLInputElement | null>;
  onCloseProfile: () => void;
  onSubmitProfile: (values: UpdateProfilePayload) => Promise<unknown> | void;
  onAvatarButtonClick: () => void;
  onAvatarFileChange: (event: ChangeEvent<HTMLInputElement>) => void | Promise<void>;
  onClosePassword: () => void;
  onSubmitPassword: (values: PasswordFormValues) => Promise<unknown> | void;
  onCloseSessions: () => void;
  onRefreshSessions: () => Promise<unknown> | void;
  onIncludeRevokedSessionsChange: (checked: boolean) => void;
  onRevokeSession: (sessionId: number) => Promise<unknown> | void;
};

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function isExpired(session: IdentitySession) {
  return new Date(session.expiresAt).getTime() <= Date.now();
}

function renderSessionStatus(session: IdentitySession) {
  if (session.current) {
    return <Tag color="blue">当前</Tag>;
  }

  if (session.revokedAt) {
    return <Tag>已撤销</Tag>;
  }

  if (isExpired(session)) {
    return <Tag color="orange">已过期</Tag>;
  }

  return <Tag color="green">有效</Tag>;
}

export function DriveProfileModals({
  currentUser,
  currentAvatarSrc,
  profileOpen,
  passwordOpen,
  sessionsOpen,
  identitySessions,
  identitySessionsLoading,
  identitySessionRevokingId,
  includeRevokedSessions,
  avatarUploading,
  profileForm,
  passwordForm,
  avatarInputRef,
  onCloseProfile,
  onSubmitProfile,
  onAvatarButtonClick,
  onAvatarFileChange,
  onClosePassword,
  onSubmitPassword,
  onCloseSessions,
  onRefreshSessions,
  onIncludeRevokedSessionsChange,
  onRevokeSession,
}: DriveProfileModalsProps) {
  const sessionColumns: TableProps<IdentitySession>['columns'] = [
    {
      title: '客户端',
      key: 'client',
      width: 300,
      render: (_, session) => (
        <div className="session-client-cell">
          <Typography.Text
            className="table-primary-text"
            ellipsis={{ tooltip: session.userAgent ?? '未知客户端' }}
          >
            {session.userAgent ?? '未知客户端'}
          </Typography.Text>
          <Typography.Text className="muted-text">{session.clientIp ?? '-'}</Typography.Text>
        </div>
      ),
    },
    {
      title: '最近使用',
      dataIndex: 'lastUsedAt',
      key: 'lastUsedAt',
      width: 180,
      render: (value: string | null, session) => formatTimestamp(value ?? session.issuedAt),
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 180,
      render: (value: string) => formatTimestamp(value),
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_, session) => renderSessionStatus(session),
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, session) => {
        const canRevoke = !session.current && !session.revokedAt && !isExpired(session);

        if (!canRevoke) {
          return (
            <Button size="small" disabled>
              {session.current ? '当前会话' : '不可撤销'}
            </Button>
          );
        }

        return (
          <Popconfirm
            title="撤销这个登录会话？"
            okText="撤销"
            cancelText="取消"
            onConfirm={() => void onRevokeSession(session.id)}
          >
            <Button
              danger
              size="small"
              icon={<LogoutOutlined />}
              loading={identitySessionRevokingId === session.id}
            >
              撤销
            </Button>
          </Popconfirm>
        );
      },
    },
  ];

  return (
    <>
      <Modal
        title="修改个人资料"
        open={profileOpen}
        onCancel={onCloseProfile}
        onOk={() => void profileForm.submit()}
        destroyOnHidden
      >
        <Form form={profileForm} layout="vertical" onFinish={(values) => void onSubmitProfile(values)}>
          <div className="profile-avatar-row">
            <Avatar size={64} src={currentAvatarSrc}>
              {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'U'}
            </Avatar>
            <Space wrap>
              <Button icon={<UploadOutlined />} loading={avatarUploading} onClick={onAvatarButtonClick}>
                上传本地头像
              </Button>
              <input
                ref={avatarInputRef}
                type="file"
                accept="image/png,image/jpeg,image/gif,image/webp"
                className="upload-input"
                onChange={(event) => void onAvatarFileChange(event)}
              />
            </Space>
          </div>
          <Form.Item
            name="phoneNumber"
            label="手机号"
            rules={[
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' },
            ]}
          >
            <Input placeholder="可选，绑定后也可用于登录" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称。' }]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="修改密码"
        open={passwordOpen}
        onCancel={onClosePassword}
        onOk={() => void passwordForm.submit()}
        destroyOnHidden
      >
        <Form form={passwordForm} layout="vertical" onFinish={(values) => void onSubmitPassword(values)}>
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
        title="登录会话"
        open={sessionsOpen}
        onCancel={onCloseSessions}
        footer={null}
        width={920}
        destroyOnHidden
      >
        <div className="session-modal-toolbar">
          <Space size="middle" wrap>
            <Switch
              checked={includeRevokedSessions}
              onChange={onIncludeRevokedSessionsChange}
            />
            <Typography.Text>显示已撤销</Typography.Text>
          </Space>
          <Button
            icon={<ReloadOutlined />}
            loading={identitySessionsLoading}
            onClick={() => void onRefreshSessions()}
          >
            刷新
          </Button>
        </div>

        <Table
          rowKey="id"
          className="management-table"
          loading={identitySessionsLoading}
          columns={sessionColumns}
          dataSource={identitySessions}
          pagination={{ pageSize: 8, showSizeChanger: false, position: ['bottomRight'] }}
          scroll={{ x: 820 }}
          locale={{ emptyText: '暂无登录会话。' }}
        />
      </Modal>
    </>
  );
}
