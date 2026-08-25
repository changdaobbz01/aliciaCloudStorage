import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useState } from 'react';
import {
  createUser,
  fetchIdentityAuditLogs,
  fetchUsers,
  resetUserPassword,
  updateIdentityApplicationRole,
  updateUserStorageQuota,
} from '../../../lib/api';
import { isCloudAdmin, type IdentityAuditLogPage, type IdentityAuditLogQuery, type User } from '../../../types';
import { DEFAULT_NEW_USER_QUOTA_GB, bytesToGigabytes, gigabytesToBytes } from '../driveShared';
import type {
  CreateUserFormValues,
  ResetUserPasswordFormValues,
  UpdateCloudAppRoleFormValues,
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
  const [auditLogPage, setAuditLogPage] = useState<IdentityAuditLogPage | null>(null);
  const [auditLogQuery, setAuditLogQuery] = useState<IdentityAuditLogQuery>({ page: 1, size: 20 });
  const [auditLogsLoading, setAuditLogsLoading] = useState(false);
  const [createUserOpen, setCreateUserOpen] = useState(false);
  const [editQuotaTarget, setEditQuotaTarget] = useState<User | null>(null);
  const [editAppRoleTarget, setEditAppRoleTarget] = useState<User | null>(null);
  const [resetPasswordTarget, setResetPasswordTarget] = useState<User | null>(null);

  const [createUserForm] = Form.useForm<CreateUserFormValues>();
  const [quotaForm] = Form.useForm<UpdateUserQuotaFormValues>();
  const [appRoleForm] = Form.useForm<UpdateCloudAppRoleFormValues>();
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

  async function loadAuditLogs(query: IdentityAuditLogQuery = auditLogQuery) {
    if (!authToken || !isAdmin) {
      setAuditLogPage(null);
      return;
    }

    setAuditLogsLoading(true);

    try {
      const page = await fetchIdentityAuditLogs(query, authToken);
      setAuditLogPage(page);
      setAuditLogQuery({
        ...query,
        page: page.page,
        size: page.size,
      });
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载审计日志失败。');
    } finally {
      setAuditLogsLoading(false);
    }
  }

  function applyAuditLogQuery(query: IdentityAuditLogQuery) {
    const nextQuery = {
      ...query,
      page: 1,
      size: query.size ?? auditLogQuery.size ?? 20,
    };
    setAuditLogQuery(nextQuery);
    void loadAuditLogs(nextQuery);
  }

  function changeAuditLogPage(page: number, size: number) {
    const nextQuery = {
      ...auditLogQuery,
      page,
      size,
    };
    setAuditLogQuery(nextQuery);
    void loadAuditLogs(nextQuery);
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
    if (isCloudAdmin(user) || user.storageQuotaBytes === null) {
      message.info('云盘管理员账号不限制存储额度。');
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

  function openEditUserAppRoleModal(user: User) {
    if (user.id === currentUser?.id) {
      message.info('当前登录账号不能在这里调整自身应用权限。');
      return;
    }

    if (user.role === 'ADMIN') {
      message.info('身份管理员始终具备云盘管理员权限，无需单独调整。');
      return;
    }

    appRoleForm.setFieldsValue({
      roleCode: isCloudAdmin(user) ? 'CLOUD_ADMIN' : 'CLOUD_USER',
    });
    setEditAppRoleTarget(user);
  }

  function closeEditUserAppRoleModal() {
    setEditAppRoleTarget(null);
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

    if (isCloudAdmin(editQuotaTarget)) {
      setEditQuotaTarget(null);
      message.info('云盘管理员账号不限制存储额度。');
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

  async function submitUserAppRole(values: UpdateCloudAppRoleFormValues) {
    if (!authToken || !editAppRoleTarget) {
      return false;
    }

    if (editAppRoleTarget.role === 'ADMIN') {
      setEditAppRoleTarget(null);
      message.info('身份管理员始终具备云盘管理员权限。');
      return false;
    }

    await updateIdentityApplicationRole(
      editAppRoleTarget.id,
      'cloud',
      {
        roleCode: values.roleCode,
      },
      authToken,
    );

    setEditAppRoleTarget(null);
    await loadUsers();
    await loadAuditLogs();
    message.success(values.roleCode === 'CLOUD_ADMIN' ? '已授予云盘管理员权限。' : '已调整为普通云盘用户。');
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
    void loadAuditLogs();
  }, [authToken, isAdmin, isAccountsView]);

  return {
    users,
    usersLoading,
    auditLogPage,
    auditLogQuery,
    auditLogsLoading,
    createUserOpen,
    editQuotaTarget,
    editAppRoleTarget,
    resetPasswordTarget,
    createUserForm,
    quotaForm,
    appRoleForm,
    resetUserPasswordForm,
    createUserRole,
    loadUsers,
    loadAuditLogs,
    applyAuditLogQuery,
    changeAuditLogPage,
    openCreateUserModal,
    closeCreateUserModal,
    openEditUserQuotaModal,
    closeEditUserQuotaModal,
    openEditUserAppRoleModal,
    closeEditUserAppRoleModal,
    openResetUserPasswordModal,
    closeResetUserPasswordModal,
    submitCreateUser,
    submitUserQuota,
    submitUserAppRole,
    submitResetUserPassword,
  };
}
