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

type CloudUsersLoadOptions = {
  force?: boolean;
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
  const authTokenRef = useRef(authToken);
  const isAdminRef = useRef(isAdmin);
  const usersRequestIdRef = useRef(0);
  const usersLoadingKeyRef = useRef<string | null>(null);

  authTokenRef.current = authToken;
  isAdminRef.current = isAdmin;

  function createUsersRequestKey(token: string | null = authToken, admin = isAdmin) {
    return JSON.stringify([token, admin]);
  }

  function isCurrentUsersRequest(requestId: number, requestKey: string) {
    return (
      usersRequestIdRef.current === requestId
      && usersLoadingKeyRef.current === requestKey
      && createUsersRequestKey(authTokenRef.current, isAdminRef.current) === requestKey
    );
  }

  function createCloudUserMutationRequestKey(
    scope: string,
    token: string | null = authToken,
    admin = isAdmin,
    target: unknown = null,
  ) {
    return JSON.stringify([scope, token, admin, target]);
  }

  function isCurrentCloudUserMutationRequest(requestKey: string, scope: string, target: unknown = null) {
    return createCloudUserMutationRequestKey(scope, authTokenRef.current, isAdminRef.current, target) === requestKey;
  }

  async function loadUsers(options: CloudUsersLoadOptions = {}) {
    if (!authToken || !isAdmin) {
      usersRequestIdRef.current += 1;
      usersLoadingKeyRef.current = null;
      usersLoadingRef.current = false;
      setUsersLoading(false);
      setUsers([]);
      return;
    }

    const requestKey = createUsersRequestKey(authToken, isAdmin);
    if (!options.force && usersLoadingKeyRef.current === requestKey) {
      return;
    }

    usersRequestIdRef.current += 1;
    const requestId = usersRequestIdRef.current;
    usersLoadingKeyRef.current = requestKey;
    usersLoadingRef.current = true;
    setUsersLoading(true);

    try {
      const nextUsers = await fetchUsers(authToken);
      if (!isCurrentUsersRequest(requestId, requestKey)) {
        return;
      }

      setUsers(nextUsers);
    } catch (loadError) {
      if (isCurrentUsersRequest(requestId, requestKey)) {
        message.error(loadError instanceof Error ? loadError.message : '加载云盘用户失败。');
      }
    } finally {
      if (isCurrentUsersRequest(requestId, requestKey)) {
        usersLoadingKeyRef.current = null;
        usersLoadingRef.current = false;
        setUsersLoading(false);
      }
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
    const target = quotaTarget;

    if (!authToken || !target || !isAdmin || quotaSavingRef.current) {
      return;
    }

    quotaSavingRef.current = true;
    setQuotaSaving(true);
    const requestToken = authToken;
    const requestKey = createCloudUserMutationRequestKey('quota', requestToken, isAdmin, target.id);

    try {
      const values = await quotaForm.validateFields();
      const storageQuotaBytes = gigabytesToBytes(values.storageQuotaGb);

      if (storageQuotaBytes < target.usedBytes) {
        if (isCurrentCloudUserMutationRequest(requestKey, 'quota', target.id)) {
          message.error(`最大额度不能低于当前已用空间 ${formatFileSize(target.usedBytes)}。`);
        }
        return;
      }

      const updatedUser = await updateUserStorageQuota(target.id, { storageQuotaBytes }, requestToken);
      if (!isCurrentCloudUserMutationRequest(requestKey, 'quota', target.id)) {
        return;
      }

      setUsers((currentUsers) =>
        currentUsers.map((user) => (user.id === updatedUser.id ? updatedUser : user)),
      );

      if (currentUser?.id === updatedUser.id) {
        onCurrentUserUpdate(updatedUser);
      }

      message.success('已更新用户云盘额度。');
      resetQuotaModal();
      await loadUsers({ force: true });
    } catch (saveError) {
      if (typeof saveError === 'object' && saveError !== null && 'errorFields' in saveError) {
        return;
      }

      if (isCurrentCloudUserMutationRequest(requestKey, 'quota', target.id)) {
        message.error(saveError instanceof Error ? saveError.message : '更新用户云盘额度失败。');
      }
    } finally {
      quotaSavingRef.current = false;
      setQuotaSaving(false);
    }
  }

  useEffect(() => {
    usersRequestIdRef.current += 1;
    usersLoadingKeyRef.current = null;
    usersLoadingRef.current = false;
    setUsersLoading(false);
    setUsers([]);
  }, [authToken, isAdmin]);

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
