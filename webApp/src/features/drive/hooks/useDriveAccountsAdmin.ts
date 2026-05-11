import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useState } from 'react';
import { createUser, fetchUsers, resetUserPassword, updateUserStorageQuota } from '../../../lib/api';
import type { User } from '../../../types';
import { DEFAULT_NEW_USER_QUOTA_GB, bytesToGigabytes, gigabytesToBytes } from '../driveShared';
import type {
  CreateUserFormValues,
  ResetUserPasswordFormValues,
  UpdateUserQuotaFormValues,
} from '../types';

type UseDriveAccountsAdminOptions = {
  authToken: string | null;
  isAdmin: boolean;
  isAccountsView: boolean;
  currentUser: User | null;
  message: MessageInstance;
  updateCurrentUser: (user: User) => void;
};

export function useDriveAccountsAdmin({
  authToken,
  isAdmin,
  isAccountsView,
  currentUser,
  message,
  updateCurrentUser,
}: UseDriveAccountsAdminOptions) {
  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [createUserOpen, setCreateUserOpen] = useState(false);
  const [editQuotaTarget, setEditQuotaTarget] = useState<User | null>(null);
  const [resetPasswordTarget, setResetPasswordTarget] = useState<User | null>(null);

  const [createUserForm] = Form.useForm<CreateUserFormValues>();
  const [quotaForm] = Form.useForm<UpdateUserQuotaFormValues>();
  const [resetUserPasswordForm] = Form.useForm<ResetUserPasswordFormValues>();
  const createUserRole = Form.useWatch('role', createUserForm) ?? 'USER';

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

  function openCreateUserModal() {
    createUserForm.setFieldsValue({
      role: 'USER',
      avatarUrl: '',
      inheritAdminBackground: Boolean(currentUser?.homeBackgroundUrl),
      storageQuotaGb: DEFAULT_NEW_USER_QUOTA_GB,
    });
    setCreateUserOpen(true);
  }

  function closeCreateUserModal() {
    setCreateUserOpen(false);
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

  function closeEditUserQuotaModal() {
    setEditQuotaTarget(null);
  }

  function openResetUserPasswordModal(user: User) {
    if (user.id === currentUser?.id) {
      message.info('当前登录账号请使用右上角的修改密码。');
      return;
    }

    resetUserPasswordForm.resetFields();
    setResetPasswordTarget(user);
  }

  function closeResetUserPasswordModal() {
    setResetPasswordTarget(null);
  }

  async function submitCreateUser(values: CreateUserFormValues) {
    if (!authToken) {
      return false;
    }

    await createUser(
      {
        phoneNumber: values.phoneNumber,
        nickname: values.nickname,
        avatarUrl: values.avatarUrl?.trim() ? values.avatarUrl.trim() : null,
        inheritAdminBackground: values.inheritAdminBackground,
        password: values.password,
        role: values.role,
        storageQuotaBytes: values.role === 'ADMIN' ? null : gigabytesToBytes(values.storageQuotaGb),
      },
      authToken,
    );

    createUserForm.resetFields();
    setCreateUserOpen(false);
    await loadUsers();
    message.success('账号创建成功。');
    return true;
  }

  async function submitUserQuota(values: UpdateUserQuotaFormValues) {
    if (!authToken || !editQuotaTarget) {
      return false;
    }

    if (editQuotaTarget.role === 'ADMIN') {
      setEditQuotaTarget(null);
      message.info('管理员账号不限制存储额度。');
      return false;
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
    await loadUsers();
    message.success('用户最大额度已更新。');
    return true;
  }

  async function submitResetUserPassword(values: ResetUserPasswordFormValues) {
    if (!authToken || !resetPasswordTarget) {
      return false;
    }

    await resetUserPassword(
      resetPasswordTarget.id,
      {
        newPassword: values.newPassword,
      },
      authToken,
    );

    resetUserPasswordForm.resetFields();
    setResetPasswordTarget(null);
    message.success('用户密码已重置，旧登录状态已失效。');
    return true;
  }

  useEffect(() => {
    if (!isAccountsView) {
      return;
    }

    void loadUsers();
  }, [authToken, isAdmin, isAccountsView]);

  return {
    users,
    usersLoading,
    createUserOpen,
    editQuotaTarget,
    resetPasswordTarget,
    createUserForm,
    quotaForm,
    resetUserPasswordForm,
    createUserRole,
    loadUsers,
    openCreateUserModal,
    closeCreateUserModal,
    openEditUserQuotaModal,
    closeEditUserQuotaModal,
    openResetUserPasswordModal,
    closeResetUserPasswordModal,
    submitCreateUser,
    submitUserQuota,
    submitResetUserPassword,
  };
}
