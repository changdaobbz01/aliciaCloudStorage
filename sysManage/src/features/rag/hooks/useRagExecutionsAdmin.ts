import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useRef, useState } from 'react';
import {
  fetchRagExecutionDetail,
  fetchRagExecutionOverview,
  fetchRagExecutions,
} from '../../../lib/api';
import type {
  RagExecutionDetail,
  RagExecutionOverview,
  RagExecutionPage,
  RagExecutionQuery,
} from '../../../types';

type UseRagExecutionsAdminOptions = {
  authToken: string | null;
  isRagAdmin: boolean;
  isRagExecutionsView: boolean;
  message: MessageInstance;
};

const initialQuery: RagExecutionQuery = {
  page: 1,
  size: 20,
};

export function useRagExecutionsAdmin({
  authToken,
  isRagAdmin,
  isRagExecutionsView,
  message,
}: UseRagExecutionsAdminOptions) {
  const [overview, setOverview] = useState<RagExecutionOverview | null>(null);
  const [executionsPage, setExecutionsPage] = useState<RagExecutionPage | null>(null);
  const [query, setQuery] = useState<RagExecutionQuery>(initialQuery);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<RagExecutionDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const authTokenRef = useRef(authToken);
  const isRagAdminRef = useRef(isRagAdmin);
  const loadingRef = useRef(false);
  const listRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);

  authTokenRef.current = authToken;
  isRagAdminRef.current = isRagAdmin;

  function isCurrentScope(token: string) {
    return authTokenRef.current === token && isRagAdminRef.current;
  }

  async function loadAll(options: { force?: boolean; nextQuery?: RagExecutionQuery } = {}) {
    const requestQuery = options.nextQuery ?? query;
    if (!authToken || !isRagAdmin) {
      listRequestIdRef.current += 1;
      loadingRef.current = false;
      setOverview(null);
      setExecutionsPage(null);
      setLoading(false);
      return;
    }
    if (loadingRef.current && !options.force) {
      return;
    }

    const token = authToken;
    const requestId = ++listRequestIdRef.current;
    loadingRef.current = true;
    setLoading(true);
    try {
      const [nextOverview, nextPage] = await Promise.all([
        fetchRagExecutionOverview(token),
        fetchRagExecutions(requestQuery, token),
      ]);
      if (listRequestIdRef.current !== requestId || !isCurrentScope(token)) {
        return;
      }
      setOverview(nextOverview);
      setExecutionsPage(nextPage);
    } catch (loadError) {
      if (listRequestIdRef.current === requestId && isCurrentScope(token)) {
        message.error(loadError instanceof Error ? loadError.message : '加载 RAG 执行任务失败。');
      }
    } finally {
      if (listRequestIdRef.current === requestId && isCurrentScope(token)) {
        loadingRef.current = false;
        setLoading(false);
      }
    }
  }

  function applyQuery(nextQuery: RagExecutionQuery) {
    const normalized = {
      ...nextQuery,
      page: nextQuery.page ?? 1,
      size: nextQuery.size ?? query.size ?? 20,
    };
    setQuery(normalized);
    void loadAll({ force: true, nextQuery: normalized });
  }

  function changePage(page: number, size: number) {
    applyQuery({ ...query, page, size });
  }

  async function openDetail(executionId: string) {
    if (!authToken || !isRagAdmin) {
      return;
    }
    const token = authToken;
    const requestId = ++detailRequestIdRef.current;
    setDetail(null);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const nextDetail = await fetchRagExecutionDetail(executionId, token);
      if (detailRequestIdRef.current !== requestId || !isCurrentScope(token)) {
        return;
      }
      setDetail(nextDetail);
    } catch (loadError) {
      if (detailRequestIdRef.current === requestId && isCurrentScope(token)) {
        message.error(loadError instanceof Error ? loadError.message : '加载任务详情失败。');
        setDetailOpen(false);
      }
    } finally {
      if (detailRequestIdRef.current === requestId && isCurrentScope(token)) {
        setDetailLoading(false);
      }
    }
  }

  function closeDetail() {
    detailRequestIdRef.current += 1;
    setDetailOpen(false);
    setDetailLoading(false);
    setDetail(null);
  }

  useEffect(() => {
    listRequestIdRef.current += 1;
    detailRequestIdRef.current += 1;
    loadingRef.current = false;
    setOverview(null);
    setExecutionsPage(null);
    setDetail(null);
    setDetailOpen(false);
    setLoading(false);
    setDetailLoading(false);
    setQuery(initialQuery);
  }, [authToken, isRagAdmin]);

  useEffect(() => {
    if (isRagExecutionsView) {
      void loadAll();
    }
  }, [authToken, isRagAdmin, isRagExecutionsView]);

  return {
    overview,
    executionsPage,
    query,
    loading,
    detail,
    detailOpen,
    detailLoading,
    loadAll,
    applyQuery,
    changePage,
    openDetail,
    closeDetail,
  };
}
