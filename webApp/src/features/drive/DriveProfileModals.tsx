import { LogOut, RefreshCw, Upload } from 'lucide-react';
import { Avatar, Button, Form, Input, Modal, Popconfirm, Space, Switch, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { MutableRefObject, ChangeEvent } from 'react';
import { AliciaModalTitle } from '../../components/AliciaModalTitle';
import type { ChangePasswordPayload, IdentitySession, UpdateProfilePayload, User } from '../../types';
import { Icon } from '../../components/Icon';

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
  profileSaving: boolean;
  avatarUploading: boolean;
  passwordSaving: boolean;
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
  profileSaving,
  avatarUploading,
  passwordSaving,
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
  const watchedAvatarUrl = Form.useWatch('avatarUrl', profileForm);
  const profileAvatarSrc =
    watchedAvatarUrl === undefined ? currentAvatarSrc : resolveProfileAvatarSrc(currentUser, watchedAvatarUrl);
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
        const revokingThisSession = identitySessionRevokingId === session.id;

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
            okButtonProps={{
              danger: true,
              loading: revokingThisSession,
              disabled: identitySessionRevokingId !== null && !revokingThisSession,
            }}
            cancelButtonProps={{ disabled: revokingThisSession }}
            onConfirm={() => void onRevokeSession(session.id)}
          >
            <Button
              danger
              size="small"
              icon={<Icon icon={LogOut} />}
              loading={revokingThisSession}
              disabled={identitySessionRevokingId !== null && !revokingThisSession}
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
        title={<AliciaModalTitle eyebrow="Account">个人资料</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-account-modal account-profile-modal"
        open={profileOpen}
        onCancel={onCloseProfile}
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
          onFinish={(values) => void onSubmitProfile(values)}
        >
          <div className="profile-avatar-row account-profile-hero">
            <Avatar size={64} src={profileAvatarSrc} className="account-profile-avatar-frame">
              {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'U'}
            </Avatar>
            <div className="account-profile-copy">
              <strong>用户图标</strong>
              <small>上传本地图片或填写图片地址后，会同步更新所有 Alicia 账号入口。</small>
              <div className="account-profile-actions">
                <Button
                  icon={<Icon icon={Upload} />}
                  loading={avatarUploading}
                  disabled={profileSaving}
                  onClick={onAvatarButtonClick}
                >
                  上传图片
                </Button>
              </div>
              <input
                ref={avatarInputRef}
                type="file"
                accept="image/png,image/jpeg,image/gif,image/webp"
                className="upload-input"
                onChange={(event) => void onAvatarFileChange(event)}
              />
            </div>
          </div>
          <div className="account-profile-fields">
            <Form.Item
              name="nickname"
              label="昵称"
              rules={[{ required: true, message: '请输入昵称。' }]}
            >
              <Input maxLength={100} placeholder="请输入昵称" />
            </Form.Item>
            <Form.Item
              name="phoneNumber"
              label="手机号"
              rules={[
                { pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' },
              ]}
            >
              <Input placeholder="可选，绑定后也可用于登录" />
            </Form.Item>
            <Form.Item name="avatarUrl" label="头像图标地址">
              <Input maxLength={500} placeholder="可选，使用图片地址或 cos: 头像地址" />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal
        title={<AliciaModalTitle eyebrow="Account">修改密码</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-account-modal"
        open={passwordOpen}
        onCancel={onClosePassword}
        onOk={() => void passwordForm.submit()}
        okText="确认修改"
        cancelText="取消"
        confirmLoading={passwordSaving}
        maskClosable={!passwordSaving}
        closable={!passwordSaving}
        cancelButtonProps={{ disabled: passwordSaving }}
        destroyOnHidden
      >
        <Form form={passwordForm} layout="vertical" disabled={passwordSaving} onFinish={(values) => void onSubmitPassword(values)}>
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
        title={<AliciaModalTitle eyebrow="Account">登录会话</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-account-modal alicia-session-modal"
        open={sessionsOpen}
        onCancel={onCloseSessions}
        footer={null}
        width={920}
        maskClosable={!identitySessionsLoading && identitySessionRevokingId === null}
        closable={!identitySessionsLoading && identitySessionRevokingId === null}
        destroyOnHidden
      >
        <div className="session-modal-toolbar">
          <Space size="middle" wrap>
            <Switch
              checked={includeRevokedSessions}
              onChange={onIncludeRevokedSessionsChange}
              disabled={identitySessionsLoading || identitySessionRevokingId !== null}
            />
            <Typography.Text>显示已撤销</Typography.Text>
          </Space>
          <Button
            icon={<Icon icon={RefreshCw} />}
            loading={identitySessionsLoading}
            disabled={identitySessionsLoading || identitySessionRevokingId !== null}
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
