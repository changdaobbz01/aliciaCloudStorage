import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useRef, useState } from 'react';
import { createShareLink, fetchMyShareLinks, revokeShareLink } from '../../../lib/api';
import type { ShareLinkSummary, StorageNode } from '../../../types';
import type { CreateShareFormValues } from '../types';

const MAX_SHARE_TARGETS = 20;

type UseDriveSharesOptions = {
  authToken: string | null;
  isSharesView: boolean;
  message: MessageInstance;
};

export function useDriveShares({ authToken, isSharesView, message }: UseDriveSharesOptions) {
  const [shareLinks, setShareLinks] = useState<ShareLinkSummary[]>([]);
  const [shareLinksLoading, setShareLinksLoading] = useState(false);
  const [shareCreating, setShareCreating] = useState(false);
  const [shareCreateTargets, setShareCreateTargets] = useState<StorageNode[]>([]);
  const [lastCreatedShare, setLastCreatedShare] = useState<ShareLinkSummary | null>(null);
  const [lastCreatedPassword, setLastCreatedPassword] = useState<string | null>(null);
  const shareCreatingRef = useRef(false);
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

  function openCreateShareModal(rawTargets: StorageNode | StorageNode[]) {
    const targets = Array.isArray(rawTargets) ? rawTargets : [rawTargets];
    const uniqueTargets = [...new Map(targets.map((target) => [target.id, target])).values()];
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
    if (!authToken || shareCreateTargets.length === 0 || shareCreatingRef.current) {
      return false;
    }

    shareCreatingRef.current = true;
    setShareCreating(true);

    try {
      const normalizedPassword = values.passwordEnabled ? values.password?.trim() ?? '' : '';
      const shareLink = await createShareLink(
        {
          nodeIds: shareCreateTargets.map((target) => target.id),
          title: values.title?.trim() || (shareCreateTargets.length === 1
            ? shareCreateTargets[0].name
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
      await loadShareLinks();
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
