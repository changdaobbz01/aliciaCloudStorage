import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useRef, useState } from 'react';
import { fetchUsers, updateUserStorageQuota } from '../../../lib/api';
import type { User } from '../../../types';
import { bytesToGigabytes, formatFileSize, gigabytesToBytes } from '../driveShared';

export type CloudQuotaFormValues = {
  storageQuotaGb: number;
};

type UseCloudUsersAdminOptions = {
  authToken: string | null;
  currentUser: User | null;
  isAdmin: boolean;
  isUsersView: boolean;
  message: MessageInstance;
  onCurrentUserUpdate: (user: User) => void;
};

export function useCloudUsersAdmin({
  authToken,
  currentUser,
  isAdmin,
  isUsersView,
  message,
  onCurrentUserUpdate,
}: UseCloudUsersAdminOptions) {
  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [quotaTarget, setQuotaTarget] = useState<User | null>(null);
  const [quotaModalOpen, setQuotaModalOpen] = useState(false);
  const [quotaSaving, setQuotaSaving] = useState(false);
  const [quotaForm] = Form.useForm<CloudQuotaFormValues>();
  const usersLoadingRef = useRef(false);
  const quotaSavingRef = useRef(false);

  async function loadUsers() {
    if (!authToken || !isAdmin) {
      setUsers([]);
      return;
    }

    if (usersLoadingRef.current) {
      return;
    }

    usersLoadingRef.current = true;
    setUsersLoading(true);

    try {
      setUsers(await fetchUsers(authToken));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载云盘用户失败。');
    } finally {
      usersLoadingRef.current = false;
      setUsersLoading(false);
    }
  }

  function openQuotaModal(user: User) {
    if (!authToken || !isAdmin) {
      return;
    }

    if (usersLoadingRef.current || quotaSavingRef.current) {
      return;
    }

    if (user.storageQuotaBytes === null) {
      message.info('当前账号缺少云盘额度配置，请刷新后再试。');
      return;
    }

    setQuotaTarget(user);
    quotaForm.setFieldsValue({
      storageQuotaGb: bytesToGigabytes(user.storageQuotaBytes),
    });
    setQuotaModalOpen(true);
  }

  function resetQuotaModal() {
    setQuotaModalOpen(false);
    setQuotaTarget(null);
    quotaForm.resetFields();
  }

  function closeQuotaModal() {
    if (quotaSavingRef.current) {
      return;
    }

    resetQuotaModal();
  }

  async function submitQuotaUpdate() {
    if (!authToken || !quotaTarget || !isAdmin || quotaSavingRef.current) {
      return;
    }

    quotaSavingRef.current = true;
    setQuotaSaving(true);

    try {
      const values = await quotaForm.validateFields();
      const storageQuotaBytes = gigabytesToBytes(values.storageQuotaGb);

      if (storageQuotaBytes < quotaTarget.usedBytes) {
        message.error(`最大额度不能低于当前已用空间 ${formatFileSize(quotaTarget.usedBytes)}。`);
        return;
      }

      const updatedUser = await updateUserStorageQuota(quotaTarget.id, { storageQuotaBytes }, authToken);

      setUsers((currentUsers) =>
        currentUsers.map((user) => (user.id === updatedUser.id ? updatedUser : user)),
      );

      if (currentUser?.id === updatedUser.id) {
        onCurrentUserUpdate(updatedUser);
      }

      message.success('已更新用户云盘额度。');
      resetQuotaModal();
      await loadUsers();
    } catch (saveError) {
      if (typeof saveError === 'object' && saveError !== null && 'errorFields' in saveError) {
        return;
      }

      message.error(saveError instanceof Error ? saveError.message : '更新用户云盘额度失败。');
    } finally {
      quotaSavingRef.current = false;
      setQuotaSaving(false);
    }
  }

  useEffect(() => {
    if (!isUsersView) {
      return;
    }

    void loadUsers();
  }, [authToken, isAdmin, isUsersView]);

  return {
    users,
    usersLoading,
    quotaForm,
    quotaTarget,
    quotaModalOpen,
    quotaSaving,
    loadUsers,
    openQuotaModal,
    closeQuotaModal,
    submitQuotaUpdate,
  };
}
