import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useState } from 'react';
import { createShareLink, fetchMyShareLinks, revokeShareLink } from '../../../lib/api';
import type { ShareLinkSummary, StorageNode } from '../../../types';
import type { CreateShareFormValues } from '../types';

type UseDriveSharesOptions = {
  authToken: string | null;
  isSharesView: boolean;
  message: MessageInstance;
};

export function useDriveShares({ authToken, isSharesView, message }: UseDriveSharesOptions) {
  const [shareLinks, setShareLinks] = useState<ShareLinkSummary[]>([]);
  const [shareLinksLoading, setShareLinksLoading] = useState(false);
  const [shareCreating, setShareCreating] = useState(false);
  const [shareCreateTarget, setShareCreateTarget] = useState<StorageNode | null>(null);
  const [lastCreatedShare, setLastCreatedShare] = useState<ShareLinkSummary | null>(null);
  const [lastCreatedPassword, setLastCreatedPassword] = useState<string | null>(null);
  const [createShareForm] = Form.useForm<CreateShareFormValues>();

  async function loadShareLinks() {
    if (!authToken) {
      setShareLinks([]);
      return;
    }

    setShareLinksLoading(true);

    try {
      setShareLinks(await fetchMyShareLinks(authToken));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载分享列表失败。');
    } finally {
      setShareLinksLoading(false);
    }
  }

  function openCreateShareModal(target: StorageNode) {
    setLastCreatedShare(null);
    setLastCreatedPassword(null);
    createShareForm.setFieldsValue({
      title: target.name,
      passwordEnabled: false,
      password: '',
      expiresInDays: 7,
      allowDownload: true,
      allowSave: true,
    });
    setShareCreateTarget(target);
  }

  function closeCreateShareModal() {
    setShareCreateTarget(null);
    setLastCreatedShare(null);
    setLastCreatedPassword(null);
    createShareForm.resetFields();
  }

  async function submitCreateShare(values: CreateShareFormValues) {
    if (!authToken || !shareCreateTarget) {
      return false;
    }

    setShareCreating(true);

    try {
      const normalizedPassword = values.passwordEnabled ? values.password?.trim() ?? '' : '';
      const shareLink = await createShareLink(
        {
          nodeIds: [shareCreateTarget.id],
          title: values.title?.trim() || shareCreateTarget.name,
          password: values.passwordEnabled ? normalizedPassword : null,
          expiresInDays: values.expiresInDays,
          allowDownload: values.allowDownload,
          allowSave: values.allowSave,
        },
        authToken,
      );

      setLastCreatedShare(shareLink);
      setLastCreatedPassword(values.passwordEnabled ? normalizedPassword : null);
      await loadShareLinks();
      message.success('分享链接已创建。');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建分享失败。');
      return false;
    } finally {
      setShareCreating(false);
    }
  }

  async function revokeShare(shareId: number) {
    if (!authToken) {
      return;
    }

    try {
      await revokeShareLink(shareId, authToken);
      await loadShareLinks();
      message.success('分享已取消。');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消分享失败。');
    }
  }

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
    shareCreateTarget,
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
