import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useRef, useState } from 'react';
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
  const overviewLoadingRef = useRef(false);
  const storageUsersLoadingRef = useRef(false);
  const trashNodesLoadingRef = useRef(false);
  const shareLinksLoadingRef = useRef(false);

  async function loadOverview() {
    if (!authToken || !isAdmin) {
      setOverview(null);
      return;
    }

    if (overviewLoadingRef.current) {
      return;
    }

    overviewLoadingRef.current = true;
    setOverviewLoading(true);

    try {
      setOverview(await fetchAdminCloudOperationsOverview(authToken));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载运营概览失败。');
    } finally {
      overviewLoadingRef.current = false;
      setOverviewLoading(false);
    }
  }

  async function loadStorageUsers(query: AdminCloudStorageUsersQuery = storageUsersQuery) {
    if (!authToken || !isAdmin) {
      setStorageUsersPage(null);
      return;
    }

    if (storageUsersLoadingRef.current) {
      return;
    }

    storageUsersLoadingRef.current = true;
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
      storageUsersLoadingRef.current = false;
      setStorageUsersLoading(false);
    }
  }

  async function loadTrashNodes(query: AdminCloudTrashNodesQuery = trashNodesQuery) {
    if (!authToken || !isAdmin) {
      setTrashNodesPage(null);
      return;
    }

    if (trashNodesLoadingRef.current) {
      return;
    }

    trashNodesLoadingRef.current = true;
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
      trashNodesLoadingRef.current = false;
      setTrashNodesLoading(false);
    }
  }

  async function loadShareLinks(query: AdminCloudShareLinksQuery = shareLinksQuery) {
    if (!authToken || !isAdmin) {
      setShareLinksPage(null);
      return;
    }

    if (shareLinksLoadingRef.current) {
      return;
    }

    shareLinksLoadingRef.current = true;
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
      shareLinksLoadingRef.current = false;
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
    if (storageUsersLoadingRef.current) {
      return;
    }

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
    if (trashNodesLoadingRef.current) {
      return;
    }

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
    if (shareLinksLoadingRef.current) {
      return;
    }

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
