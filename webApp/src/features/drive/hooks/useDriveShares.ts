import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useRef, useState } from 'react';
import { createShareLink, fetchMyShareLinks, revokeShareLink } from '../../../lib/api';
import type { ShareLinkSummary, StorageNode } from '../../../types';
import { MAX_SHARE_TARGETS, uniqueStorageNodes } from '../cloudOperationPolicy';
import type { CreateShareFormValues } from '../types';

type UseDriveSharesOptions = {
  authToken: string | null;
  isSharesView: boolean;
  message: MessageInstance;
};

type ShareLinksLoadOptions = {
  force?: boolean;
};

export function useDriveShares({ authToken, isSharesView, message }: UseDriveSharesOptions) {
  const [shareLinks, setShareLinks] = useState<ShareLinkSummary[]>([]);
  const [shareLinksLoading, setShareLinksLoading] = useState(false);
  const [shareCreating, setShareCreating] = useState(false);
  const [shareCreateTargets, setShareCreateTargets] = useState<StorageNode[]>([]);
  const [lastCreatedShare, setLastCreatedShare] = useState<ShareLinkSummary | null>(null);
  const [lastCreatedPassword, setLastCreatedPassword] = useState<string | null>(null);
  const [shareRevokingId, setShareRevokingId] = useState<number | null>(null);
  const shareCreatingRef = useRef(false);
  const shareRevokingIdRef = useRef<number | null>(null);
  const authTokenRef = useRef(authToken);
  const shareLinksRequestIdRef = useRef(0);
  const shareLinksLoadingKeyRef = useRef<string | null>(null);
  const [createShareForm] = Form.useForm<CreateShareFormValues>();
  authTokenRef.current = authToken;

  function createShareLinksRequestKey(token: string | null = authToken) {
    return JSON.stringify([token]);
  }

  function isCurrentShareLinksRequest(requestId: number, requestKey: string) {
    return (
      shareLinksRequestIdRef.current === requestId
      && shareLinksLoadingKeyRef.current === requestKey
      && createShareLinksRequestKey(authTokenRef.current) === requestKey
    );
  }

  async function loadShareLinks(options: ShareLinksLoadOptions = {}) {
    if (!authToken) {
      shareLinksRequestIdRef.current += 1;
      shareLinksLoadingKeyRef.current = null;
      setShareLinksLoading(false);
      setShareLinks([]);
      return;
    }

    const requestKey = createShareLinksRequestKey();
    if (!options.force && shareLinksLoadingKeyRef.current === requestKey) {
      return;
    }

    shareLinksRequestIdRef.current += 1;
    const requestId = shareLinksRequestIdRef.current;
    shareLinksLoadingKeyRef.current = requestKey;
    setShareLinksLoading(true);

    try {
      const nextShareLinks = await fetchMyShareLinks(authToken);
      if (!isCurrentShareLinksRequest(requestId, requestKey)) {
        return;
      }

      setShareLinks(nextShareLinks);
    } catch (error) {
      if (isCurrentShareLinksRequest(requestId, requestKey)) {
        message.error(error instanceof Error ? error.message : '加载分享列表失败。');
      }
    } finally {
      if (isCurrentShareLinksRequest(requestId, requestKey)) {
        shareLinksLoadingKeyRef.current = null;
        setShareLinksLoading(false);
      }
    }
  }

  function openCreateShareModal(rawTargets: StorageNode | StorageNode[]) {
    const targets = Array.isArray(rawTargets) ? rawTargets : [rawTargets];
    const uniqueTargets = uniqueStorageNodes(targets).filter((target) => Number.isInteger(target.id) && target.id > 0);
    if (uniqueTargets.length === 0) {
      message.warning('请先选择要分享的文件或文件夹。');
      return false;
    }
    if (uniqueTargets.length > MAX_SHARE_TARGETS) {
      message.warning(`单个分享最多包含 ${MAX_SHARE_TARGETS} 个项目。`);
      return false;
    }

    const defaultTitle = uniqueTargets.length === 1
      ? uniqueTargets[0].name
      : '批量分享';
    setLastCreatedShare(null);
    setLastCreatedPassword(null);
    createShareForm.setFieldsValue({
      title: defaultTitle,
      passwordEnabled: false,
      password: '',
      expiresInDays: 7,
      allowDownload: true,
      allowSave: true,
    });
    setShareCreateTargets(uniqueTargets.map((target) => ({ ...target })));
    return true;
  }

  function closeCreateShareModal() {
    if (shareCreatingRef.current) {
      return;
    }
    setShareCreateTargets([]);
    setLastCreatedShare(null);
    setLastCreatedPassword(null);
    createShareForm.resetFields();
  }

  async function submitCreateShare(values: CreateShareFormValues) {
    if (!authToken || shareCreatingRef.current) {
      return false;
    }

    const uniqueTargets = uniqueStorageNodes(shareCreateTargets).filter((target) => Number.isInteger(target.id) && target.id > 0);
    if (uniqueTargets.length === 0) {
      message.warning('请先选择要分享的文件或文件夹。');
      return false;
    }
    if (uniqueTargets.length > MAX_SHARE_TARGETS) {
      message.warning(`单个分享最多包含 ${MAX_SHARE_TARGETS} 个项目。`);
      return false;
    }

    shareCreatingRef.current = true;
    setShareCreating(true);

    try {
      const normalizedPassword = values.passwordEnabled ? values.password?.trim() ?? '' : '';
      const shareLink = await createShareLink(
        {
          nodeIds: uniqueTargets.map((target) => target.id),
          title: values.title?.trim() || (uniqueTargets.length === 1
            ? uniqueTargets[0].name
            : '批量分享'),
          password: values.passwordEnabled ? normalizedPassword : null,
          expiresInDays: values.expiresInDays,
          allowDownload: values.allowDownload,
          allowSave: values.allowSave,
        },
        authToken,
      );

      setLastCreatedShare(shareLink);
      setLastCreatedPassword(values.passwordEnabled ? normalizedPassword : null);
      await loadShareLinks({ force: true });
      message.success('分享链接已创建。');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建分享失败。');
      return false;
    } finally {
      shareCreatingRef.current = false;
      setShareCreating(false);
    }
  }

  async function revokeShare(shareId: number) {
    if (!authToken) {
      return;
    }

    if (shareRevokingIdRef.current !== null) {
      return;
    }

    shareRevokingIdRef.current = shareId;
    setShareRevokingId(shareId);

    try {
      await revokeShareLink(shareId, authToken);
      await loadShareLinks({ force: true });
      message.success('分享已取消。');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消分享失败。');
    } finally {
      shareRevokingIdRef.current = null;
      setShareRevokingId(null);
    }
  }

  useEffect(() => {
    shareLinksRequestIdRef.current += 1;
    shareLinksLoadingKeyRef.current = null;
    setShareLinksLoading(false);

    if (!authToken) {
      setShareLinks([]);
    }
  }, [authToken]);

  useEffect(() => {
    if (!isSharesView) {
      return;
    }

    void loadShareLinks();
  }, [authToken, isSharesView]);

  return {
    shareLinks,
    shareLinksLoading,
    shareCreating,
    shareRevokingId,
    shareCreateTargets,
    createShareForm,
    lastCreatedShare,
    lastCreatedPassword,
    loadShareLinks,
    openCreateShareModal,
    closeCreateShareModal,
    submitCreateShare,
    revokeShare,
  };
}
