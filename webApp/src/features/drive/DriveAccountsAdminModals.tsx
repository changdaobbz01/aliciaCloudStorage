import { Checkbox, Form, Input, InputNumber, Modal, Select, Space, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { AliciaModalTitle } from '../../components/AliciaModalTitle';
import { formatFileSize, formatNullableBytes } from './driveShared';
import type {
  CreateUserFormValues,
  ResetUserPasswordFormValues,
  UpdateCloudAppRoleFormValues,
  UpdateUserQuotaFormValues,
} from './types';
import type { User } from '../../types';

type DriveAccountsAdminModalsProps = {
  currentUser: User | null;
  createUserOpen: boolean;
  createUserRole: User['role'];
  editQuotaTarget: User | null;
  editAppRoleTarget: User | null;
  resetPasswordTarget: User | null;
  createUserForm: FormInstance<CreateUserFormValues>;
  quotaForm: FormInstance<UpdateUserQuotaFormValues>;
  appRoleForm: FormInstance<UpdateCloudAppRoleFormValues>;
  resetUserPasswordForm: FormInstance<ResetUserPasswordFormValues>;
  onCloseCreateUser: () => void;
  onSubmitCreateUser: (values: CreateUserFormValues) => Promise<unknown> | void;
  onCloseResetPassword: () => void;
  onSubmitResetPassword: (values: ResetUserPasswordFormValues) => Promise<unknown> | void;
  onCloseEditQuota: () => void;
  onSubmitUserQuota: (values: UpdateUserQuotaFormValues) => Promise<unknown> | void;
  onCloseEditAppRole: () => void;
  onSubmitUserAppRole: (values: UpdateCloudAppRoleFormValues) => Promise<unknown> | void;
};

export function DriveAccountsAdminModals({
  currentUser,
  createUserOpen,
  createUserRole,
  editQuotaTarget,
  editAppRoleTarget,
  resetPasswordTarget,
  createUserForm,
  quotaForm,
  appRoleForm,
  resetUserPasswordForm,
  onCloseCreateUser,
  onSubmitCreateUser,
  onCloseResetPassword,
  onSubmitResetPassword,
  onCloseEditQuota,
  onSubmitUserQuota,
  onCloseEditAppRole,
  onSubmitUserAppRole,
}: DriveAccountsAdminModalsProps) {
  return (
    <>
      <Modal
        title={<AliciaModalTitle eyebrow="Admin">新增账号</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-admin-modal"
        open={createUserOpen}
        onCancel={onCloseCreateUser}
        onOk={() => void createUserForm.submit()}
        okText="创建账号"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={createUserForm} layout="vertical" onFinish={(values) => void onSubmitCreateUser(values)}>
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
            label="昵称"
            rules={[{ required: true, message: '请输入昵称。' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="avatarUrl" label="头像地址">
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item
            name="inheritAdminBackground"
            valuePropName="checked"
            extra={
              currentUser?.homeBackgroundUrl
                ? '为新账号复制当前管理员的主页背景图。'
                : '当前管理员尚未设置主页背景图。'
            }
          >
            <Checkbox disabled={!currentUser?.homeBackgroundUrl}>同步管理员当前背景图</Checkbox>
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
            <Typography.Text className="muted-text">身份管理员默认具备云盘管理员权限，不受个人配额限制。</Typography.Text>
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
        title={(
          <AliciaModalTitle eyebrow="Application">
            {editAppRoleTarget ? `应用权限：${editAppRoleTarget.nickname}` : '应用权限'}
          </AliciaModalTitle>
        )}
        rootClassName="alicia-modal alicia-admin-modal"
        open={editAppRoleTarget !== null}
        onCancel={onCloseEditAppRole}
        onOk={() => void appRoleForm.submit()}
        okText="保存权限"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={appRoleForm} layout="vertical" onFinish={(values) => void onSubmitUserAppRole(values)}>
          {editAppRoleTarget ? (
            <Space direction="vertical" size={4} style={{ display: 'flex', marginBottom: 16 }}>
              <Typography.Text className="muted-text">
                当前账号：{editAppRoleTarget.email ?? editAppRoleTarget.phoneNumber}
              </Typography.Text>
              <Typography.Text className="muted-text">
                应用范围：Alicia 云盘
              </Typography.Text>
            </Space>
          ) : null}
          <Form.Item
            name="roleCode"
            label="云盘应用角色"
            rules={[{ required: true, message: '请选择云盘应用角色。' }]}
            extra="云盘管理员可以进入账号管理、应用包管理并查看全局云盘统计；普通用户只管理自己的文件和分享。"
          >
            <Select
              options={[
                { value: 'CLOUD_USER', label: '普通云盘用户' },
                { value: 'CLOUD_ADMIN', label: '云盘管理员' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={(
          <AliciaModalTitle eyebrow="Admin">
            {resetPasswordTarget ? `重置密码：${resetPasswordTarget.nickname}` : '重置密码'}
          </AliciaModalTitle>
        )}
        rootClassName="alicia-modal alicia-admin-modal"
        open={resetPasswordTarget !== null}
        onCancel={onCloseResetPassword}
        onOk={() => void resetUserPasswordForm.submit()}
        okText="重置密码"
        cancelText="取消"
        destroyOnHidden
      >
        <Form
          form={resetUserPasswordForm}
          layout="vertical"
          onFinish={(values) => void onSubmitResetPassword(values)}
        >
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
        title={(
          <AliciaModalTitle eyebrow="Admin">
            {editQuotaTarget ? `修改额度：${editQuotaTarget.nickname}` : '修改额度'}
          </AliciaModalTitle>
        )}
        rootClassName="alicia-modal alicia-admin-modal"
        open={editQuotaTarget !== null}
        onCancel={onCloseEditQuota}
        onOk={() => void quotaForm.submit()}
        okText="保存额度"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={quotaForm} layout="vertical" onFinish={(values) => void onSubmitUserQuota(values)}>
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
    </>
  );
}
