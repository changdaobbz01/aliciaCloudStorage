import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useState } from 'react';
import {
  fetchAdminCloudOperationShares,
  fetchAdminCloudOperationTrash,
  fetchAdminCloudOperationsOverview,
  fetchAdminCloudStorageUsers,
} from '../../../lib/api';
import type {
  AdminCloudOperationsOverview,
  AdminCloudShareLinksPage,
  AdminCloudShareLinksQuery,
  AdminCloudStorageUsersPage,
  AdminCloudStorageUsersQuery,
  AdminCloudTrashNodesPage,
  AdminCloudTrashNodesQuery,
} from '../../../types';

type UseDriveOperationsAdminOptions = {
  authToken: string | null;
  isAdmin: boolean;
  isOperationsView: boolean;
  message: MessageInstance;
};

const DEFAULT_PAGE_SIZE = 10;
const initialStorageUsersQuery: AdminCloudStorageUsersQuery = {
  page: 1,
  size: DEFAULT_PAGE_SIZE,
  sortBy: 'usedBytes',
  sortDirection: 'desc',
};
const initialTrashNodesQuery: AdminCloudTrashNodesQuery = {
  page: 1,
  size: DEFAULT_PAGE_SIZE,
  sortBy: 'deletedAt',
  sortDirection: 'desc',
  rootOnly: true,
};
const initialShareLinksQuery: AdminCloudShareLinksQuery = {
  page: 1,
  size: DEFAULT_PAGE_SIZE,
  sortBy: 'createdAt',
  sortDirection: 'desc',
};

export function useDriveOperationsAdmin({
  authToken,
  isAdmin,
  isOperationsView,
  message,
}: UseDriveOperationsAdminOptions) {
  const [overview, setOverview] = useState<AdminCloudOperationsOverview | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [storageUsersPage, setStorageUsersPage] = useState<AdminCloudStorageUsersPage | null>(null);
  const [storageUsersQuery, setStorageUsersQuery] = useState<AdminCloudStorageUsersQuery>(initialStorageUsersQuery);
  const [storageUsersLoading, setStorageUsersLoading] = useState(false);
  const [trashNodesPage, setTrashNodesPage] = useState<AdminCloudTrashNodesPage | null>(null);
  const [trashNodesQuery, setTrashNodesQuery] = useState<AdminCloudTrashNodesQuery>(initialTrashNodesQuery);
  const [trashNodesLoading, setTrashNodesLoading] = useState(false);
  const [shareLinksPage, setShareLinksPage] = useState<AdminCloudShareLinksPage | null>(null);
  const [shareLinksQuery, setShareLinksQuery] = useState<AdminCloudShareLinksQuery>(initialShareLinksQuery);
  const [shareLinksLoading, setShareLinksLoading] = useState(false);

  async function loadOverview() {
    if (!authToken || !isAdmin) {
      setOverview(null);
      return;
    }

    setOverviewLoading(true);

    try {
      setOverview(await fetchAdminCloudOperationsOverview(authToken));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载运营概览失败。');
    } finally {
      setOverviewLoading(false);
    }
  }

  async function loadStorageUsers(query: AdminCloudStorageUsersQuery = storageUsersQuery) {
    if (!authToken || !isAdmin) {
      setStorageUsersPage(null);
      return;
    }

    setStorageUsersLoading(true);

    try {
      const page = await fetchAdminCloudStorageUsers(query, authToken);
      setStorageUsersPage(page);
      setStorageUsersQuery({
        ...query,
        page: page.page,
        size: page.size,
        sortBy: page.sortBy,
        sortDirection: page.sortDirection,
      });
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载容量用户明细失败。');
    } finally {
      setStorageUsersLoading(false);
    }
  }

  async function loadTrashNodes(query: AdminCloudTrashNodesQuery = trashNodesQuery) {
    if (!authToken || !isAdmin) {
      setTrashNodesPage(null);
      return;
    }

    setTrashNodesLoading(true);

    try {
      const page = await fetchAdminCloudOperationTrash(query, authToken);
      setTrashNodesPage(page);
      setTrashNodesQuery({
        ...query,
        page: page.page,
        size: page.size,
        sortBy: page.sortBy,
        sortDirection: page.sortDirection,
      });
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载回收站明细失败。');
    } finally {
      setTrashNodesLoading(false);
    }
  }

  async function loadShareLinks(query: AdminCloudShareLinksQuery = shareLinksQuery) {
    if (!authToken || !isAdmin) {
      setShareLinksPage(null);
      return;
    }

    setShareLinksLoading(true);

    try {
      const page = await fetchAdminCloudOperationShares(query, authToken);
      setShareLinksPage(page);
      setShareLinksQuery({
        ...query,
        page: page.page,
        size: page.size,
        sortBy: page.sortBy,
        sortDirection: page.sortDirection,
      });
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载分享链接明细失败。');
    } finally {
      setShareLinksLoading(false);
    }
  }

  async function loadAll() {
    await Promise.all([
      loadOverview(),
      loadStorageUsers(),
      loadTrashNodes(),
      loadShareLinks(),
    ]);
  }

  function applyStorageUsersQuery(query: AdminCloudStorageUsersQuery) {
    const nextQuery = {
      ...initialStorageUsersQuery,
      ...storageUsersQuery,
      ...query,
      page: query.page ?? 1,
      size: query.size ?? storageUsersQuery.size ?? DEFAULT_PAGE_SIZE,
    };
    setStorageUsersQuery(nextQuery);
    void loadStorageUsers(nextQuery);
  }

  function changeStorageUsersPage(page: number, size: number) {
    applyStorageUsersQuery({ ...storageUsersQuery, page, size });
  }

  function applyTrashNodesQuery(query: AdminCloudTrashNodesQuery) {
    const nextQuery = {
      ...initialTrashNodesQuery,
      ...trashNodesQuery,
      ...query,
      page: query.page ?? 1,
      size: query.size ?? trashNodesQuery.size ?? DEFAULT_PAGE_SIZE,
    };
    setTrashNodesQuery(nextQuery);
    void loadTrashNodes(nextQuery);
  }

  function changeTrashNodesPage(page: number, size: number) {
    applyTrashNodesQuery({ ...trashNodesQuery, page, size });
  }

  function applyShareLinksQuery(query: AdminCloudShareLinksQuery) {
    const nextQuery = {
      ...initialShareLinksQuery,
      ...shareLinksQuery,
      ...query,
      page: query.page ?? 1,
      size: query.size ?? shareLinksQuery.size ?? DEFAULT_PAGE_SIZE,
    };
    setShareLinksQuery(nextQuery);
    void loadShareLinks(nextQuery);
  }

  function changeShareLinksPage(page: number, size: number) {
    applyShareLinksQuery({ ...shareLinksQuery, page, size });
  }

  useEffect(() => {
    if (!isOperationsView) {
      return;
    }

    void loadAll();
  }, [authToken, isAdmin, isOperationsView]);

  return {
    overview,
    overviewLoading,
    storageUsersPage,
    storageUsersQuery,
    storageUsersLoading,
    trashNodesPage,
    trashNodesQuery,
    trashNodesLoading,
    shareLinksPage,
    shareLinksQuery,
    shareLinksLoading,
    loadAll,
    loadOverview,
    loadStorageUsers,
    loadTrashNodes,
    loadShareLinks,
    applyStorageUsersQuery,
    changeStorageUsersPage,
    applyTrashNodesQuery,
    changeTrashNodesPage,
    applyShareLinksQuery,
    changeShareLinksPage,
  };
}
