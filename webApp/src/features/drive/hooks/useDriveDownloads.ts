import { useEffect, useMemo, useRef, useState } from 'react';
import type { MessageInstance } from 'antd/es/message/interface';
import { downloadStorageArchive, downloadStorageFile } from '../../../lib/api';
import type { StorageNode } from '../../../types';
import { uniqueStorageNodes, validateArchiveNodeIds } from '../cloudOperationPolicy';
import { formatFileSize } from '../driveShared';
import type {
  DriveDownloadButtonState,
  DriveDownloadSourceType,
  DriveDownloadTask,
  DriveDownloadTaskStatus,
} from '../types';

type UseDriveDownloadsOptions = {
  authToken: string | null;
  message: MessageInstance;
};

const ACTIVE_DOWNLOAD_STATUSES = new Set<DriveDownloadTaskStatus>([
  'queued',
  'preparing',
  'downloading',
  'saving',
]);

function createTaskId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function saveBlobToLocalFile(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

function stripZipSuffix(fileName: string) {
  return fileName.toLowerCase().endsWith('.zip') ? fileName.slice(0, -4) : fileName;
}

function createArchiveFallbackFileName(nodes: StorageNode[]) {
  if (nodes.length === 1) {
    return `${stripZipSuffix(nodes[0].name)}.zip`;
  }

  return `AliciaCloud-${new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')}.zip`;
}

function resolveDownloadSourceType(nodes: StorageNode[]): DriveDownloadSourceType {
  return nodes.length === 1 && nodes[0].type === 'FILE' ? 'file' : 'archive';
}

function createDownloadTask(nodes: StorageNode[]): DriveDownloadTask {
  const sourceType = resolveDownloadSourceType(nodes);
  const displayName = nodes.length === 1 ? nodes[0].name : `${nodes.length} 个项目`;

  return {
    id: createTaskId(),
    sourceType,
    nodeIds: nodes.map((node) => node.id),
    displayName,
    fileName: sourceType === 'file' ? nodes[0].name : createArchiveFallbackFileName(nodes),
    version: sourceType === 'file' ? nodes[0].updatedAt : null,
    status: 'queued',
    loadedBytes: 0,
    totalBytes: sourceType === 'file' ? nodes[0].size : null,
    percent: null,
    createdAt: Date.now(),
    finishedAt: null,
    error: null,
  };
}

function sameNodeIds(left: number[], right: number[]) {
  if (left.length !== right.length) {
    return false;
  }

  const normalizedLeft = [...left].sort((a, b) => a - b);
  const normalizedRight = [...right].sort((a, b) => a - b);
  return normalizedLeft.every((value, index) => value === normalizedRight[index]);
}

function isActiveDownloadStatus(status: DriveDownloadTaskStatus) {
  return ACTIVE_DOWNLOAD_STATUSES.has(status);
}

function isCancelableDownloadStatus(status: DriveDownloadTaskStatus) {
  return status === 'queued' || status === 'preparing' || status === 'downloading';
}

function isAbortError(error: unknown) {
  return error instanceof Error && error.name === 'AbortError';
}

function createAbortError() {
  return new DOMException('下载已取消。', 'AbortError');
}

function formatDownloadButtonLabel(task: DriveDownloadTask | null, idleLabel: string) {
  if (!task) {
    return idleLabel;
  }

  if (task.status === 'error') {
    return '重新下载';
  }

  if (task.status === 'queued') {
    return '排队中';
  }

  if (task.status === 'preparing') {
    return '准备中↓';
  }

  if (task.status === 'saving') {
    return task.percent === 100 ? '100%↓' : '保存中';
  }

  if (task.status === 'downloading') {
    if (task.percent !== null) {
      return `${task.percent}%↓`;
    }

    return task.loadedBytes > 0 ? `${formatFileSize(task.loadedBytes)}↓` : '下载中↓';
  }

  return idleLabel;
}

export function useDriveDownloads({ authToken, message }: UseDriveDownloadsOptions) {
  const [downloadTasks, setDownloadTasksState] = useState<DriveDownloadTask[]>([]);
  const downloadTasksRef = useRef<DriveDownloadTask[]>([]);
  const authTokenRef = useRef(authToken);
  const controllersRef = useRef<Map<string, AbortController>>(new Map());
  const downloadChainRef = useRef<Promise<void>>(Promise.resolve());

  function commitDownloadTasks(updater: (tasks: DriveDownloadTask[]) => DriveDownloadTask[]) {
    const nextTasks = updater(downloadTasksRef.current);
    downloadTasksRef.current = nextTasks;
    setDownloadTasksState(nextTasks);
  }

  function updateDownloadTask(taskId: string, updater: (task: DriveDownloadTask) => DriveDownloadTask) {
    commitDownloadTasks((current) => current.map((task) => (task.id === taskId ? updater(task) : task)));
  }

  function findTask(predicate: (task: DriveDownloadTask) => boolean) {
    return [...downloadTasksRef.current].reverse().find(predicate) ?? null;
  }

  function findMatchingTask(
    sourceType: DriveDownloadSourceType,
    nodeIds: number[],
    predicate: (task: DriveDownloadTask) => boolean,
  ) {
    return findTask((task) => task.sourceType === sourceType && sameNodeIds(task.nodeIds, nodeIds) && predicate(task));
  }

  async function runDownloadTask(taskId: string) {
    const task = downloadTasksRef.current.find((candidate) => candidate.id === taskId);
    const token = authTokenRef.current;

    if (!task || task.status !== 'queued') {
      return;
    }

    if (!token) {
      updateDownloadTask(taskId, (current) => ({
        ...current,
        status: 'error',
        error: '登录状态不可用，请重新登录后再下载。',
        finishedAt: Date.now(),
      }));
      return;
    }

    const controller = new AbortController();
    controllersRef.current.set(taskId, controller);
    updateDownloadTask(taskId, (current) => ({
      ...current,
      status: 'preparing',
      loadedBytes: 0,
      percent: null,
      error: null,
      finishedAt: null,
    }));

    try {
      const downloadResult =
        task.sourceType === 'file'
          ? await downloadStorageFile(task.nodeIds[0], token, task.version ?? undefined, {
              signal: controller.signal,
              onProgress: ({ loaded, total, percent }) => {
                if (controller.signal.aborted) {
                  return;
                }

                updateDownloadTask(taskId, (current) => ({
                  ...current,
                  status: 'downloading',
                  loadedBytes: loaded,
                  totalBytes: total ?? current.totalBytes,
                  percent,
                  error: null,
                }));
              },
            })
          : await downloadStorageArchive(
              { nodeIds: task.nodeIds },
              token,
              {
                signal: controller.signal,
                onProgress: ({ loaded, total, percent }) => {
                  if (controller.signal.aborted) {
                    return;
                  }

                  updateDownloadTask(taskId, (current) => ({
                    ...current,
                    status: 'downloading',
                    loadedBytes: loaded,
                    totalBytes: total,
                    percent,
                    error: null,
                  }));
                },
              },
            );

      if (controller.signal.aborted) {
        throw createAbortError();
      }

      updateDownloadTask(taskId, (current) => ({
        ...current,
        status: 'saving',
        fileName: downloadResult.fileName ?? current.fileName,
        loadedBytes: downloadResult.blob.size || current.loadedBytes,
        totalBytes: current.totalBytes ?? (downloadResult.blob.size || null),
        percent: current.totalBytes ? 100 : current.percent,
      }));

      const fileName = downloadResult.fileName ?? task.fileName ?? 'AliciaCloud-download';
      saveBlobToLocalFile(downloadResult.blob, fileName);

      updateDownloadTask(taskId, (current) => ({
        ...current,
        status: 'success',
        fileName,
        loadedBytes: downloadResult.blob.size || current.loadedBytes,
        totalBytes: current.totalBytes ?? (downloadResult.blob.size || null),
        percent: 100,
        finishedAt: Date.now(),
        error: null,
      }));
      message.success(`已下载「${task.displayName}」。`);
    } catch (downloadError) {
      if (isAbortError(downloadError) || controller.signal.aborted) {
        updateDownloadTask(taskId, (current) => ({
          ...current,
          status: 'canceled',
          error: '下载已取消。',
          finishedAt: Date.now(),
        }));
        return;
      }

      updateDownloadTask(taskId, (current) => ({
        ...current,
        status: 'error',
        error: downloadError instanceof Error ? downloadError.message : '下载失败，请稍后重试。',
        finishedAt: Date.now(),
      }));
      message.error(downloadError instanceof Error ? downloadError.message : '下载失败，请稍后重试。');
    } finally {
      controllersRef.current.delete(taskId);
    }
  }

  function queueTaskRun(taskId: string) {
    downloadChainRef.current = downloadChainRef.current
      .catch(() => undefined)
      .then(() => runDownloadTask(taskId));
  }

  function enqueueDownloadTask(task: DriveDownloadTask) {
    commitDownloadTasks((current) => [...current, task]);
    queueTaskRun(task.id);
    message.success('已加入下载队列，可在下载管理中查看进度。');
  }

  function retryDownloadTask(taskId: string) {
    const task = downloadTasksRef.current.find((candidate) => candidate.id === taskId);

    if (!task || task.status !== 'error') {
      return;
    }

    updateDownloadTask(taskId, (current) => ({
      ...current,
      status: 'queued',
      loadedBytes: 0,
      percent: null,
      error: null,
      finishedAt: null,
    }));
    queueTaskRun(taskId);
  }

  function downloadNodes(nodes: StorageNode[]) {
    const uniqueNodes = uniqueStorageNodes(nodes).filter((node) => Number.isInteger(node.id) && node.id > 0);

    if (uniqueNodes.length === 0) {
      message.warning('请先选择要下载的项目。');
      return;
    }

    if (!authTokenRef.current) {
      message.error('登录状态不可用，请重新登录后再下载。');
      return;
    }

    const sourceType = resolveDownloadSourceType(uniqueNodes);
    const nodeIds = uniqueNodes.map((node) => node.id);

    if (sourceType === 'archive') {
      const archiveSelection = validateArchiveNodeIds(nodeIds, '请先选择要下载的项目。');
      if (!archiveSelection.valid) {
        message.warning(archiveSelection.message);
        return;
      }
    }

    const activeTask = findMatchingTask(sourceType, nodeIds, (task) => isActiveDownloadStatus(task.status));

    if (activeTask) {
      message.info('该项目正在下载，可在下载管理中查看进度。');
      return;
    }

    const failedTask = findMatchingTask(sourceType, nodeIds, (task) => task.status === 'error');
    if (failedTask) {
      retryDownloadTask(failedTask.id);
      return;
    }

    enqueueDownloadTask(createDownloadTask(uniqueNodes));
  }

  function downloadNode(node: StorageNode) {
    downloadNodes([node]);
  }

  function cancelDownloadTask(taskId: string) {
    const task = downloadTasksRef.current.find((candidate) => candidate.id === taskId);
    if (!task || !isCancelableDownloadStatus(task.status)) {
      return;
    }

    const controller = controllersRef.current.get(taskId);

    if (controller) {
      controller.abort();
    }

    updateDownloadTask(taskId, (current) => ({
      ...current,
      status: 'canceled',
      error: '下载已取消。',
      finishedAt: Date.now(),
    }));
  }

  function clearFinishedDownloads() {
    commitDownloadTasks((current) => current.filter((task) => !['success', 'canceled'].includes(task.status)));
  }

  function clearDownloadHistory() {
    commitDownloadTasks((current) => current.filter((task) => isActiveDownloadStatus(task.status)));
  }

  function getNodeDownloadButtonState(node: StorageNode): DriveDownloadButtonState {
    const task = findTask(
      (candidate) =>
        candidate.nodeIds.length === 1 &&
        candidate.nodeIds[0] === node.id &&
        (isActiveDownloadStatus(candidate.status) || candidate.status === 'error'),
    );

    return {
      label: formatDownloadButtonLabel(task, '下载'),
      busy: task ? isActiveDownloadStatus(task.status) : false,
      task,
    };
  }

  function getSelectionDownloadButtonState(nodes: StorageNode[]): DriveDownloadButtonState {
    if (nodes.length === 0) {
      return { label: '下载选中', busy: false, task: null };
    }

    const sourceType = resolveDownloadSourceType(nodes);
    const nodeIds = nodes.map((node) => node.id);
    const task = findMatchingTask(
      sourceType,
      nodeIds,
      (candidate) => isActiveDownloadStatus(candidate.status) || candidate.status === 'error',
    );

    return {
      label: formatDownloadButtonLabel(task, '下载选中'),
      busy: task ? isActiveDownloadStatus(task.status) : false,
      task,
    };
  }

  const activeDownloadCount = useMemo(
    () => downloadTasks.filter((task) => isActiveDownloadStatus(task.status)).length,
    [downloadTasks],
  );
  const finishedDownloadCount = useMemo(
    () => downloadTasks.filter((task) => ['success', 'error', 'canceled'].includes(task.status)).length,
    [downloadTasks],
  );

  useEffect(() => {
    authTokenRef.current = authToken;

    if (authToken) {
      return;
    }

    controllersRef.current.forEach((controller) => controller.abort());
    controllersRef.current.clear();
    commitDownloadTasks((current) =>
      current.map((task) =>
        isActiveDownloadStatus(task.status)
          ? {
              ...task,
              status: 'canceled',
              error: '登录状态已变更，下载已取消。',
              finishedAt: Date.now(),
            }
          : task,
      ),
    );
  }, [authToken]);

  return {
    downloadTasks,
    activeDownloadCount,
    finishedDownloadCount,
    downloadNode,
    downloadNodes,
    retryDownloadTask,
    cancelDownloadTask,
    clearFinishedDownloads,
    clearDownloadHistory,
    getNodeDownloadButtonState,
    getSelectionDownloadButtonState,
  };
}
