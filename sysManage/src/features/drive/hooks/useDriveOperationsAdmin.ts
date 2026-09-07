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

type DriveOperationsReadOptions = {
  force?: boolean;
};

type RefState<T> = {
  current: T;
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
  const authTokenRef = useRef(authToken);
  const isAdminRef = useRef(isAdmin);
  const overviewRequestIdRef = useRef(0);
  const overviewLoadingKeyRef = useRef<string | null>(null);
  const storageUsersRequestIdRef = useRef(0);
  const storageUsersLoadingKeyRef = useRef<string | null>(null);
  const trashNodesRequestIdRef = useRef(0);
  const trashNodesLoadingKeyRef = useRef<string | null>(null);
  const shareLinksRequestIdRef = useRef(0);
  const shareLinksLoadingKeyRef = useRef<string | null>(null);

  authTokenRef.current = authToken;
  isAdminRef.current = isAdmin;

  function createOperationsRequestKey(
    scope: string,
    token: string | null = authToken,
    admin = isAdmin,
    query: unknown = null,
  ) {
    return JSON.stringify([scope, token, admin, query]);
  }

  function isCurrentOperationsRequest(
    requestIdRef: RefState<number>,
    loadingKeyRef: RefState<string | null>,
    requestId: number,
    requestKey: string,
    scope: string,
    query: unknown = null,
  ) {
    return (
      requestIdRef.current === requestId
      && loadingKeyRef.current === requestKey
      && createOperationsRequestKey(scope, authTokenRef.current, isAdminRef.current, query) === requestKey
    );
  }

  async function loadOverview(options: DriveOperationsReadOptions = {}) {
    if (!authToken || !isAdmin) {
      overviewRequestIdRef.current += 1;
      overviewLoadingKeyRef.current = null;
      overviewLoadingRef.current = false;
      setOverviewLoading(false);
      setOverview(null);
      return;
    }

    const requestKey = createOperationsRequestKey('overview', authToken, isAdmin);
    if (!options.force && overviewLoadingKeyRef.current === requestKey) {
      return;
    }

    overviewRequestIdRef.current += 1;
    const requestId = overviewRequestIdRef.current;
    overviewLoadingKeyRef.current = requestKey;
    overviewLoadingRef.current = true;
    setOverviewLoading(true);

    try {
      const nextOverview = await fetchAdminCloudOperationsOverview(authToken);
      if (!isCurrentOperationsRequest(overviewRequestIdRef, overviewLoadingKeyRef, requestId, requestKey, 'overview')) {
        return;
      }

      setOverview(nextOverview);
    } catch (loadError) {
      if (isCurrentOperationsRequest(overviewRequestIdRef, overviewLoadingKeyRef, requestId, requestKey, 'overview')) {
        message.error(loadError instanceof Error ? loadError.message : '加载运营概览失败。');
      }
    } finally {
      if (isCurrentOperationsRequest(overviewRequestIdRef, overviewLoadingKeyRef, requestId, requestKey, 'overview')) {
        overviewLoadingKeyRef.current = null;
        overviewLoadingRef.current = false;
        setOverviewLoading(false);
      }
    }
  }

  async function loadStorageUsers(
    query: AdminCloudStorageUsersQuery = storageUsersQuery,
    options: DriveOperationsReadOptions = {},
  ) {
    if (!authToken || !isAdmin) {
      storageUsersRequestIdRef.current += 1;
      storageUsersLoadingKeyRef.current = null;
      storageUsersLoadingRef.current = false;
      setStorageUsersLoading(false);
      setStorageUsersPage(null);
      return;
    }

    const requestKey = createOperationsRequestKey('storage-users', authToken, isAdmin, query);
    if (!options.force && storageUsersLoadingKeyRef.current === requestKey) {
      return;
    }

    storageUsersRequestIdRef.current += 1;
    const requestId = storageUsersRequestIdRef.current;
    storageUsersLoadingKeyRef.current = requestKey;
    storageUsersLoadingRef.current = true;
    setStorageUsersLoading(true);

    try {
      const page = await fetchAdminCloudStorageUsers(query, authToken);
      if (
        !isCurrentOperationsRequest(
          storageUsersRequestIdRef,
          storageUsersLoadingKeyRef,
          requestId,
          requestKey,
          'storage-users',
          query,
        )
      ) {
        return;
      }

      setStorageUsersPage(page);
      setStorageUsersQuery({
        ...query,
        page: page.page,
        size: page.size,
        sortBy: page.sortBy,
        sortDirection: page.sortDirection,
      });
    } catch (loadError) {
      if (
        isCurrentOperationsRequest(
          storageUsersRequestIdRef,
          storageUsersLoadingKeyRef,
          requestId,
          requestKey,
          'storage-users',
          query,
        )
      ) {
        message.error(loadError instanceof Error ? loadError.message : '加载容量用户明细失败。');
      }
    } finally {
      if (
        isCurrentOperationsRequest(
          storageUsersRequestIdRef,
          storageUsersLoadingKeyRef,
          requestId,
          requestKey,
          'storage-users',
          query,
        )
      ) {
        storageUsersLoadingKeyRef.current = null;
        storageUsersLoadingRef.current = false;
        setStorageUsersLoading(false);
      }
    }
  }

  async function loadTrashNodes(
    query: AdminCloudTrashNodesQuery = trashNodesQuery,
    options: DriveOperationsReadOptions = {},
  ) {
    if (!authToken || !isAdmin) {
      trashNodesRequestIdRef.current += 1;
      trashNodesLoadingKeyRef.current = null;
      trashNodesLoadingRef.current = false;
      setTrashNodesLoading(false);
      setTrashNodesPage(null);
      return;
    }

    const requestKey = createOperationsRequestKey('trash-nodes', authToken, isAdmin, query);
    if (!options.force && trashNodesLoadingKeyRef.current === requestKey) {
      return;
    }

    trashNodesRequestIdRef.current += 1;
    const requestId = trashNodesRequestIdRef.current;
    trashNodesLoadingKeyRef.current = requestKey;
    trashNodesLoadingRef.current = true;
    setTrashNodesLoading(true);

    try {
      const page = await fetchAdminCloudOperationTrash(query, authToken);
      if (
        !isCurrentOperationsRequest(
          trashNodesRequestIdRef,
          trashNodesLoadingKeyRef,
          requestId,
          requestKey,
          'trash-nodes',
          query,
        )
      ) {
        return;
      }

      setTrashNodesPage(page);
      setTrashNodesQuery({
        ...query,
        page: page.page,
        size: page.size,
        sortBy: page.sortBy,
        sortDirection: page.sortDirection,
      });
    } catch (loadError) {
      if (
        isCurrentOperationsRequest(
          trashNodesRequestIdRef,
          trashNodesLoadingKeyRef,
          requestId,
          requestKey,
          'trash-nodes',
          query,
        )
      ) {
        message.error(loadError instanceof Error ? loadError.message : '加载回收站明细失败。');
      }
    } finally {
      if (
        isCurrentOperationsRequest(
          trashNodesRequestIdRef,
          trashNodesLoadingKeyRef,
          requestId,
          requestKey,
          'trash-nodes',
          query,
        )
      ) {
        trashNodesLoadingKeyRef.current = null;
        trashNodesLoadingRef.current = false;
        setTrashNodesLoading(false);
      }
    }
  }

  async function loadShareLinks(
    query: AdminCloudShareLinksQuery = shareLinksQuery,
    options: DriveOperationsReadOptions = {},
  ) {
    if (!authToken || !isAdmin) {
      shareLinksRequestIdRef.current += 1;
      shareLinksLoadingKeyRef.current = null;
      shareLinksLoadingRef.current = false;
      setShareLinksLoading(false);
      setShareLinksPage(null);
      return;
    }

    const requestKey = createOperationsRequestKey('share-links', authToken, isAdmin, query);
    if (!options.force && shareLinksLoadingKeyRef.current === requestKey) {
      return;
    }

    shareLinksRequestIdRef.current += 1;
    const requestId = shareLinksRequestIdRef.current;
    shareLinksLoadingKeyRef.current = requestKey;
    shareLinksLoadingRef.current = true;
    setShareLinksLoading(true);

    try {
      const page = await fetchAdminCloudOperationShares(query, authToken);
      if (
        !isCurrentOperationsRequest(
          shareLinksRequestIdRef,
          shareLinksLoadingKeyRef,
          requestId,
          requestKey,
          'share-links',
          query,
        )
      ) {
        return;
      }

      setShareLinksPage(page);
      setShareLinksQuery({
        ...query,
        page: page.page,
        size: page.size,
        sortBy: page.sortBy,
        sortDirection: page.sortDirection,
      });
    } catch (loadError) {
      if (
        isCurrentOperationsRequest(
          shareLinksRequestIdRef,
          shareLinksLoadingKeyRef,
          requestId,
          requestKey,
          'share-links',
          query,
        )
      ) {
        message.error(loadError instanceof Error ? loadError.message : '加载分享链接明细失败。');
      }
    } finally {
      if (
        isCurrentOperationsRequest(
          shareLinksRequestIdRef,
          shareLinksLoadingKeyRef,
          requestId,
          requestKey,
          'share-links',
          query,
        )
      ) {
        shareLinksLoadingKeyRef.current = null;
        shareLinksLoadingRef.current = false;
        setShareLinksLoading(false);
      }
    }
  }

  async function loadAll(options: DriveOperationsReadOptions = {}) {
    await Promise.all([
      loadOverview(options),
      loadStorageUsers(storageUsersQuery, options),
      loadTrashNodes(trashNodesQuery, options),
      loadShareLinks(shareLinksQuery, options),
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
    void loadStorageUsers(nextQuery, { force: true });
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
    void loadTrashNodes(nextQuery, { force: true });
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
    void loadShareLinks(nextQuery, { force: true });
  }

  function changeShareLinksPage(page: number, size: number) {
    applyShareLinksQuery({ ...shareLinksQuery, page, size });
  }

  useEffect(() => {
    overviewRequestIdRef.current += 1;
    overviewLoadingKeyRef.current = null;
    overviewLoadingRef.current = false;
    setOverviewLoading(false);
    setOverview(null);

    storageUsersRequestIdRef.current += 1;
    storageUsersLoadingKeyRef.current = null;
    storageUsersLoadingRef.current = false;
    setStorageUsersLoading(false);
    setStorageUsersPage(null);

    trashNodesRequestIdRef.current += 1;
    trashNodesLoadingKeyRef.current = null;
    trashNodesLoadingRef.current = false;
    setTrashNodesLoading(false);
    setTrashNodesPage(null);

    shareLinksRequestIdRef.current += 1;
    shareLinksLoadingKeyRef.current = null;
    shareLinksLoadingRef.current = false;
    setShareLinksLoading(false);
    setShareLinksPage(null);
  }, [authToken, isAdmin]);

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
